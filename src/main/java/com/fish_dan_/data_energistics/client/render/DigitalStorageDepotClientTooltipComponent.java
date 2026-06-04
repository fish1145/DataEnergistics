package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotTooltipComponent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.StackSizeRenderer;
import org.joml.Matrix4f;

import java.util.List;

public class DigitalStorageDepotClientTooltipComponent implements ClientTooltipComponent {

    private static final int SLOT_SIZE = 17;
    private static final int TITLE_HEIGHT = 10;
    private static final int SECTION_GAP = 2;
    private static final int ITEM_COLUMNS = 7;
    private static final int GENERIC_COLUMNS = 3;
    private static final int TITLE_COLOR = 0x7E7E7E;

    private final DigitalStorageDepotTooltipComponent component;
    private final Component itemsTitle = Component.translatable("tooltip.data_energistics.digital_storage_depot.items");
    private final Component fluidsTitle = Component.translatable("tooltip.data_energistics.digital_storage_depot.fluids");
    private final Component keysTitle = Component.translatable("tooltip.data_energistics.digital_storage_depot.keys");

    public DigitalStorageDepotClientTooltipComponent(DigitalStorageDepotTooltipComponent component) {
        this.component = component;
    }

    @Override
    public int getHeight() {
        int height = 0;
        height += getSectionHeight(this.component.items(), ITEM_COLUMNS);
        height += getSectionHeight(this.component.fluids(), GENERIC_COLUMNS);
        height += getSectionHeight(this.component.keys(), GENERIC_COLUMNS);
        return Math.max(0, height - SECTION_GAP);
    }

    @Override
    public int getWidth(Font font) {
        int width = 0;
        width = Math.max(width, getSectionWidth(font, this.itemsTitle, this.component.items(), ITEM_COLUMNS));
        width = Math.max(width, getSectionWidth(font, this.fluidsTitle, this.component.fluids(), GENERIC_COLUMNS));
        width = Math.max(width, getSectionWidth(font, this.keysTitle, this.component.keys(), GENERIC_COLUMNS));
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
        int yOffset = y;
        yOffset = renderSectionTitle(font, this.itemsTitle, this.component.items(), ITEM_COLUMNS, x, yOffset, matrix, bufferSource);
        yOffset = renderSectionTitle(font, this.fluidsTitle, this.component.fluids(), GENERIC_COLUMNS, x, yOffset, matrix, bufferSource);
        renderSectionTitle(font, this.keysTitle, this.component.keys(), GENERIC_COLUMNS, x, yOffset, matrix, bufferSource);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        int yOffset = y;
        yOffset = renderSectionIcons(font, this.component.items(), ITEM_COLUMNS, x, yOffset, guiGraphics);
        yOffset = renderSectionIcons(font, this.component.fluids(), GENERIC_COLUMNS, x, yOffset, guiGraphics);
        renderSectionIcons(font, this.component.keys(), GENERIC_COLUMNS, x, yOffset, guiGraphics);
    }

    private int renderSectionTitle(Font font, Component title, List<GenericStack> stacks, int columns, int x, int y,
                                   Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
        if (stacks.isEmpty()) {
            return y;
        }

        font.drawInBatch(title, x, y, TITLE_COLOR, false, matrix, bufferSource,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        return y + getSectionHeight(stacks, columns);
    }

    private int renderSectionIcons(Font font, List<GenericStack> stacks, int columns, int x, int y, GuiGraphics guiGraphics) {
        if (stacks.isEmpty()) {
            return y;
        }

        int iconY = y + TITLE_HEIGHT;
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack stack = stacks.get(i);
            int iconX = x + i % columns * SLOT_SIZE;
            int rowY = iconY + i / columns * SLOT_SIZE;
            CustomKeyGuiRenderer.draw(minecraft, guiGraphics, iconX, rowY, stack.what());
            StackSizeRenderer.renderSizeLabel(
                    guiGraphics,
                    font,
                    iconX,
                    rowY,
                    stack.what().formatAmount(stack.amount(), AmountFormat.SLOT),
                    false);
        }
        return y + getSectionHeight(stacks, columns);
    }

    private static int getSectionHeight(List<GenericStack> stacks, int columns) {
        if (stacks.isEmpty()) {
            return 0;
        }

        int rows = (stacks.size() + columns - 1) / columns;
        return TITLE_HEIGHT + rows * SLOT_SIZE + SECTION_GAP;
    }

    private static int getSectionWidth(Font font, Component title, List<GenericStack> stacks, int columns) {
        if (stacks.isEmpty()) {
            return 0;
        }

        int visibleColumns = Math.min(columns, stacks.size());
        return Math.max(font.width(title), visibleColumns * SLOT_SIZE);
    }
}
