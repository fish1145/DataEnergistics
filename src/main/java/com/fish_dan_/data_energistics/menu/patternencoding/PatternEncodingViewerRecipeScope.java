package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Couples a recipe-type learning key with the viewer workstations advertised for one transfer.
 *
 * <p>
 * Only {@link #rankingContext()} is persisted as provider history. Workstations are an ephemeral matching condition
 * that is intersected with server-authoritative provider metadata or AE2 terminal groups from the current network.
 * </p>
 */
public record PatternEncodingViewerRecipeScope(
                                               PatternEncodingRankingContext rankingContext,
                                               List<ResourceLocation> workstationIds) {

    /**
     * Canonicalizes viewer workstation IDs so packet and synchronized snapshot comparisons are deterministic.
     */
    public PatternEncodingViewerRecipeScope {
        workstationIds = new LinkedHashSet<>(workstationIds).stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }
}
