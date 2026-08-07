package com.fish_dan_.data_energistics.client.jei.transfer;

import com.fish_dan_.data_energistics.client.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.world.item.ItemStack;

import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Holds the synchronous JEI runtime and one nested transfer frame per client thread. */
public final class JeiPatternTransferContextBridge {

    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile IJeiRuntime runtime;

    private JeiPatternTransferContextBridge() {}

    /** Publishes the currently available JEI runtime for transfer lookup. */
    public static void install(IJeiRuntime value) {
        runtime = value;
    }

    /** Clears only the runtime that is ending and discards any unconsumed transfer frames. */
    public static void clear(IJeiRuntime value) {
        if (runtime == value) {
            runtime = null;
        }
        FRAMES.remove();
    }

    /** Resolves category and category workstations through the active JEI runtime. */
    public static PatternEncodingRankingContext resolve(IRecipeLayoutDrawable<?> recipeLayout) {
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

    /** Starts one transfer frame after its context has been fully validated. */
    public static void begin(PatternEncodingTermMenu menu, PatternEncodingRankingContext context) {
        FRAMES.get().push(new Frame(menu, context));
    }

    /** Returns the current frame's context for the AE2 transfer handler. */
    @Nullable
    public static PatternEncodingRankingContext current(PatternEncodingTermMenu menu) {
        Frame frame = FRAMES.get().peek();
        return frame != null && frame.menu() == menu ? frame.context() : null;
    }

    /** Removes the current frame and releases the thread-local container when the stack becomes empty. */
    public static void end(PatternEncodingTermMenu menu) {
        Deque<Frame> frames = FRAMES.get();
        if (!frames.isEmpty() && frames.peek().menu() == menu) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    private record Frame(PatternEncodingTermMenu menu, PatternEncodingRankingContext context) {}
}
