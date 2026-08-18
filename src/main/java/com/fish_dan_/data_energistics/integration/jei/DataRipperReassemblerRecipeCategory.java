package com.fish_dan_.data_energistics.integration.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.xei.recipe.DataReassemblerLayout;
import com.fish_dan_.data_energistics.integration.xei.recipe.DataRipperReassemblerRecipeUiProvider;
import com.fish_dan_.data_energistics.integration.xei.recipe.DataRipperReassemblerRecipeView;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;

/**
 * JEI category backed by the same LDLib2 UI and validated view used by EMI.
 */
public final class DataRipperReassemblerRecipeCategory
                                                       extends ModularUIRecipeCategory<DataRipperReassemblerRecipeView> {

    public static final RecipeType<DataRipperReassemblerRecipeView> RECIPE_TYPE = RecipeType.create(
            Data_Energistics.MODID,
            "data_reassembler",
            DataRipperReassemblerRecipeView.class);

    private static final DataRipperReassemblerRecipeUiProvider UI_PROVIDER = new DataRipperReassemblerRecipeUiProvider(new JeiDataReassemblerIngredientAdapter());

    private final IDrawable icon;

    public DataRipperReassemblerRecipeCategory(IGuiHelper guiHelper) {
        super(UI_PROVIDER);
        this.icon = guiHelper.createDrawableItemLike(DEBlocks.DATA_RIPPER_REASSEMBLER.get());
    }

    @Override
    public RecipeType<DataRipperReassemblerRecipeView> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("recipe.data_energistics.data_reassembler");
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return DataReassemblerLayout.RECIPE_WIDTH;
    }

    @Override
    public int getHeight() {
        return DataReassemblerLayout.RECIPE_HEIGHT;
    }
}
