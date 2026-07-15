package com.fish_dan_.data_energistics.client.recipe;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import java.util.List;

/**
 * Viewer-neutral bridge that gives shared LDLib2 elements native recipe-viewer identities.
 */
public interface DataReassemblerRecipeIngredientAdapter {

    /**
     * Registers one logical item slot, including every alternative that may be displayed.
     */
    void registerItemSlot(ItemSlot element, IngredientIO role, List<ItemStack> candidates);

    /**
     * Registers a fluid or custom AE key slot without converting it to a fake item in shared code.
     */
    void registerGenericStackSlot(UIElement element, IngredientIO role, GenericStack stack);
}
