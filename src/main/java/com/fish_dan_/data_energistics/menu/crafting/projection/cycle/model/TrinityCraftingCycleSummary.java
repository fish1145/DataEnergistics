package com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, network-friendly cycle statistics projected from one executable Trinity crafting plan.
 *
 * <p>
 * Cycle headers, material contributions and inventory consumption remain separate so a transport can flatten and
 * batch each record family without depending on the server-side plan classes. The factory rebuilds the indexes used
 * by the confirmation screen and rejects partial or contradictory transports before publishing the summary.
 * </p>
 */
public final class TrinityCraftingCycleSummary {

    private final Map<AEKey, BigInteger> initialExpectedInputs;
    private final List<TrinityCraftingCycleHeader> cycles;
    private final List<TrinityCraftingCycleMaterialContribution> contributions;
    private final Map<AEKey, List<TrinityCraftingCycleMaterialContribution>> contributionsByKey;

    private TrinityCraftingCycleSummary(Map<AEKey, BigInteger> initialExpectedInputs,
                                        List<TrinityCraftingCycleHeader> cycles,
                                        List<TrinityCraftingCycleMaterialContribution> contributions) {
        this.initialExpectedInputs = copyInitialExpectedInputs(initialExpectedInputs);
        this.cycles = copyAndValidateCycles(cycles);
        this.contributions = copyAndValidateContributions(contributions, this.cycles);
        this.contributionsByKey = indexContributions(this.contributions);
    }

    /**
     * Rebuilds one complete summary from the three record families used by the network representation.
     *
     * @param initialExpectedInputs exact materials withdrawn from ME storage
     * @param cycles                cycle header records
     * @param contributions         material membership records
     * @return validated immutable summary with stable cycle and per-key ordering
     */
    public static TrinityCraftingCycleSummary create(Map<AEKey, BigInteger> initialExpectedInputs,
                                                     List<TrinityCraftingCycleHeader> cycles,
                                                     List<TrinityCraftingCycleMaterialContribution> contributions) {
        return new TrinityCraftingCycleSummary(initialExpectedInputs, cycles, contributions);
    }

    /**
     * @return exact positive materials withdrawn from ME storage, including non-cycle materials
     */
    public Map<AEKey, BigInteger> initialExpectedInputs() {
        return this.initialExpectedInputs;
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
     * @return immutable lookup from material to all cycle contributions in display order
     */
    public Map<AEKey, List<TrinityCraftingCycleMaterialContribution>> contributionsByKey() {
        return this.contributionsByKey;
    }

    /**
     * Looks up the amount of one material withdrawn from ME storage.
     *
     * @param key material shown by the confirmation table
     * @return exact withdrawal, or zero when the material is not an initial input
     */
    public BigInteger inventoryConsumption(AEKey key) {
        return this.initialExpectedInputs.getOrDefault(key, BigInteger.ZERO);
    }

    /**
     * Looks up every cycle containing one material without assigning global inventory consumption to an individual
     * cycle.
     *
     * @param key material shown by the confirmation table
     * @return immutable contributions sorted by cycle display ordinal
     */
    public List<TrinityCraftingCycleMaterialContribution> contributionsFor(AEKey key) {
        return this.contributionsByKey.getOrDefault(key, List.of());
    }

    private static Map<AEKey, BigInteger> copyInitialExpectedInputs(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity cycle summary initial inputs must be positive");
            }
            copied.put(key, amount);
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
