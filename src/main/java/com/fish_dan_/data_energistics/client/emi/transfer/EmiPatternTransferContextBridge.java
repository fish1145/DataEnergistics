package com.fish_dan_.data_energistics.client.emi.transfer;

import com.fish_dan_.data_energistics.client.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.world.item.ItemStack;

import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Resolves exact EMI category workstations and scopes them to one synchronous transfer call. */
public final class EmiPatternTransferContextBridge {

    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private EmiPatternTransferContextBridge() {}

    /** Resolves a canonical context from the recipe's category workstation lookup. */
    public static @NotNull PatternEncodingRankingContext resolve(@NotNull EmiRecipe recipe) {
        var category = recipe.getCategory();
        List<ItemStack> workstations = new ArrayList<>();
        for (EmiIngredient ingredient : EmiApi.getRecipeManager().getWorkstations(category)) {
            for (EmiStack workstation : ingredient.getEmiStacks()) {
                if (workstations.size() == PatternEncodingRankingContext.MAX_WORKSTATION_IDS) {
                    throw new IllegalArgumentException(
                            "EMI workstation count exceeds " + PatternEncodingRankingContext.MAX_WORKSTATION_IDS);
                }
                workstations.add(workstation.getItemStack());
            }
        }
        return PatternEncodingViewerContext.fromItemWorkstations(category.getId(), workstations);
    }

    /** Starts a transfer frame after the viewer context has been validated. */
    public static void begin(@NotNull PatternEncodingTermMenu menu,
                             @NotNull PatternEncodingRankingContext context) {
        FRAMES.get().push(new Frame(menu, context));
    }

    /** Returns the context scoped to the successful transfer, rejecting an unbalanced callback. */
    public static @NotNull PatternEncodingRankingContext requireCurrent(@NotNull PatternEncodingTermMenu menu) {
        Frame frame = FRAMES.get().peek();
        if (frame == null || frame.menu() != menu) {
            throw new IllegalStateException("EMI transfer context is not scoped to the current menu");
        }
        return frame.context();
    }

    /** Removes a frame only when it belongs to the menu whose transfer just returned. */
    public static void end(@NotNull PatternEncodingTermMenu menu) {
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

    private record Frame(@NotNull PatternEncodingTermMenu menu,
                         @NotNull PatternEncodingRankingContext context) {}
}
