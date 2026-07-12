package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.networking.IGridNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous Trinity runtime publication contract exposed by one AE2 crafting service.
 *
 * <p>
 * Publications change service-visible CPU membership immediately. The caller separately posts AE2's CPU-change event
 * as a cache-invalidation notification after publishing or withdrawing.
 * </p>
 */
public interface TrinityCraftingRuntimeRegistry {

    /**
     * Publishes one node-runtime pair. Repeating the same identities is idempotent; replacing a runtime requires an
     * explicit {@link #withdraw(IGridNode)} first.
     *
     * @return whether node membership changed
     */
    boolean publish(IGridNode node, TrinityDataCoreCraftingRuntime runtime);

    /**
     * Withdraws the runtime published by the exact node identity.
     *
     * @return whether node membership changed
     */
    boolean withdraw(IGridNode node);

    /** Creates isolated membership state for one crafting service instance. */
    static Local createLocal() {
        return new TrinityCraftingRuntimeRegistryImpl();
    }

    /** Internal view used by the crafting-service mixin to consume and reconcile its own publications. */
    interface Local extends TrinityCraftingRuntimeRegistry {

        /** Returns the immutable snapshot, deduplicated by runtime identity. */
        List<TrinityDataCoreCraftingRuntime> snapshot();

        /** Atomically replaces node publications from one complete AE2 machine scan. */
        List<TrinityDataCoreCraftingRuntime> reconcile(
                                                       Map<IGridNode, TrinityDataCoreCraftingRuntime> scannedRegistrations);
    }
}

/** Identity-based runtime membership owned by exactly one crafting service. */
final class TrinityCraftingRuntimeRegistryImpl implements TrinityCraftingRuntimeRegistry.Local {

    /** Exact access-node publications; node equality must never merge distinct AE2 nodes. */
    private final Map<IGridNode, TrinityDataCoreCraftingRuntime> registrations = new IdentityHashMap<>();

    /** Immutable service-visible runtime membership replaced only after a complete mutation succeeds. */
    private volatile List<TrinityDataCoreCraftingRuntime> snapshot = List.of();

    @Override
    public synchronized boolean publish(IGridNode node, TrinityDataCoreCraftingRuntime runtime) {
        if (this.registrations.containsKey(node)) {
            TrinityDataCoreCraftingRuntime current = this.registrations.get(node);
            if (current == runtime) {
                return false;
            }
            Data_Energistics.LOGGER.error(
                    "Refusing to replace Trinity crafting runtime {} with {} for grid node {} without withdrawal",
                    identity(current),
                    identity(runtime),
                    identity(node));
            throw new IllegalStateException("A different Trinity crafting runtime is already published for this node");
        }

        Map<IGridNode, TrinityDataCoreCraftingRuntime> replacements = new IdentityHashMap<>(this.registrations);
        replacements.put(node, runtime);
        commitRegistrations(replacements);
        return true;
    }

    @Override
    public synchronized boolean withdraw(IGridNode node) {
        if (!this.registrations.containsKey(node)) {
            return false;
        }
        Map<IGridNode, TrinityDataCoreCraftingRuntime> replacements = new IdentityHashMap<>(this.registrations);
        replacements.remove(node);
        commitRegistrations(replacements);
        return true;
    }

    @Override
    public List<TrinityDataCoreCraftingRuntime> snapshot() {
        return this.snapshot;
    }

    @Override
    public synchronized List<TrinityDataCoreCraftingRuntime> reconcile(
                                                                       Map<IGridNode, TrinityDataCoreCraftingRuntime> scannedRegistrations) {
        Map<IGridNode, TrinityDataCoreCraftingRuntime> replacements = new IdentityHashMap<>();
        replacements.putAll(scannedRegistrations);
        return commitRegistrations(replacements);
    }

    /** Builds the complete immutable view before changing live identity registrations. */
    private List<TrinityDataCoreCraftingRuntime> commitRegistrations(
                                                                     Map<IGridNode, TrinityDataCoreCraftingRuntime> replacements) {
        List<TrinityDataCoreCraftingRuntime> replacementSnapshot = createSnapshot(replacements.values());
        this.registrations.clear();
        this.registrations.putAll(replacements);
        this.snapshot = replacementSnapshot;
        return replacementSnapshot;
    }

    private static List<TrinityDataCoreCraftingRuntime> createSnapshot(
                                                                       Iterable<TrinityDataCoreCraftingRuntime> registrations) {
        Map<TrinityDataCoreCraftingRuntime, Boolean> seen = new IdentityHashMap<>();
        List<TrinityDataCoreCraftingRuntime> runtimes = new ArrayList<>();
        for (TrinityDataCoreCraftingRuntime runtime : registrations) {
            if (!seen.containsKey(runtime)) {
                seen.put(runtime, Boolean.TRUE);
                runtimes.add(runtime);
            }
        }
        return List.copyOf(runtimes);
    }

    private static String identity(Object value) {
        return value.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(value));
    }
}
