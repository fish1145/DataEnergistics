package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sort-based water-filling implementation of startup-first max-min target allocation.
 */
final class StartupFirstMaxMinCapacitySlicePlanner implements CapacitySlicePlanner {

    /**
     * Plans at most one physical call per selected snapshot.
     *
     * <p>
     * Known zero capacity is skipped. Unknown capacity, unknown single-batch capacity, and routing modes other than
     * {@code TARGETED} are limited to one logical craft so snapshot uncertainty never invents counted semantics.
     * </p>
     *
     * @param snapshots         immutable provider target observations in stable provider order
     * @param remainingCrafts   non-negative logical work still requested
     * @param physicalCallLimit non-negative maximum number of returned slices
     * @param cursor            non-negative round-robin cursor into the original snapshot order
     * @return immutable slices and the next cursor
     */
    @Override
    public CapacitySlicePlan plan(
                                  List<ProviderCapacitySnapshot> snapshots,
                                  BigInteger remainingCrafts,
                                  int physicalCallLimit,
                                  int cursor) {
        List<ProviderCapacitySnapshot> stableSnapshots = List.copyOf(snapshots);
        validateRequest(remainingCrafts, physicalCallLimit, cursor);
        int targetCount = stableSnapshots.size();
        if (targetCount == 0) {
            return new CapacitySlicePlan(List.of(), 0);
        }

        int start = Math.floorMod(cursor, targetCount);
        if (remainingCrafts.signum() == 0 || physicalCallLimit == 0) {
            return new CapacitySlicePlan(List.of(), start);
        }

        ArrayList<TargetCapacity> eligible = new ArrayList<>(targetCount);
        for (int offset = 0; offset < targetCount; offset++) {
            int snapshotIndex = Math.floorMod((long) start + offset, targetCount);
            ProviderCapacitySnapshot snapshot = stableSnapshots.get(snapshotIndex);
            long capacity = effectiveCapacity(snapshot);
            if (capacity > 0L) {
                eligible.add(new TargetCapacity(snapshotIndex, snapshot, capacity));
            }
        }
        if (eligible.isEmpty()) {
            return new CapacitySlicePlan(List.of(), start);
        }

        int selectionLimit = Math.min(physicalCallLimit, eligible.size());
        if (remainingCrafts.compareTo(BigInteger.valueOf(selectionLimit)) < 0) {
            selectionLimit = remainingCrafts.intValueExact();
        }
        List<TargetCapacity> selected = List.copyOf(eligible.subList(0, selectionLimit));
        long[] allocations = fairAllocations(
                selected,
                remainingCrafts.subtract(BigInteger.valueOf(selectionLimit)));

        ArrayList<CapacitySlice> slices = new ArrayList<>(selectionLimit);
        for (int index = 0; index < selectionLimit; index++) {
            slices.add(new CapacitySlice(selected.get(index).snapshot(), allocations[index]));
        }
        int nextCursor = Math.floorMod(selected.getLast().snapshotIndex() + 1, targetCount);
        return new CapacitySlicePlan(slices, nextCursor);
    }

    /**
     * Validates caller-controlled scalar boundaries before target traversal.
     */
    private static void validateRequest(BigInteger remainingCrafts, int physicalCallLimit, int cursor) {
        if (remainingCrafts == null || remainingCrafts.signum() < 0) {
            throw new IllegalArgumentException("Remaining logical craft count must not be negative");
        }
        if (physicalCallLimit < 0) {
            throw new IllegalArgumentException("Physical call limit must not be negative");
        }
        if (cursor < 0) {
            throw new IllegalArgumentException("Capacity slice cursor must not be negative");
        }
    }

    /**
     * Derives the strongest safe one-call capacity without turning uncertainty into counted support.
     */
    private static long effectiveCapacity(ProviderCapacitySnapshot snapshot) {
        DispatchCapacity capacity = snapshot.capacity();
        DispatchCapacity maximumSingleBatch = snapshot.maximumSingleBatch();
        if (isKnownZero(capacity) || isKnownZero(maximumSingleBatch)) {
            return 0L;
        }
        if (snapshot.routingMode() != ProviderRoutingMode.TARGETED ||
                !(capacity instanceof DispatchCapacity.Known(long knownCapacity)) ||
                !(maximumSingleBatch instanceof DispatchCapacity.Known(long knownMaximum))) {
            return 1L;
        }
        return Math.min(knownCapacity, knownMaximum);
    }

    /**
     * Recognizes only an explicit zero fact; unknown remains a conservative single-call candidate.
     */
    private static boolean isKnownZero(DispatchCapacity capacity) {
        return capacity instanceof DispatchCapacity.Known(long logicalCrafts) && logicalCrafts == 0L;
    }

    /**
     * Gives every selected target one startup craft, then computes one max-min water level in O(n log n).
     */
    private static long[] fairAllocations(List<TargetCapacity> selected, BigInteger remainingCrafts) {
        int selectedCount = selected.size();
        ArrayList<Long> residualCapacities = new ArrayList<>(selectedCount);
        for (TargetCapacity target : selected) {
            residualCapacities.add(Math.subtractExact(target.capacity(), 1L));
        }
        residualCapacities.sort(Comparator.naturalOrder());

        long waterLevel = 0L;
        int activeTargets = selectedCount;
        int sortedIndex = 0;
        int remainder = 0;
        while (sortedIndex < selectedCount && remainingCrafts.signum() > 0) {
            long nextLevel = residualCapacities.get(sortedIndex);
            long levelIncrease = Math.subtractExact(nextLevel, waterLevel);
            BigInteger required = BigInteger.valueOf(levelIncrease)
                    .multiply(BigInteger.valueOf(activeTargets));
            if (remainingCrafts.compareTo(required) < 0) {
                BigInteger[] division = remainingCrafts.divideAndRemainder(BigInteger.valueOf(activeTargets));
                waterLevel = Math.addExact(waterLevel, division[0].longValueExact());
                remainder = division[1].intValueExact();
                break;
            }

            waterLevel = nextLevel;
            remainingCrafts = remainingCrafts.subtract(required);
            while (sortedIndex < selectedCount && residualCapacities.get(sortedIndex) == waterLevel) {
                sortedIndex++;
                activeTargets--;
            }
        }

        long[] allocations = new long[selectedCount];
        for (int index = 0; index < selectedCount; index++) {
            long residual = Math.subtractExact(selected.get(index).capacity(), 1L);
            allocations[index] = Math.addExact(1L, Math.min(residual, waterLevel));
            if (remainder > 0 && residual > waterLevel) {
                allocations[index] = Math.incrementExact(allocations[index]);
                remainder--;
            }
        }
        return allocations;
    }

    /**
     * Retains original-list identity while sorting only scalar residual capacities.
     */
    private record TargetCapacity(
                                  int snapshotIndex,
                                  ProviderCapacitySnapshot snapshot,
                                  long capacity) {}
}
