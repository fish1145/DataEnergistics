package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Request-local observations of immutable proof layers; no cached quantity or inventory values are represented.
 */
public record TrinityPlanningCacheStatistics(
                                             int patternExpansionHits,
                                             int patternExpansionMisses,
                                             boolean targetStructureHit,
                                             int dagRouteProofHits,
                                             int dagRouteHintHits,
                                             int cycleUnitProofHits,
                                             int mipTemplateHits,
                                             boolean requestInFlightShared) {

    public static TrinityPlanningCacheStatistics empty() {
        return new TrinityPlanningCacheStatistics(0, 0, false, 0, 0, 0, 0, false);
    }
}
