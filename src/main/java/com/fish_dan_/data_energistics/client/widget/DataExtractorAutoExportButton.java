package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorAutoExportMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class DataExtractorAutoExportButton extends IconButton {

    private final Consumer<DataExtractorAutoExportMode> onChange;
    private DataExtractorAutoExportMode mode = DataExtractorAutoExportMode.OFF;

    public DataExtractorAutoExportButton(Consumer<DataExtractorAutoExportMode> onChange) {
        super(btn -> {
            if (btn instanceof DataExtractorAutoExportButton button) {
                button.onChange.accept(button.mode.next());
            }
        });
        this.onChange = onChange;
    }

    public void setMode(DataExtractorAutoExportMode mode) {
        this.mode = mode == null ? DataExtractorAutoExportMode.OFF : mode;
    }

    @Override
    protected Icon getIcon() {
        return switch (this.mode) {
            case OFF -> Icon.AUTO_EXPORT_OFF;
            case CONTAINER -> Icon.AUTO_EXPORT_ON;
            case AE -> Icon.TOOLBAR_BUTTON_BACKGROUND;
        };
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.mode != DataExtractorAutoExportMode.AE) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (!this.visible) {
            return;
        }

        int yOffset = this.isHovered() ? 1 : 0;
        Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                .dest(this.getX() - 1, this.getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        DataEnergisticsIcon.getBlitter("AUTO_EXPORT_AE")
                .dest(this.getX(), this.getY() + 1 + yOffset)
                .zOffset(3)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("button.data_energistics.auto_export"),
                Component.translatable("button.data_energistics.data_extractor.auto_export." + this.mode.getSerializedName()));
    }
}
