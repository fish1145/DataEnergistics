package com.fish_dan_.data_energistics.client.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;

import net.minecraft.client.Minecraft;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import java.util.List;

/**
 * Renders a native AE2 generic stack while leaving viewer identity registration to an adapter.
 */
public final class DataReassemblerGenericStackSlot extends UIElement {

    private static final int SLOT_SIZE = 18;

    private final GenericStack stack;

    public DataReassemblerGenericStackSlot(GenericStack stack) {
        if (stack.amount() <= 0L) {
            Data_Energistics.LOGGER.error("Generic recipe slot received a non-positive amount: {}", stack.amount());
            throw new IllegalArgumentException("Generic recipe slot amount must be positive: " + stack.amount());
        }
        this.stack = stack;
        getLayout().width(SLOT_SIZE);
        getLayout().height(SLOT_SIZE);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(stack.what().getDisplayName(), GenericStackDisplayHelper.createAmountTooltip(stack)),
                null,
                null,
                null));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        guiContext.graphics.pose().pushPose();
        guiContext.graphics.pose().translate(0.0F, 0.0F, 100.0F);
        CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiContext.graphics, x + 1, y + 1, this.stack.what());
        GenericStackDisplayHelper.renderSmallOverlay(
                guiContext.graphics,
                x,
                y,
                GenericStackDisplayHelper.formatCompactAmount(this.stack));
        guiContext.graphics.pose().popPose();
    }
}
