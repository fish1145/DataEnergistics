package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

import java.util.List;
import java.util.function.Consumer;

public class DigitalStorageDepotOutputTypeCycleButton extends Button implements ITooltip {

    private final Consumer<DigitalStorageDepotOutputType> onChange;
    private DigitalStorageDepotOutputType currentType = DigitalStorageDepotOutputType.ITEMS;

    public DigitalStorageDepotOutputTypeCycleButton(Consumer<DigitalStorageDepotOutputType> onChange) {
        super(0, 0, 16, 16, Component.empty(), btn -> {
            if (btn instanceof DigitalStorageDepotOutputTypeCycleButton button) {
                button.onChange.accept(button.currentType.next());
            }
        }, Button.DEFAULT_NARRATION);
        this.onChange = onChange;
    }

    public void setCurrentType(DigitalStorageDepotOutputType type) {
        this.currentType = type == null ? DigitalStorageDepotOutputType.ITEMS : type;
    }

    public void setVisibility(boolean visibility) {
        this.visible = visibility;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = this.isHovered() ? 1 : 0;
        Icon background = this.isHovered()
                ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : (this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND);

        background.getBlitter()
                .dest(this.getX() - 1, this.getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        DataEnergisticsIcon.getBlitter(getCurrentIconName())
                .dest(this.getX(), this.getY() + 1 + yOffset)
                .zOffset(3)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("gui.data_energistics.set_output_sides.content_type"),
                getCurrentTooltip());
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    private String getCurrentIconName() {
        return switch (this.currentType) {
            case ITEMS -> "POWER_UNIT_I";
            case FLUIDS -> "POWER_UNIT_F";
            case KEYS -> "POWER_UNIT_K";
        };
    }

    private Component getCurrentTooltip() {
        return switch (this.currentType) {
            case ITEMS -> Component.translatable("tooltip.data_energistics.digital_storage_depot.items");
            case FLUIDS -> Component.translatable("tooltip.data_energistics.digital_storage_depot.fluids");
            case KEYS -> Component.translatable("tooltip.data_energistics.digital_storage_depot.keys");
        };
    }
}
