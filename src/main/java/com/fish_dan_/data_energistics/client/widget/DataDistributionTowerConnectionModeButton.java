package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class DataDistributionTowerConnectionModeButton extends Button implements ITooltip {

    private final Consumer<ConnectionMode> onChange;
    private ConnectionMode mode = ConnectionMode.AE_AND_FE;

    public DataDistributionTowerConnectionModeButton(Consumer<ConnectionMode> onChange) {
        super(0, 0, 16, 16, Component.empty(), btn -> {
            if (btn instanceof DataDistributionTowerConnectionModeButton button) {
                button.onChange.accept(button.mode.next());
            }
        }, Button.DEFAULT_NARRATION);
        this.onChange = onChange;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode == null ? ConnectionMode.AE_AND_FE : mode;
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

        if (this.mode == ConnectionMode.AE_AND_FE) {
            DataEnergisticsIcon.getBlitter("POWER_UNIT_AF")
                    .dest(this.getX(), this.getY() + 1 + yOffset)
                    .zOffset(4)
                    .blit(guiGraphics);
            return;
        }

        Icon icon = this.mode == ConnectionMode.AE_ONLY ? Icon.POWER_UNIT_AE : Icon.POWER_UNIT_RF;
        icon.getBlitter()
                .dest(this.getX(), this.getY() + 1 + yOffset)
                .zOffset(4)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("button.data_energistics.data_distribution_tower.connection_mode"),
                Component.translatable("button.data_energistics.data_distribution_tower.connection_mode." + this.mode.getSerializedName()));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }
}
