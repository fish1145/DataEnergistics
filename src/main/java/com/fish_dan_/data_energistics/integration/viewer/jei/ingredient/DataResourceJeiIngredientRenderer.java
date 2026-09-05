package com.fish_dan_.data_energistics.integration.viewer.jei.ingredient;

import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.key.CustomKeyGuiRenderer;

import appeng.api.client.AEKeyRendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredientRenderer;
import org.jspecify.annotations.Nullable;

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

    /**
     * JEI 19.x still requires this abstract entry point; forward it to the context-aware API.
     *
     * @deprecated use the context-aware overload
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public List<Component> getTooltip(DataResourceJeiIngredient ingredient, TooltipFlag tooltipFlag) {
        return getTooltip(ingredient, TooltipContext.EMPTY, null, tooltipFlag);
    }

    @Override
    public List<Component> getTooltip(DataResourceJeiIngredient ingredient, TooltipContext tooltipContext,
                                      @Nullable Player player, TooltipFlag tooltipFlag) {
        List<Component> tooltip = new ObjectArrayList<>(AEKeyRendering.getTooltip(ingredient.key().aeKey()));
        tooltip.add(GenericStackDisplayHelper.createAmountTooltip(ingredient.asGenericStack()));
        return tooltip;
    }
}
