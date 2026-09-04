package com.fish_dan_.data_energistics.integration.viewer.emi.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.DataChargePressEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderViewerWorkstations;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Resolves exact EMI category workstations and scopes them to one synchronous transfer call.
 */
public final class EmiPatternTransferContextBridge {

    private static final ResourceLocation WORKSTATION_SOURCE_ID = Data_Energistics.id(
            "emi_recipe_type_workstations");
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private EmiPatternTransferContextBridge() {}

    /**
     * Registers the workstation lookup owned by EMI's current recipe registry.
     */
    public static void registerWorkstationSource(PatternProviderViewerWorkstations.Source source) {
        PatternProviderViewerWorkstations.register(WORKSTATION_SOURCE_ID, source);
    }

    /**
     * Resolves a canonical context directly from the recipe's EMI category ID.
     */
    public static PatternEncodingRankingContext resolve(EmiRecipe recipe) {
        return PatternEncodingViewerContext.fromRecipeType(recipe.getCategory().getId());
    }

    /** Resolves the stable recipe identity represented by the transferred EMI recipe. */
    public static @Nullable ResourceLocation resolveRecipeId(@Nullable RecipeHolder<?> holder, EmiRecipe recipe) {
        if (recipe instanceof DataChargePressEmiRecipe dataChargePressRecipe) {
            return dataChargePressRecipe.patternRecipeId();
        }
        return holder == null ? recipe.getId() : holder.id();
    }

    /**
     * Starts a transfer frame after the viewer context has been validated.
     */
    public static void begin(PatternEncodingTermMenu menu,
                             PatternEncodingRankingContext context) {
        FRAMES.get().push(new Frame(menu, context));
    }

    /**
     * Returns the context scoped to the successful transfer, rejecting an unbalanced callback.
     */
    public static PatternEncodingRankingContext requireCurrent(PatternEncodingTermMenu menu) {
        Frame frame = FRAMES.get().peek();
        if (frame == null || frame.menu() != menu) {
            throw new IllegalStateException("EMI transfer context is not scoped to the current menu");
        }
        return frame.context();
    }

    /**
     * Removes a frame only when it belongs to the menu whose transfer just returned.
     */
    public static void end(PatternEncodingTermMenu menu) {
        Deque<Frame> frames = FRAMES.get();
        Frame frame = frames.poll();
        if (frame == null) {
            FRAMES.remove();
            throw new IllegalStateException("EMI transfer context ended without an active frame");
        }
        if (frame.menu() != menu) {
            frames.clear();
            FRAMES.remove();
            throw new IllegalStateException("EMI transfer contexts were closed out of order");
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    private record Frame(PatternEncodingTermMenu menu,
                         PatternEncodingRankingContext context) {}
}
