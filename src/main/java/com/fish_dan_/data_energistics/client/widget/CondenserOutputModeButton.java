package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.accessor.condenser.CondenserMenuAccessor;
import com.fish_dan_.data_energistics.ae2.settings.CondenserOutputMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import appeng.api.config.CondenserOutput;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import java.util.List;

public class CondenserOutputModeButton extends IconButton {

    private static final int RADIX_CONTAINMENT_SPHERE_REQUIRED_POWER = 131072;

    private final CondenserMenuAccessor menu;
    private CondenserOutputMode mode = CondenserOutputMode.TRASH;

    public CondenserOutputModeButton(CondenserMenuAccessor menu) {
        super(btn -> {});
        this.menu = menu;
    }

    public void setMode(CondenserOutputMode mode) {
        this.mode = mode == null ? CondenserOutputMode.TRASH : mode;
    }

    @Override
    public void onPress() {
        var nextMode = CondenserOutputMode.fromOrdinal((this.mode.ordinal() + 1) % CondenserOutputMode.values().length);
        this.menu.dataEnergistics$setCondenserOutputMode(nextMode.ordinal());
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.mode != CondenserOutputMode.RADIX_CONTAINMENT_SPHERE) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (!this.visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        Icon background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        Blitter blitter = DataEnergisticsIcon.getBlitter("CONDENSER_OUTPUT_RADIX_CONTAINMENT_SPHERE");
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    protected Icon getIcon() {
        return switch (this.mode) {
            case MATTER_BALLS -> Icon.CONDENSER_OUTPUT_MATTER_BALL;
            case SINGULARITY -> Icon.CONDENSER_OUTPUT_SINGULARITY;
            case RADIX_CONTAINMENT_SPHERE -> Icon.TOOLBAR_BUTTON_BACKGROUND;
            default -> Icon.CONDENSER_OUTPUT_TRASH;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        return switch (this.mode) {
            case MATTER_BALLS -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("button.data_energistics.condenser_output.matter_balls"),
                    Component.translatable(
                            "button.data_energistics.condenser_output.power",
                            CondenserOutput.MATTER_BALLS.requiredPower));
            case SINGULARITY -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("button.data_energistics.condenser_output.singularity"),
                    Component.translatable(
                            "button.data_energistics.condenser_output.power",
                            CondenserOutput.SINGULARITY.requiredPower));
            case RADIX_CONTAINMENT_SPHERE -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("item.data_energistics.radix_containment_sphere"),
                    Component.translatable("button.data_energistics.condenser_output.radix_containment_sphere.detail"),
                    Component.translatable(
                            "button.data_energistics.condenser_output.power",
                            RADIX_CONTAINMENT_SPHERE_REQUIRED_POWER));
            default -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("button.data_energistics.condenser_output.trash"));
        };
    }
}
