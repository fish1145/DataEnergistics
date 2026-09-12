package com.fish_dan_.data_energistics.client.crafting.tree.viewer;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import appeng.api.stacks.GenericStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Client-thread-only optional viewer boundary. Implementations are installed by their native plugin lifecycle;
 * the graph UI never resolves EMI/JEI classes when those mods are absent. No recipe selection changes the plan.
 */
public interface CraftingPlanIngredientViewer {

    /** Stable preference rank; EMI is preferred over JEI when both runtimes are ready. */
    int priority();

    /** Whether native navigation is currently available, including after viewer reloads. */
    boolean available();

    /** Binds native hover lookup to a newly created canvas. The supplier returns null outside a material icon. */
    void bind(UIElement canvas, Supplier<@Nullable GenericStack> hovered);

    /** Opens recipes/uses without changing the plan; returns false if this native runtime is unavailable. */
    boolean show(GenericStack stack, boolean recipes);

    /**
     * Adds a non-null material to native favorites on the client thread without opening another screen.
     * Existing favorites remain present. Returns false when the runtime is unavailable; invalid ingredients
     * follow the native viewer's validation. Does not change the plan.
     */
    boolean favorite(GenericStack stack);
}
