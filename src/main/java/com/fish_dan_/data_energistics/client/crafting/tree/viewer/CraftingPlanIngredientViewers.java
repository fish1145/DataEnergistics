package com.fish_dan_.data_energistics.client.crafting.tree.viewer;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Native plugins replace their own binding on reload; no optional implementation is constructed by the core UI. */
public final class CraftingPlanIngredientViewers {

    private static final Map<String, CraftingPlanIngredientViewer> VIEWERS = new Object2ObjectLinkedOpenHashMap<>();

    private CraftingPlanIngredientViewers() {}

    public static synchronized void register(String id, CraftingPlanIngredientViewer viewer) {
        VIEWERS.put(id, viewer);
    }

    private static synchronized List<CraftingPlanIngredientViewer> viewers() {
        return VIEWERS.values().stream().sorted(Comparator.comparingInt(CraftingPlanIngredientViewer::priority).reversed()).toList();
    }

    public static void bind(UIElement canvas, Supplier<@Nullable GenericStack> hovered) {
        viewers().forEach(viewer -> viewer.bind(canvas, hovered));
    }

    public static boolean show(GenericStack stack, boolean recipes) {
        for (var viewer : viewers()) if (viewer.show(stack, recipes)) return true;
        return false;
    }
}
