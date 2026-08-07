package com.fish_dan_.data_energistics.client.transfer;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Builds a viewer context from the exact item workstations supplied by a recipe viewer. */
public final class PatternEncodingViewerContext {

    private PatternEncodingViewerContext() {}

    /**
     * Converts viewer workstation stacks to a canonical registry-ID snapshot.
     *
     * <p>An empty collection is valid for categories that have no registered workstation. Any non-item, empty, air,
     * or unregistered stack invalidates the complete context instead of being silently discarded.</p>
     */
    public static PatternEncodingRankingContext fromItemWorkstations(ResourceLocation categoryId,
                                                                      Collection<ItemStack> workstations) {
        if (workstations.size() > PatternEncodingRankingContext.MAX_WORKSTATION_IDS) {
            throw new IllegalArgumentException(
                    "Recipe viewer workstation count exceeds "
                            + PatternEncodingRankingContext.MAX_WORKSTATION_IDS);
        }
        List<ResourceLocation> workstationIds = new ArrayList<>(workstations.size());
        for (ItemStack workstation : workstations) {
            if (workstation.isEmpty()) {
                throw new IllegalArgumentException("Recipe viewer returned an empty or air workstation");
            }
            ResourceLocation workstationId = BuiltInRegistries.ITEM.getKeyOrNull(workstation.getItem());
            if (workstationId == null) {
                throw new IllegalArgumentException("Recipe viewer returned an unregistered workstation item");
            }
            workstationIds.add(workstationId);
        }
        return PatternEncodingRankingContext.of(categoryId, workstationIds);
    }
}
