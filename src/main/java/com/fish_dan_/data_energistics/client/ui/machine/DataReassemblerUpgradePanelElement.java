package com.fish_dan_.data_energistics.client.ui.machine;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.style.Blitter;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the AE2-compatible external upgrade panel behind mapped LDLib2 item slots.
 */
final class DataReassemblerUpgradePanelElement extends UIElement {

    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int MAX_ROWS = 8;
    private static final int BORDER_COLOR = 0xFFF2F2F2;
    private static final Blitter BACKGROUND = Blitter.texture("guis/extra_panels.png", 128, 128);
    private static final Blitter INNER_CORNER = BACKGROUND.copy().src(12, 33, SLOT_SIZE, SLOT_SIZE);

    private final int slotCount;

    DataReassemblerUpgradePanelElement(int slotCount, Supplier<List<Component>> tooltipSupplier) {
        this.slotCount = slotCount;
        getLayout().width(widthForSlots(slotCount));
        getLayout().height(heightForSlots(slotCount));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> tooltip = tooltipSupplier.get();
            event.hoverTooltips = new HoverTooltips(List.copyOf(tooltip), null, null, ItemStack.EMPTY);
        });
    }

    /** Calculates the visual panel width so the external viewer margin follows every upgrade column. */
    static int widthForSlots(int slotCount) {
        int columns = (slotCount + MAX_ROWS - 1) / MAX_ROWS;
        return PADDING * 2 + columns * SLOT_SIZE;
    }

    /** Calculates the visual panel height so the external viewer margin follows the populated rows. */
    static int heightForSlots(int slotCount) {
        return PADDING * 2 + Math.min(MAX_ROWS, slotCount) * SLOT_SIZE;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        int originX = Math.round(getPositionX()) + PADDING;
        int originY = Math.round(getPositionY()) + PADDING;
        for (int i = 0; i < this.slotCount; i++) {
            int row = i % MAX_ROWS;
            int column = i / MAX_ROWS;
            int x = originX + column * SLOT_SIZE;
            int y = originY + row * SLOT_SIZE;
            boolean lastSlot = i + 1 >= this.slotCount;
            boolean lastRow = row + 1 >= MAX_ROWS;
            drawSlot(guiContext, x, y, column == 0, row == 0, i >= this.slotCount - MAX_ROWS, lastRow || lastSlot);
            if (column > 0 && lastSlot && !lastRow) {
                INNER_CORNER.dest(x, y + SLOT_SIZE).blit(guiContext.graphics);
            }
        }
        guiContext.graphics.hLine(originX - 4, originX + 11, originY, BORDER_COLOR);
        guiContext.graphics.hLine(
                originX - 4,
                originX + 11,
                originY + SLOT_SIZE * this.slotCount - 1,
                BORDER_COLOR);
        guiContext.graphics.vLine(
                originX - 5,
                originY - 1,
                originY + SLOT_SIZE * this.slotCount,
                BORDER_COLOR);
        guiContext.graphics.vLine(
                originX + 12,
                originY - 1,
                originY + SLOT_SIZE * this.slotCount,
                BORDER_COLOR);
    }

    private static void drawSlot(
                                 GUIContext guiContext,
                                 int x,
                                 int y,
                                 boolean borderLeft,
                                 boolean borderTop,
                                 boolean borderRight,
                                 boolean borderBottom) {
        int sourceX = PADDING;
        int sourceY = PADDING;
        int sourceWidth = SLOT_SIZE;
        int sourceHeight = SLOT_SIZE;
        if (borderLeft) {
            x -= PADDING;
            sourceX = 0;
            sourceWidth += PADDING;
        }
        if (borderRight) {
            sourceWidth += PADDING;
        }
        if (borderTop) {
            y -= PADDING;
            sourceY = 0;
            sourceHeight += PADDING;
        }
        if (borderBottom) {
            sourceHeight += PADDING + 2;
        }
        BACKGROUND.copy().src(sourceX, sourceY, sourceWidth, sourceHeight).dest(x, y).blit(guiContext.graphics);
    }

    /** Item slot variant that restores the reassembler's placement-toolbox empty-slot icon. */
    static final class UpgradeSlot extends ItemSlot {

        UpgradeSlot(Slot slot) {
            super(slot);
            DataRipperReassemblerMachineUiProviderImpl.configureSlot(this, false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            if (getValue().isEmpty()) {
                DataEnergisticsIcon.getBlitter("PLACEMENT_TOOLBOX")
                        .dest(Math.round(getContentX()), Math.round(getContentY()))
                        .blit(guiContext.graphics);
            }
            super.drawBackgroundAdditional(guiContext);
        }
    }
}
