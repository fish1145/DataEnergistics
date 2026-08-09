package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.DataReassemblerLayout;
import com.fish_dan_.data_energistics.client.recipe.DataRipperReassemblerRecipeUiProvider;
import com.fish_dan_.data_energistics.client.recipe.DataRipperReassemblerRecipeView;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

/**
 * EMI wrapper around the shared LDLib2 data reassembler recipe UI.
 */
public final class DataRipperReassemblerEmiRecipe extends ModularUIEMIRecipe {

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            Data_Energistics.id("data_reassembler"),
            EmiStack.of(ModBlocks.DATA_RIPPER_REASSEMBLER.get())) {

        @Override
        public Component getName() {
            return Component.translatable("recipe.data_energistics.data_reassembler");
        }
    };

    private static final DataRipperReassemblerRecipeUiProvider UI_PROVIDER = new DataRipperReassemblerRecipeUiProvider(new EmiDataReassemblerIngredientAdapter());

    private final DataRipperReassemblerRecipeView recipe;

    public DataRipperReassemblerEmiRecipe(RecipeHolder<DataRipperReassemblerRecipe> holder) {
        this(DataRipperReassemblerRecipeView.from(holder));
    }

    private DataRipperReassemblerEmiRecipe(DataRipperReassemblerRecipeView recipe) {
        super(ignored -> UI_PROVIDER.createModularUI(recipe));
        this.recipe = recipe;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return this.recipe.id();
    }

    @Override
    public int getDisplayWidth() {
        return DataReassemblerLayout.RECIPE_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return DataReassemblerLayout.RECIPE_HEIGHT;
    }
}
