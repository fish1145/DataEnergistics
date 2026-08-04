package com.fish_dan_.data_energistics.client.jei.ingredient;

import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.client.AEKeyRendering;
import mezz.jei.api.ingredients.IIngredientRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders native Data Energistics JEI ingredients with their AE key icon and amount overlay.
 */
public final class DataResourceJeiIngredientRenderer implements IIngredientRenderer<DataResourceJeiIngredient> {

    /**
     * Shared stateless renderer instance registered with JEI.
     */
    public static final DataResourceJeiIngredientRenderer INSTANCE = new DataResourceJeiIngredientRenderer();

    private DataResourceJeiIngredientRenderer() {}

    @Override
    public void render(GuiGraphics guiGraphics, DataResourceJeiIngredient ingredient) {
        CustomKeyGuiRenderer.draw(
                Minecraft.getInstance(),
                guiGraphics,
                0,
                0,
                ingredient.key().aeKey());
        if (ingredient.amount() != 1L) {
            GenericStackDisplayHelper.renderSmallOverlay(
                    guiGraphics,
                    0,
                    0,
                    GenericStackDisplayHelper.formatCompactAmount(ingredient.amount()));
        }
    }

    @Override
    public List<Component> getTooltip(DataResourceJeiIngredient ingredient, TooltipFlag tooltipFlag) {
        List<Component> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(ingredient.key().aeKey()));
        tooltip.add(GenericStackDisplayHelper.createAmountTooltip(ingredient.asGenericStack()));
        return tooltip;
    }
}
