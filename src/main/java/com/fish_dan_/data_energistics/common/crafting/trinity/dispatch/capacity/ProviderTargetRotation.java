package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure hierarchical rotation that interleaves one target from each provider before visiting later targets.
 */
final class ProviderTargetRotation {

    private final List<Target> targets;

    private ProviderTargetRotation(List<Target> targets) {
        this.targets = List.copyOf(targets);
    }

    /**
     * Builds a complete provider-first, target-second order without dropping duplicate snapshot values.
     */
    static ProviderTargetRotation create(List<ProviderCapacitySnapshot> snapshots, CraftingDispatchCursor cursor) {
        List<ProviderCapacitySnapshot> stableSnapshots = List.copyOf(snapshots);
        if (cursor == null) {
            throw new IllegalArgumentException("Provider target rotation requires a fairness cursor");
        }
        LinkedHashMap<CraftingProviderId, ArrayList<ProviderCapacitySnapshot>> grouped = new LinkedHashMap<>();
        for (ProviderCapacitySnapshot snapshot : stableSnapshots) {
            grouped.computeIfAbsent(snapshot.providerId(), ignored -> new ArrayList<>()).add(snapshot);
        }
        if (grouped.isEmpty()) {
            return new ProviderTargetRotation(List.of());
        }

        List<Map.Entry<CraftingProviderId, ArrayList<ProviderCapacitySnapshot>>> providers =
                List.copyOf(grouped.entrySet());
        int providerCount = providers.size();
        int providerStart = Math.floorMod(cursor.provider(), providerCount);
        int maximumTargets = providers.stream().mapToInt(entry -> entry.getValue().size()).max().orElseThrow();
        ArrayList<ArrayList<Target>> rounds = new ArrayList<>(maximumTargets);
        for (int targetRound = 0; targetRound < maximumTargets; targetRound++) {
            rounds.add(new ArrayList<>());
        }
        ArrayList<Target> rotated = new ArrayList<>(stableSnapshots.size());
        for (int providerOffset = 0; providerOffset < providerCount; providerOffset++) {
            int providerIndex = Math.floorMod(providerStart + providerOffset, providerCount);
            List<ProviderCapacitySnapshot> providerTargets = providers.get(providerIndex).getValue();
            long firstTargetRound = providerIndex < providerStart ?
                    Math.incrementExact(cursor.target()) :
                    cursor.target();
            for (int targetOffset = 0; targetOffset < providerTargets.size(); targetOffset++) {
                long targetRound = Math.addExact(firstTargetRound, targetOffset);
                int targetIndex = Math.floorMod(targetRound, providerTargets.size());
                int nextProvider = Math.floorMod(providerIndex + 1, providerCount);
                long nextTargetRound = providerIndex == providerCount - 1 ?
                        Math.incrementExact(targetRound) :
                        targetRound;
                CraftingDispatchCursor successor = new CraftingDispatchCursor(
                        nextProvider,
                        nextTargetRound);
                rounds.get(targetOffset).add(new Target(providerTargets.get(targetIndex), successor));
            }
        }
        rounds.forEach(rotated::addAll);
        return new ProviderTargetRotation(rotated);
    }

    /** @return immutable complete rotated targets */
    List<Target> targets() {
        return this.targets;
    }

    /**
     * @param snapshot immutable capacity observation retained from the capture
     * @param successor cursor suggested only after this exact target receives a real provider call
     */
    record Target(ProviderCapacitySnapshot snapshot, CraftingDispatchCursor successor) {

        Target {
            if (snapshot == null || successor == null) {
                throw new IllegalArgumentException("Rotated provider target must be complete");
            }
        }
    }
}
