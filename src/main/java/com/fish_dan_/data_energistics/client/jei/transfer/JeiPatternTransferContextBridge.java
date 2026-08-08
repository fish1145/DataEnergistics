package com.fish_dan_.data_energistics.client.jei.transfer;

import com.fish_dan_.data_energistics.client.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the stable JEI recipe type used by the server-owned provider lookup.
 */
public final class JeiPatternTransferContextBridge {

    private JeiPatternTransferContextBridge() {
    }

    /**
     * Resolves the recipe type directly from the transferred JEI layout.
     */
    public static @NotNull PatternEncodingRankingContext resolve(@NotNull IRecipeLayoutDrawable<?> recipeLayout) {
        var recipeType = recipeLayout.getRecipeCategory().getRecipeType();
        return PatternEncodingViewerContext.fromRecipeType(recipeType.getUid());
    }
}
