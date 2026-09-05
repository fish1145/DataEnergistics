package com.fish_dan_.data_energistics.integration.viewer.xei.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;

import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

/**
 * Canonical role assignment for one material slot in every supported recipe viewer.
 *
 * @param io       the sole INPUT or OUTPUT role published to XEI
 * @param material exact component-aware item and amount represented by the slot
 */
public record MultiblockXeiIngredient(IngredientIO io, PreviewMaterial material) {

    /**
     * Rejects roles that would accidentally expose scene, selector, or diagnostic elements as recipe data.
     */
    public MultiblockXeiIngredient {
        if (io != IngredientIO.INPUT && io != IngredientIO.OUTPUT) {
            throw new IllegalArgumentException("Multiblock XEI ingredients only support INPUT and OUTPUT roles");
        }
    }

    /**
     * Maps all selected structure and controller materials to INPUT and appends one marked order-package OUTPUT.
     */
    public static List<MultiblockXeiIngredient> from(MultiblockRecipeView view) {
        ObjectList<MultiblockXeiIngredient> ingredients = new ObjectArrayList<>(view.inputs().size() + 1);
        for (PreviewMaterial input : view.inputs()) {
            ingredients.add(new MultiblockXeiIngredient(IngredientIO.INPUT, input));
        }
        ingredients.add(new MultiblockXeiIngredient(IngredientIO.OUTPUT, view.output()));
        return ObjectLists.unmodifiable(ingredients);
    }

    /**
     * Converts the material to JEI and ItemSlot's exact int-count representation.
     */
    public ItemStack toItemStack() {
        int amount;
        try {
            amount = Math.toIntExact(this.material.amount());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "XEI material amount exceeds the ItemStack int range: " + this.material.amount(),
                    exception);
        }
        return this.material.key().toStack(amount);
    }
}
