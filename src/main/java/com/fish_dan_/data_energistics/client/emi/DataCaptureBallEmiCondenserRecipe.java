package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.core.AppEng;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.List;

final class DataCaptureBallEmiCondenserRecipe extends BasicEmiRecipe {

    static final EmiRecipeCategory CATEGORY = resolveCategory();
    private static final int REQUIRED_POWER = 131072;

    DataCaptureBallEmiCondenserRecipe() {
        super(CATEGORY, ResourceLocation.fromNamespaceAndPath("data_energistics", "condenser/data_capture_ball"), 96, 48);
        this.outputs.add(EmiStack.of(DataCaptureBallItem.createChargedStack()));
        this.catalysts.add(EmiIngredient.of(List.of(
                EmiStack.of(ModItems.DATA_STORAGE_COMPONENT_16K.get()),
                EmiStack.of(ModItems.DATA_STORAGE_COMPONENT_64K.get()),
                EmiStack.of(ModItems.DATA_STORAGE_COMPONENT_256K.get()))));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        ResourceLocation background = AppEng.makeId("textures/guis/condenser.png");
        widgets.addTexture(background, 0, 0, 96, 48, 48, 25);
        ResourceLocation statesLocation = AppEng.makeId("textures/guis/states.png");
        widgets.addTexture(statesLocation, 4, 28, 14, 14, 241, 81);
        widgets.addTexture(statesLocation, 80, 28, 16, 16, 240, 240);
        widgets.addAnimatedTexture(background, 72, 0, 6, 18, 176, 0, 2000, false, true, false);
        widgets.addDrawable(81, 28, 14, 14, (guiGraphics, mouseX, mouseY, delta) -> DataEnergisticsIcon.getBlitter("CONDENSER_OUTPUT_DATA_CAPTURE_BALL")
                .dest(0, 0, 14, 14)
                .blit(guiGraphics));
        widgets.addTooltipText(List.of(
                Component.translatable("button.data_energistics.condenser_output.data_capture_ball"),
                Component.translatable("button.data_energistics.condenser_output.data_capture_ball.detail"),
                Component.translatable("button.data_energistics.condenser_output.power", REQUIRED_POWER)), 80, 28, 16, 16);
        widgets.addSlot(EmiStack.of(DataCaptureBallItem.createChargedStack()), 56, 26).drawBack(false);
        widgets.addSlot(EmiIngredient.of(List.of(
                EmiStack.of(ModItems.DATA_STORAGE_COMPONENT_16K.get()),
                EmiStack.of(ModItems.DATA_STORAGE_COMPONENT_64K.get()),
                EmiStack.of(ModItems.DATA_STORAGE_COMPONENT_256K.get()))), 52, 0).drawBack(false);
    }

    private static EmiRecipeCategory resolveCategory() {
        try {
            Class<?> recipeClass = Class.forName("appeng.integration.modules.emi.EmiCondenserRecipe");
            var field = recipeClass.getDeclaredField("CATEGORY");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof EmiRecipeCategory category) {
                return category;
            }
        } catch (ReflectiveOperationException ignored) {}

        return new EmiRecipeCategory(
                ResourceLocation.fromNamespaceAndPath("data_energistics", "condenser_data_capture_ball"),
                EmiStack.of(DataCaptureBallItem.createChargedStack()));
    }
}
