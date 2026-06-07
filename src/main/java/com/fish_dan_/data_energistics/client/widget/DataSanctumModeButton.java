package com.fish_dan_.data_energistics.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

import java.util.List;
import java.util.function.IntConsumer;

public class DataSanctumModeButton extends Button implements ITooltip {

    private static final int ICON_SIZE = 16;

    private final int mode;
    private final IntConsumer onChange;
    private boolean selected;

    public DataSanctumModeButton(int mode, IntConsumer onChange) {
        super(0, 0, ICON_SIZE, ICON_SIZE, Component.empty(), btn -> {
            if (btn instanceof DataSanctumModeButton button) {
                button.onChange.accept(button.mode);
            }
        }, DEFAULT_NARRATION);
        this.mode = mode;
        this.onChange = onChange;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = this.isHovered() ? 1 : 0;
        Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.selected || this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                .dest(this.getX() - 1, this.getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        guiGraphics.renderItem(getModeIcon(), this.getX(), this.getY() + 1 + yOffset);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("button.data_energistics.data_sanctum_status.mode"),
                Component.translatable("screen.data_energistics.data_sanctum_status.mode." + this.mode));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), ICON_SIZE, ICON_SIZE);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    private ItemStack getModeIcon() {
        return switch (this.mode) {
            case 1 -> new ItemStack(Items.ENDER_PEARL);
            case 2 -> new ItemStack(Items.END_PORTAL_FRAME);
            default -> new ItemStack(Items.GRAY_DYE);
        };
    }
}
