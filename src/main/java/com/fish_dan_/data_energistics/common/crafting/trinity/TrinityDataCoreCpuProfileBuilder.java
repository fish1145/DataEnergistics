package com.fish_dan_.data_energistics.common.crafting.trinity;

import java.util.Map;
import java.util.TreeMap;

/**
 * Mutable collector for named Trinity Data Core CPU contributions.
 *
 * <p>
 * The host keeps one builder-like contribution map so child structure lifecycle hooks can replace their own data
 * without invalidating unrelated sections.
 */
public final class TrinityDataCoreCpuProfileBuilder {

    private final Map<String, TrinityDataCoreCpuContribution> contributions = new TreeMap<>();

    /**
     * Adds or replaces the contribution for one structure name.
     *
     * @param structureName structure name that owns the contribution
     * @param contribution  contribution data
     */
    public void put(String structureName, TrinityDataCoreCpuContribution contribution) {
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
    public TrinityDataCoreCpuProfile build() {
        return TrinityDataCoreCpuProfile.fromContributions(this.contributions);
    }

    /**
     * @return copy of current named contributions for persistence
     */
    public Map<String, TrinityDataCoreCpuContribution> contributions() {
        return Map.copyOf(this.contributions);
    }

    private static String requireStructureName(String structureName) {
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("CPU contribution structure name must not be blank");
        }
        return structureName;
    }
}
