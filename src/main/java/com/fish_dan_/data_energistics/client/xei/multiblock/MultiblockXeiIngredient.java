package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;

import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import java.util.ArrayList;
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
        if (io == null || material == null) {
            throw new IllegalArgumentException("Multiblock XEI ingredient arguments cannot be null");
        }
        if (io != IngredientIO.INPUT && io != IngredientIO.OUTPUT) {
            throw new IllegalArgumentException("Multiblock XEI ingredients only support INPUT and OUTPUT roles");
        }
    }

    /**
     * Maps all selected materials to INPUT and appends exactly one owner/controller OUTPUT.
     */
    public static List<MultiblockXeiIngredient> from(MultiblockRecipeView view) {
        if (view == null) {
            throw new IllegalArgumentException("Multiblock XEI ingredient mapping requires a recipe view");
        }
        List<MultiblockXeiIngredient> ingredients = new ArrayList<>(view.inputs().size() + 1);
        for (PreviewMaterial input : view.inputs()) {
            ingredients.add(new MultiblockXeiIngredient(IngredientIO.INPUT, input));
        }
        ingredients.add(new MultiblockXeiIngredient(IngredientIO.OUTPUT, view.output()));
        return List.copyOf(ingredients);
    }
}
