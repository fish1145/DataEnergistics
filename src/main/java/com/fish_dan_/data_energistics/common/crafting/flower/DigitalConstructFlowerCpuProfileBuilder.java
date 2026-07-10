package com.fish_dan_.data_energistics.common.crafting.flower;

import java.util.Map;
import java.util.TreeMap;

/**
 * Mutable collector for named Digital Construct Flower CPU contributions.
 *
 * <p>
 * The host keeps one builder-like contribution map so child structure lifecycle hooks can replace their own data
 * without invalidating unrelated sections.
 */
public final class DigitalConstructFlowerCpuProfileBuilder {

    private final Map<String, DigitalConstructFlowerCpuContribution> contributions = new TreeMap<>();

    /**
     * Adds or replaces the contribution for one structure name.
     *
     * @param structureName structure name that owns the contribution
     * @param contribution  contribution data
     */
    public void put(String structureName, DigitalConstructFlowerCpuContribution contribution) {
        this.contributions.put(requireStructureName(structureName), contribution);
    }

    /**
     * Removes the contribution for one structure name.
     *
     * @param structureName structure name that no longer contributes CPU data
     */
    public void remove(String structureName) {
        this.contributions.remove(requireStructureName(structureName));
    }

    /**
     * Removes every contribution.
     */
    public void clear() {
        this.contributions.clear();
    }

    /**
     * @return immutable aggregate profile for the current contribution set
     */
    public DigitalConstructFlowerCpuProfile build() {
        return DigitalConstructFlowerCpuProfile.fromContributions(this.contributions);
    }

    /**
     * @return copy of current named contributions for persistence
     */
    public Map<String, DigitalConstructFlowerCpuContribution> contributions() {
        return Map.copyOf(this.contributions);
    }

    private static String requireStructureName(String structureName) {
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("CPU contribution structure name must not be blank");
        }
        return structureName;
    }
}
