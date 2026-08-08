package com.fish_dan_.data_energistics.client.transfer;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds a viewer context from the exact item workstations supplied by a recipe viewer.
 */
public final class PatternEncodingViewerContext {

    private PatternEncodingViewerContext() {}

    /**
     * Resolves the mode that AE2's viewer transfer will request before the asynchronously synchronized menu field
     * reflects that request.
     */
    public static @NotNull EncodingMode resolveEncodingMode(@Nullable Recipe<?> recipe, boolean craftingCategory) {
        if (recipe == null || (!craftingCategory && !EncodingHelper.isSupportedCraftingRecipe(recipe))) {
            return EncodingMode.PROCESSING;
        }
        if (recipe.getType() == RecipeType.STONECUTTING) {
            return EncodingMode.STONECUTTING;
        }
        if (recipe.getType() == RecipeType.SMITHING) {
            return EncodingMode.SMITHING_TABLE;
        }
        return EncodingMode.CRAFTING;
    }

    /**
     * Converts viewer workstation stacks to a canonical registry-ID snapshot.
     *
     * <p>
     * An empty collection is valid for categories that have no registered workstation. Any non-item, empty, air,
     * or unregistered stack invalidates the complete context instead of being silently discarded.
     * </p>
     */
    public static @NotNull PatternEncodingRankingContext fromItemWorkstations(
                                                                              @NotNull ResourceLocation categoryId,
                                                                              @NotNull Collection<@NotNull ItemStack> workstations) {
        if (workstations.size() > PatternEncodingRankingContext.MAX_WORKSTATION_IDS) {
            throw new IllegalArgumentException(
                    "Recipe viewer workstation count exceeds " + PatternEncodingRankingContext.MAX_WORKSTATION_IDS);
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
