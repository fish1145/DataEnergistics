package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeViewSource;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import dev.emi.emi.api.recipe.EmiRecipe;

/**
 * Exact ownership predicate that keeps AE2's catch-all handler away from typed multiblock recipes.
 */
public final class EmiEncodePatternHandlerMultiblockTransferGuard {

    private EmiEncodePatternHandlerMultiblockTransferGuard() {}

    /**
     * Returns true only for AE2's encoding handler family and DataE's typed live multiblock recipe wrappers.
     */
    public static boolean shouldDefer(Object handler, EmiRecipe recipe) {
        return handler instanceof EmiEncodePatternHandler<?> &&
                recipe instanceof MultiblockRecipeViewSource;
    }
}
