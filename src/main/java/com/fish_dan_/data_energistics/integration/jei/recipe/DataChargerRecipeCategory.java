package com.fish_dan_.data_energistics.integration.jei.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipe;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

public final class DataChargerRecipeCategory extends AbstractRecipeCategory<DataChargerRecipe> {

    public static final RecipeType<DataChargerRecipe> RECIPE_TYPE = RecipeType.create(Data_Energistics.MODID, "data_charger", DataChargerRecipe.class);
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/gui/gui_vanilla.png");
    private static final int WIDTH = 92;
    private static final int HEIGHT = 40;
    private static final int INPUT_X = 12;
    private static final int OUTPUT_X = 62;
    private static final int SLOT_Y = 2;
    private static final int ARROW_X = 35;
    private static final int ARROW_Y = 3;
    private static final int COST_Y = 25;

    private final IDrawable arrow;

    public DataChargerRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("recipe.data_energistics.data_charger"),
                guiHelper.createDrawableItemLike(DEBlocks.DATA_CHARGER.get()),
                WIDTH,
                HEIGHT);
        this.arrow = guiHelper.createDrawable(TEXTURE, 82, 128, 24, 17);
    }

    @Override
    public void draw(DataChargerRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("recipe.data_energistics.data_charger.cost", recipe.getDataFlow(), formatPower(recipe.getPower())),
                0,
                COST_Y,
                0xFF404040,
                false);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DataChargerRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, SLOT_Y).addIngredients(recipe.getIngredient());
        builder.addOutputSlot(OUTPUT_X, SLOT_Y).addItemStack(recipe.getResult());
    }

    private static String formatPower(double power) {
        if (Math.rint(power) == power) {
            return Long.toString((long) power);
        }
        return Double.toString(power);
    }
}
