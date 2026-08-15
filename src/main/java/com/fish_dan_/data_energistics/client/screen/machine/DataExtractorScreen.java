package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorAutoExportMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.widget.DataExtractorAutoExportButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.machine.DataExtractorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ProgressBar.Direction;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

public class DataExtractorScreen extends UpgradeableScreen<DataExtractorMenu> {

    private final DataExtractorToggleButton redstoneControlButton;
    private final DataExtractorToggleButton rangeVisibleButton;
    private final DataExtractorAutoExportButton autoExportButton;
    private final OutputSideActionButton outputSideButton;
    private final ProgressBar collectionProgressBar;

    public DataExtractorScreen(DataExtractorMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.widgets.add("display_component", new UpgradesPanel(menu.getSlots(DataExtractorMenu.DISPLAY_COMPONENT_INPUT)));
        this.redstoneControlButton = new DataExtractorToggleButton(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.redstone_control",
                "button.data_energistics.data_extractor.redstone_control.enabled",
                "button.data_energistics.data_extractor.redstone_control.disabled",
                this.menu::sendSetRedstoneControlled);
        this.addToLeftToolbar(this.redstoneControlButton);

        this.rangeVisibleButton = new DataExtractorToggleButton(
                Icon.PATTERN_TERMINAL_ALL,
                Icon.PATTERN_TERMINAL_VISIBLE,
                "button.data_energistics.range_visible",
                "button.data_energistics.data_extractor.range_visible.enabled",
                "button.data_energistics.data_extractor.range_visible.disabled",
                this.menu::sendSetRangeVisible);
        this.addToLeftToolbar(this.rangeVisibleButton);

        this.autoExportButton = new DataExtractorAutoExportButton(this.menu::sendSetAutoExportMode);
        this.addToLeftToolbar(this.autoExportButton);

        this.outputSideButton = new OutputSideActionButton(button -> openOutputConfig());
        this.addToLeftToolbar(this.outputSideButton);

        this.collectionProgressBar = new ProgressBar(this.menu, style.getImage("progressBar"), Direction.VERTICAL);
        widgets.add("progressBar", this.collectionProgressBar);
    }

    private void openOutputConfig() {
        if (this.menu.getHost() == null) {
            return;
        }

        this.switchToScreen(new DataExtractorOutputSideScreen(
                this,
                this.menu.getHost(),
                this.menu.getOutputSides(),
                this.menu::sendSetOutputSide));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.setTextContent("status", Component.translatable(
                this.menu.online ? "screen.data_energistics.status.online" : "screen.data_energistics.status.offline"));
        this.setTextContent("damage", translate("damage", this.menu.damagePerCycle));
        this.setTextContent("data_flow", translate("data_flow", this.menu.dataFlowPerCycle, this.menu.workIntervalSeconds));
        this.setTextContent("targets", translate("targets", this.menu.targetCount, this.menu.targetLimit));
        this.redstoneControlButton.setState(this.menu.redstoneControlled);
        this.rangeVisibleButton.setState(this.menu.rangeVisible);
        this.autoExportButton.setMode(this.menu.getAutoExportMode());
        this.outputSideButton.setVisibility(this.menu.getAutoExportMode() == DataExtractorAutoExportMode.CONTAINER);

        boolean hasProgress = this.menu.getMaxProgress() > 0;
        this.collectionProgressBar.visible = hasProgress;
        if (hasProgress) {
            int percent = this.menu.getCurrentProgress() * 100 / Math.max(1, this.menu.getMaxProgress());
            this.collectionProgressBar.setFullMsg(Component.literal(percent + "%"));
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.isActive() && slot.getItem().isEmpty()) {
            if (this.menu.getSlotSemantic(slot) == SlotSemantics.MACHINE_INPUT) {
                DataEnergisticsIcon.getBlitter("BACKGROUND_DATA_CARRIER")
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            } else if (this.menu.getSlotSemantic(slot) == DataExtractorMenu.CROP_INPUT) {
                DataEnergisticsIcon.getBlitter("BACKGROUND_CROP")
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            } else if (this.menu.getSlotSemantic(slot) == DataExtractorMenu.ORE_INPUT) {
                DataEnergisticsIcon.getBlitter("BACKGROUND_ORE")
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            } else if (this.menu.getSlotSemantic(slot) == DataExtractorMenu.SWORD_INPUT) {
                DataEnergisticsIcon.getBlitter("BACKGROUND_SWORD")
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            }
        }

        super.renderSlot(guiGraphics, slot);
    }

    private Component translate(String key, Object... args) {
        return Component.translatable("screen.data_energistics.data_extractor." + key, args);
    }
}
