package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiComposition;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import lombok.Getter;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JEI adapter for the shared live Trinity multiblock composition.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class TrinityMultiblockJeiCategory extends ModularUIRecipeCategory<MultiblockXeiRecipe> {

    /**
     * Sole controller-level JEI recipe type for every Trinity substructure and selection.
     */
    public static final RecipeType<MultiblockXeiRecipe> RECIPE_TYPE = new RecipeType<>(
            MultiblockXeiRecipe.CATEGORY_ID,
            MultiblockXeiRecipe.class);

    @Getter
    private final IDrawable icon;

    /**
     * Creates the category with LDLib2's ModularUI recipe provider and the Trinity controller icon.
     */
    public TrinityMultiblockJeiCategory(IJeiHelpers helpers) {
        super(recipe -> recipe.createModularUI("trinity_multiblock_jei"));
        if (helpers == null) {
            throw new IllegalArgumentException("Trinity multiblock JEI category requires JEI helpers");
        }
        this.icon = helpers.getGuiHelper().createDrawableItemLike(ModBlocks.TRINITY_DATA_CORE.get());
    }

    @Override
    public RecipeType<MultiblockXeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.TRINITY_DATA_CORE.get().getName();
    }

    @Override
    public int getWidth() {
        return MultiblockXeiComposition.WIDTH;
    }

    @Override
    public int getHeight() {
        return MultiblockXeiComposition.HEIGHT;
    }
}
