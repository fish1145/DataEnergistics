package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

public class DataSanctumModeButton extends Button implements ITooltip {

    private static final int ICON_SIZE = 16;

    private final IntConsumer onChange;
    private int currentMode;

    public DataSanctumModeButton(IntConsumer onChange) {
        super(0, 0, ICON_SIZE, ICON_SIZE, Component.empty(), btn -> {
            if (btn instanceof DataSanctumModeButton button) {
                button.onChange.accept((button.currentMode + 1) % 3);
            }
        }, DEFAULT_NARRATION);
        this.onChange = onChange;
    }

    public void setCurrentMode(int mode) {
        this.currentMode = Math.max(0, Math.min(2, mode));
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = this.isHovered() ? 1 : 0;
        Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                .dest(this.getX() - 1, this.getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        DataEnergisticsIcon.getBlitter(getModeIconName())
                .dest(this.getX(), this.getY() + 1 + yOffset)
                .zOffset(3)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("button.data_energistics.data_sanctum_status.mode"),
                Component.translatable("screen.data_energistics.data_sanctum_status.mode." + this.currentMode));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), ICON_SIZE, ICON_SIZE);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    private String getModeIconName() {
        return switch (this.currentMode) {
            case 1 -> "POWER_UNIT_BLACK_HOLE";
            case 2 -> "POWER_UNIT_CRACK";
            default -> "POWER_UNIT_STANDBY";
        };
    }
}
