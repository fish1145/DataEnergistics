package com.fish_dan_.data_energistics.integration.viewer.emi.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipe;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

public final class DataChargerEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 92;
    private static final int HEIGHT = 40;
    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            Data_Energistics.id("data_charger"),
            EmiStack.of(DEBlocks.DATA_CHARGER.get())) {

        @Override
        public Component getName() {
            return Component.translatable("recipe.data_energistics.data_charger");
        }
    };

    private final DataChargerRecipe recipe;

    public DataChargerEmiRecipe(RecipeHolder<DataChargerRecipe> holder) {
        super(CATEGORY, holder.id(), WIDTH, HEIGHT);
        this.recipe = holder.value();
        this.inputs.add(EmiIngredient.of(this.recipe.getIngredient()));
        this.outputs.add(EmiStack.of(this.recipe.getResult()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.inputs.getFirst(), 12, 2);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 35, 3);
        widgets.addSlot(this.outputs.getFirst(), 62, 2).recipeContext(this);
        widgets.addText(
                Component.translatable("recipe.data_energistics.data_charger.cost", this.recipe.getDataFlow(), formatPower(this.recipe.getPower())),
                0,
                25,
                0x404040,
                false);
    }

    private static String formatPower(double power) {
        if (Math.rint(power) == power) {
            return Long.toString((long) power);
        }
        return Double.toString(power);
    }
}
