package com.fish_dan_.data_energistics.client.jei.transfer;

import com.fish_dan_.data_energistics.client.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Holds the synchronous JEI runtime used to resolve exact transfer context. */
public final class JeiPatternTransferContextBridge {

    @Nullable
    private static volatile IJeiRuntime runtime;

    private JeiPatternTransferContextBridge() {}

    /** Publishes the currently available JEI runtime for transfer lookup. */
    public static void install(@NotNull IJeiRuntime value) {
        runtime = value;
    }

    /** Clears only the JEI runtime instance that is ending. */
    public static void clear(@NotNull IJeiRuntime value) {
        if (runtime == value) {
            runtime = null;
        }
    }

    /** Resolves category and category workstations through the active JEI runtime. */
    public static @NotNull PatternEncodingRankingContext resolve(@NotNull IRecipeLayoutDrawable<?> recipeLayout) {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            throw new IllegalStateException("JEI runtime is unavailable during recipe transfer");
        }
        var recipeType = recipeLayout.getRecipeCategory().getRecipeType();
        List<ItemStack> workstations = currentRuntime.getRecipeManager()
                .createRecipeCatalystLookup(recipeType)
                .getItemStack()
                .limit(PatternEncodingRankingContext.MAX_WORKSTATION_IDS + 1L)
                .toList();
        return PatternEncodingViewerContext.fromItemWorkstations(recipeType.getUid(), workstations);
    }
}
