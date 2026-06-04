package com.fish_dan_.data_energistics.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import java.util.List;

public class DigitalStorageDepotOutputTypeButton extends IconButton {

    private static final int BUTTON_SIZE = 8;

    private final String shortLabel;
    private final Component tooltip;
    private boolean selected;

    public DigitalStorageDepotOutputTypeButton(String shortLabel, Component tooltip, Button.OnPress onPress) {
        super(onPress);
        this.shortLabel = shortLabel;
        this.tooltip = tooltip;
        this.setWidth(BUTTON_SIZE);
        this.setHeight(BUTTON_SIZE);
        this.setMessage(tooltip);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = this.isHovered() ? 1 : 0;
        Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.selected ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                .dest(this.getX(), this.getY() + yOffset, this.width, this.height)
                .zOffset(2)
                .blit(guiGraphics);

        var font = Minecraft.getInstance().font;
        int textColor = this.selected ? 0xFFFFFF : 0xD0D0D0;
        int textX = this.getX() + (this.width - font.width(this.shortLabel)) / 2;
        int textY = this.getY() + yOffset;
        guiGraphics.drawString(font, this.shortLabel, textX, textY, textColor, false);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(this.tooltip);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.active && this.visible && mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + BUTTON_SIZE && mouseY < this.getY() + BUTTON_SIZE;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), BUTTON_SIZE, BUTTON_SIZE);
    }
}
