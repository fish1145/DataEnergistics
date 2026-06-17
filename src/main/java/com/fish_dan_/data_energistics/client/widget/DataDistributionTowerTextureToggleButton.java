package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

public class DataDistributionTowerTextureToggleButton extends Button implements ITooltip {

    private final String enabledIcon;
    private final String disabledIcon;
    private final String titleKey;
    private final String enabledKey;
    private final String disabledKey;
    private final Consumer<Boolean> onChange;
    @Setter
    private boolean state;
    private float visualScale = 1.0F;
    @Setter
    private int visualZOffset;

    public DataDistributionTowerTextureToggleButton(
                                                    String enabledIcon,
                                                    String disabledIcon,
                                                    String titleKey,
                                                    String enabledKey,
                                                    String disabledKey,
                                                    Consumer<Boolean> onChange) {
        super(0, 0, 16, 16, Component.empty(), btn -> {
            if (btn instanceof DataDistributionTowerTextureToggleButton button) {
                button.onChange.accept(!button.state);
            }
        }, Button.DEFAULT_NARRATION);
        this.enabledIcon = enabledIcon;
        this.disabledIcon = disabledIcon;
        this.titleKey = titleKey;
        this.enabledKey = enabledKey;
        this.disabledKey = disabledKey;
        this.onChange = onChange;
    }

    public void setVisualScale(float visualScale) {
        this.visualScale = visualScale;
        this.width = scale(16);
        this.height = scale(16);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = this.isHovered() ? 1 : 0;
        Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.isFocused() || this.state ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                .dest(this.getX() - scale(1), this.getY() + scale(yOffset), scale(18), scale(20))
                .zOffset(this.visualZOffset + 2)
                .blit(guiGraphics);

        DataEnergisticsIcon.getBlitter(this.state ? this.enabledIcon : this.disabledIcon)
                .dest(this.getX(), this.getY() + scale(1 + yOffset), scale(16), scale(16))
                .zOffset(this.visualZOffset + 4)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable(this.titleKey),
                Component.translatable(this.state ? this.enabledKey : this.disabledKey));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), scale(16), scale(16));
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    private int scale(int value) {
        return Math.round(value * this.visualScale);
    }
}
