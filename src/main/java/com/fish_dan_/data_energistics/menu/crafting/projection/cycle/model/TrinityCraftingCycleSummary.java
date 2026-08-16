package com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model;

import appeng.api.stacks.AEKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Immutable, network-friendly cycle statistics projected from one executable Trinity crafting plan.
 *
 * <p>
 * Cycle headers, material contributions and inventory usage percentages remain separate so a transport can flatten
 * and
 * batch each record family without depending on the server-side plan classes. The factory rebuilds the indexes used
 * by the confirmation screen and rejects partial or contradictory transports before publishing the summary.
 * </p>
 */
public final class TrinityCraftingCycleSummary {

    /** One hundred percent expressed as hundredths of a percentage point. */
    public static final int MAX_INVENTORY_USAGE_BASIS_POINTS = 10_000;

    private final Map<AEKey, Integer> inventoryUsageBasisPoints;
    private final List<TrinityCraftingCycleHeader> cycles;
    private final List<TrinityCraftingCycleMaterialContribution> contributions;
    private final Map<AEKey, List<TrinityCraftingCycleMaterialContribution>> contributionsByKey;

    private TrinityCraftingCycleSummary(Map<AEKey, Integer> inventoryUsageBasisPoints,
                                        List<TrinityCraftingCycleHeader> cycles,
                                        List<TrinityCraftingCycleMaterialContribution> contributions) {
        this.inventoryUsageBasisPoints = copyInventoryUsage(inventoryUsageBasisPoints);
        this.cycles = copyAndValidateCycles(cycles);
        this.contributions = copyAndValidateContributions(contributions, this.cycles);
        this.contributionsByKey = indexContributions(this.contributions);
    }

    /**
     * Rebuilds one complete summary from the three record families used by the network representation.
     *
     * @param inventoryUsageBasisPoints inventory usage percentages in hundredths of a percentage point
     * @param cycles                    cycle header records
     * @param contributions             material membership records
     * @return validated immutable summary with stable cycle and per-key ordering
     */
    public static TrinityCraftingCycleSummary create(Map<AEKey, Integer> inventoryUsageBasisPoints,
                                                     List<TrinityCraftingCycleHeader> cycles,
                                                     List<TrinityCraftingCycleMaterialContribution> contributions) {
        return new TrinityCraftingCycleSummary(inventoryUsageBasisPoints, cycles, contributions);
    }

    /**
     * @return inventory usage percentages keyed by every material withdrawn from ME storage
     */
    public Map<AEKey, Integer> inventoryUsageBasisPoints() {
        return this.inventoryUsageBasisPoints;
    }

    /**
     * @return cycle headers sorted by stable repeat-block index with contiguous display ordinals
     */
    public List<TrinityCraftingCycleHeader> cycles() {
        return this.cycles;
    }

    /**
     * @return material contribution records grouped in stable cycle order
     */
    public List<TrinityCraftingCycleMaterialContribution> contributions() {
        return this.contributions;
    }

    /**
     * Looks up the percentage of one material's current ME inventory consumed by this plan.
     *
     * @param key material shown by the confirmation table
     * @return hundredths of a percentage point capped at 100%, or empty when the material is not withdrawn
     */
    public OptionalInt inventoryUsage(AEKey key) {
        Integer basisPoints = this.inventoryUsageBasisPoints.get(key);
        return basisPoints == null ? OptionalInt.empty() : OptionalInt.of(basisPoints);
    }

    /**
     * Looks up every cycle containing one material. Its inventory usage percentage remains a plan-wide value and is
     * not assigned to an individual cycle.
     *
     * @param key material shown by the confirmation table
     * @return immutable contributions sorted by cycle display ordinal
     */
    public List<TrinityCraftingCycleMaterialContribution> contributionsFor(AEKey key) {
        return this.contributionsByKey.getOrDefault(key, List.of());
    }

    private static Map<AEKey, Integer> copyInventoryUsage(Map<AEKey, Integer> source) {
        LinkedHashMap<AEKey, Integer> copied = new LinkedHashMap<>();
        source.forEach((key, basisPoints) -> {
            if (basisPoints < 0 || basisPoints > MAX_INVENTORY_USAGE_BASIS_POINTS) {
                throw new IllegalArgumentException("Trinity inventory usage must remain within [0%, 100%]");
            }
            copied.put(key, basisPoints);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static List<TrinityCraftingCycleHeader> copyAndValidateCycles(
                                                                          List<TrinityCraftingCycleHeader> source) {
        ArrayList<TrinityCraftingCycleHeader> copied = new ArrayList<>(source);
        copied.sort(Comparator.comparingInt(TrinityCraftingCycleHeader::blockIndex));
        Set<Integer> blockIndexes = new HashSet<>();
        for (int index = 0; index < copied.size(); index++) {
            TrinityCraftingCycleHeader cycle = copied.get(index);
            if (!blockIndexes.add(cycle.blockIndex())) {
                throw new IllegalArgumentException("Trinity cycle summary block indexes must be unique");
            }
            if (cycle.displayOrdinal() != index + 1) {
                throw new IllegalArgumentException(
                        "Trinity cycle summary display ordinals must follow sorted block indexes");
            }
        }
        return List.copyOf(copied);
    }

    private static List<TrinityCraftingCycleMaterialContribution> copyAndValidateContributions(
                                                                                               List<TrinityCraftingCycleMaterialContribution> source,
                                                                                               List<TrinityCraftingCycleHeader> cycles) {
        Map<Integer, TrinityCraftingCycleHeader> cyclesByBlockIndex = new HashMap<>();
        cycles.forEach(cycle -> cyclesByBlockIndex.put(cycle.blockIndex(), cycle));
        ArrayList<TrinityCraftingCycleMaterialContribution> copied = new ArrayList<>(source.size());
        Set<BlockMaterial> seen = new HashSet<>();
        for (TrinityCraftingCycleMaterialContribution contribution : source) {
            TrinityCraftingCycleHeader cycle = cyclesByBlockIndex.get(contribution.blockIndex());
            if (cycle == null || cycle.displayOrdinal() != contribution.displayOrdinal()) {
                throw new IllegalArgumentException("Trinity cycle contribution must reference an existing cycle");
            }
            if (!seen.add(new BlockMaterial(contribution.blockIndex(), contribution.key()))) {
                throw new IllegalArgumentException(
                        "Trinity cycle summary cannot repeat a material within one cycle");
            }
            copied.add(contribution);
        }
        copied.sort(Comparator.comparingInt(TrinityCraftingCycleMaterialContribution::displayOrdinal));
        return List.copyOf(copied);
    }

    private static Map<AEKey, List<TrinityCraftingCycleMaterialContribution>> indexContributions(
                                                                                                 List<TrinityCraftingCycleMaterialContribution> contributions) {
        LinkedHashMap<AEKey, List<TrinityCraftingCycleMaterialContribution>> mutable = new LinkedHashMap<>();
        for (TrinityCraftingCycleMaterialContribution contribution : contributions) {
            mutable.computeIfAbsent(contribution.key(), ignored -> new ArrayList<>()).add(contribution);
        }
        LinkedHashMap<AEKey, List<TrinityCraftingCycleMaterialContribution>> copied = new LinkedHashMap<>();
        mutable.forEach((key, values) -> copied.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(copied);
    }

    private record BlockMaterial(int blockIndex, AEKey key) {}
}
