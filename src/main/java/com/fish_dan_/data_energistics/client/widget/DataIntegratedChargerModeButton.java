package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity.MachineMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import java.util.List;
import java.util.function.Consumer;

/** Cycles the integrated charger's server-authoritative processing mode. */
public final class DataIntegratedChargerModeButton extends Button implements ITooltip {

    private static final int ICON_SIZE = 16;
    private final Consumer<MachineMode> onChange;
    private MachineMode mode = MachineMode.POWDER;

    public DataIntegratedChargerModeButton(Consumer<MachineMode> onChange) {
        super(0, 0, ICON_SIZE, ICON_SIZE, Component.empty(), button -> {
            if (button instanceof DataIntegratedChargerModeButton modeButton) {
                modeButton.onChange.accept(modeButton.mode.next());
            }
        }, DEFAULT_NARRATION);
        this.onChange = onChange;
    }

    public void setMode(MachineMode mode) {
        this.mode = mode == null ? MachineMode.POWDER : mode;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        getModeIcon().dest(this.getX(), this.getY()).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("button.data_energistics.data_integrated_charger.machine_mode"),
                Component.translatable("button.data_energistics.data_integrated_charger.machine_mode." + modeKey()));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), ICON_SIZE, ICON_SIZE);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    private Blitter getModeIcon() {
        return switch (this.mode) {
            case POWDER -> Icon.PLACEMENT_ITEM.getBlitter();
            case CRYSTAL_GROWTH -> DataEnergisticsIcon.getBlitter("DATA_INTEGRATED_CHARGER_1");
            case CHARGER -> DataEnergisticsIcon.getBlitter("DATA_INTEGRATED_CHARGER_3");
            case INSCRIBER -> DataEnergisticsIcon.getBlitter("DATA_INTEGRATED_CHARGER_2");
        };
    }

    private String modeKey() {
        return switch (this.mode) {
            case POWDER -> "powder";
            case CRYSTAL_GROWTH -> "crystal_growth";
            case CHARGER -> "charger";
            case INSCRIBER -> "inscriber";
        };
    }
}
