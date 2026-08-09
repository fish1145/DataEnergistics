package com.fish_dan_.data_energistics.common.multiblock.preview.catalog;

import net.minecraft.resources.ResourceLocation;

/**
 * Platform-neutral live source used by recipe viewers and future transfer adapters.
 *
 * <p>
 * The registered identity remains controller-stable while the current view follows the active preview
 * selection and definition revision.
 * </p>
 */
public interface MultiblockRecipeViewSource {

    /**
     * Returns the stable controller-level recipe id used for XEI registration.
     */
    ResourceLocation registeredRecipeId();

    /**
     * Returns the current typed recipe view, rejecting disposed or stale preview state.
     */
    MultiblockRecipeView currentRecipeView();
}
