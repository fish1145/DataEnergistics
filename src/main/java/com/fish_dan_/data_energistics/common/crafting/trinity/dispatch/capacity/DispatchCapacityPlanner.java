package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityCachedComputation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationNamespace;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Grid-scoped cached boundary for pure provider-first capacity slice calculation.
 * <p>
 * Cache-backed adapter over the existing max-min slice algorithm.
 */
public final class DispatchCapacityPlanner {

    /**
     * Creates the production planner over the shared server-lifetime computation cache.
     *
     * @param cache cache supplier resolved for each calculation
     * @return independent stateless planner facade
     */
    public static DispatchCapacityPlanner create(Supplier<TrinityComputationCache> cache) {
        return new DispatchCapacityPlanner(cache, CapacitySlicePlanner.create());
    }

    private final Supplier<TrinityComputationCache> cache;
    private final CapacitySlicePlanner delegate;

    DispatchCapacityPlanner(Supplier<TrinityComputationCache> cache, CapacitySlicePlanner delegate) {
        if (cache == null || delegate == null) {
            throw new IllegalArgumentException("Dispatch capacity planning requires a cache and delegate");
        }
        this.cache = cache;
        this.delegate = delegate;
    }

    /**
     * Computes or reuses one immutable slice plan.
     *
     * @param capture           complete capacity capture identity and values
     * @param remainingCrafts   non-negative remaining logical work
     * @param physicalCallLimit non-negative maximum returned physical calls
     * @param cursor            persistent provider and target fairness position
     * @return immutable capacity allocations
     */
    public DispatchCapacitySlicePlan plan(ProviderCapacityCapture capture,
                                          BigInteger remainingCrafts,
                                          int physicalCallLimit,
                                          CraftingDispatchCursor cursor) {
        SliceKey key = new SliceKey(
                capture.key(),
                capture.snapshots(),
                remainingCrafts,
                physicalCallLimit,
                cursor);
        try {
            return this.cache.get().computeInline(
                    key.captureKey().gridScope(),
                    TrinityComputationNamespace.CAPACITY_SLICE,
                    key.captureKey().capacityEpoch(),
                    key,
                    () -> TrinityCachedComputation.cacheable(calculate(key))).value();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dispatch capacity cache wait was interrupted", exception);
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    private DispatchCapacitySlicePlan calculate(SliceKey key) {
        ProviderTargetRotation rotation = ProviderTargetRotation.create(key.snapshots(), key.cursor());
        List<ProviderCapacitySnapshot> orderedSnapshots = rotation.targets().stream()
                .map(ProviderTargetRotation.Target::snapshot)
                .toList();
        CapacitySlicePlan rawPlan = this.delegate.plan(
                orderedSnapshots,
                key.remainingCrafts(),
                key.physicalCallLimit(),
                0);
        Map<ProviderCapacitySnapshot, ArrayDeque<ProviderTargetRotation.Target>> targetsByIdentity = new IdentityHashMap<>();
        for (ProviderTargetRotation.Target target : rotation.targets()) {
            targetsByIdentity.computeIfAbsent(target.snapshot(), ignored -> new ArrayDeque<>()).addLast(target);
        }
        ArrayList<DispatchCapacitySlicePlan.Slice> slices = new ArrayList<>(rawPlan.slices().size());
        for (CapacitySlice rawSlice : rawPlan.slices()) {
            ArrayDeque<ProviderTargetRotation.Target> matching = targetsByIdentity.get(rawSlice.target());
            if (matching == null || matching.isEmpty()) {
                throw new IllegalStateException("Capacity slice planner selected a target outside its rotated input");
            }
            ProviderTargetRotation.Target selected = matching.removeFirst();
            slices.add(new DispatchCapacitySlicePlan.Slice(
                    selected.snapshot(),
                    rawSlice.logicalCrafts(),
                    selected.successor()));
        }
        return new DispatchCapacitySlicePlan(slices);
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Dispatch capacity cache calculation failed", failure);
    }

    /**
     * Complete immutable capacity-slice key retained inside the shared Grid LRU.
     */
    private record SliceKey(
                            ProviderCapacityCaptureKey captureKey,
                            List<ProviderCapacitySnapshot> snapshots,
                            BigInteger remainingCrafts,
                            int physicalCallLimit,
                            CraftingDispatchCursor cursor) {

        private SliceKey {
            if (captureKey == null || remainingCrafts == null || cursor == null) {
                throw new IllegalArgumentException("Dispatch capacity slice cache key must be complete");
            }
            snapshots = List.copyOf(snapshots);
            if (remainingCrafts.signum() < 0 || physicalCallLimit < 0) {
                throw new IllegalArgumentException("Dispatch capacity slice cache bounds must not be negative");
            }
        }
    }
}
