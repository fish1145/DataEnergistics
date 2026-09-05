package com.fish_dan_.data_energistics.client.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class OutputSideDisplayButton extends IconButton {

    private static final Component TOOLTIP_ON = Component.translatable("gui.data_energistics.set_output_sides.on");
    private static final Component TOOLTIP_OFF = Component.translatable("gui.data_energistics.set_output_sides.off");

    private ItemStack display = ItemStack.EMPTY;
    private boolean on;

    public OutputSideDisplayButton(Button.OnPress onPress) {
        super(onPress);
    }

    public void setDisplay(ItemLike display) {
        this.display = new ItemStack(display);
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public boolean isOn() {
        return this.on;
    }

    public void flip() {
        this.on = !this.on;
    }

    @Override
    public Component getMessage() {
        return this.on ? TOOLTIP_ON : TOOLTIP_OFF;
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }
        int yOffset = this.isHovered() ? 1 : 0;
        Icon bgIcon = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : (this.on ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND);
        bgIcon.getBlitter().dest(this.getX() - 1, this.getY() + yOffset, 18, 20).zOffset(2).blit(guiGraphics);
        if (!this.display.isEmpty()) {
            guiGraphics.renderItem(this.display, this.getX(), this.getY() + 1 + yOffset, 0, 3);
        }
    }
}
