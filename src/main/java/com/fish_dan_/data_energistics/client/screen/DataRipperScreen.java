package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.ae2.DataRipperSettings;
import com.fish_dan_.data_energistics.client.widget.DataRipperSettingToggleButton;
import com.fish_dan_.data_energistics.menu.DataRipperMenu;
import com.fish_dan_.data_energistics.util.DataRipperPowerUtils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.YesNo;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;

import java.util.ArrayList;
import java.util.List;

public class DataRipperScreen extends AEBaseScreen<DataRipperMenu> {

    private static final int SLOT_SIZE = 16;
    private static final int UPGRADE_BACKGROUND_SIZE = 16;
    private static final int UPGRADE_BACKGROUND_OFFSET = (SLOT_SIZE - UPGRADE_BACKGROUND_SIZE) / 2;

    private final DataRipperSettingToggleButton accelerateButton;
    private final DataRipperSettingToggleButton redstoneControlButton;
    private final TextUpdater textUpdater = new TextUpdater();

    public DataRipperScreen(DataRipperMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.configureUpgradeSlots();
        if (menu.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }

        this.redstoneControlButton = new DataRipperSettingToggleButton(
                DataRipperSettings.REDSTONE_CONTROL,
                YesNo.NO,
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.redstone_control",
                "button.data_energistics.data_ripper.redstone_control.enabled",
                "button.data_energistics.data_ripper.redstone_control.disabled",
                "button.data_energistics.data_ripper.redstone_control.blocked");
        this.addToLeftToolbar(this.redstoneControlButton);

        this.accelerateButton = new DataRipperSettingToggleButton(
                DataRipperSettings.ACCELERATE,
                YesNo.YES,
                Icon.UNLOCKED,
                Icon.LOCKED,
                "button.data_energistics.data_ripper.accelerate",
                "button.data_energistics.data_ripper.accelerate.enabled",
                "button.data_energistics.data_ripper.accelerate.disabled",
                "button.data_energistics.data_ripper.accelerate.blocked");
        this.addToLeftToolbar(this.accelerateButton);
    }

    private void configureUpgradeSlots() {
        for (var slot : this.menu.getSlots(SlotSemantics.UPGRADE)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setIcon(null);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.isEmptyUpgradeSlot(this.hoveredSlot)) {
            this.drawTooltipWithHeader(guiGraphics, mouseX, mouseY, this.getCompatibleUpgradeTooltip());
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (this.isEmptyUpgradeSlot(slot)) {
            this.renderUpgradeSlotBackground(guiGraphics, slot);
        }

        super.renderSlot(guiGraphics, slot);
    }

    private boolean isEmptyUpgradeSlot(Slot slot) {
        return slot != null && slot.isActive() && slot.getItem().isEmpty() && this.menu.getSlotSemantic(slot) == SlotSemantics.UPGRADE;
    }

    private List<Component> getCompatibleUpgradeTooltip() {
        var tooltip = new ArrayList<Component>();
        tooltip.add(GuiText.CompatibleUpgrades.text());
        tooltip.addAll(Upgrades.getTooltipLinesForMachine(this.menu.getUpgrades().getUpgradableItem()));
        return tooltip;
    }

    private void renderUpgradeSlotBackground(GuiGraphics guiGraphics, Slot slot) {
        Icon.BACKGROUND_UPGRADE.getBlitter()
                .dest(slot.x + UPGRADE_BACKGROUND_OFFSET, slot.y + UPGRADE_BACKGROUND_OFFSET, UPGRADE_BACKGROUND_SIZE, UPGRADE_BACKGROUND_SIZE)
                .blit(guiGraphics);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (this.menu.targetBlacklisted) {
            this.accelerateButton.active = false;
            this.accelerateButton.set(YesNo.UNDECIDED);
        } else {
            this.accelerateButton.active = true;
            this.accelerateButton.set(this.menu.getAccelerate());
        }

        this.redstoneControlButton.set(this.menu.getRedstoneControl());
        this.textUpdater.update();
    }

    public void refreshGui() {
        this.textUpdater.update();
    }

    private class TextUpdater {

        void update() {
            if (DataRipperScreen.this.menu.targetBlacklisted) {
                this.updateBlacklisted();
            } else {
                this.updateNormal();
            }
        }

        private void updateBlacklisted() {
            this.set("enable", this.translatable("enable"));
            this.set("speed", this.translatable("speed", 0));
            this.set("energy", this.translatable("energy", DataRipperPowerUtils.formatDataFlowCost(0)));
            this.set("power_ratio", this.translatable("power_ratio", DataRipperPowerUtils.formatPercentage(0.0D)));
            this.set("multiplier", this.translatable("multiplier", "0.00x"));
        }

        private void updateNormal() {
            int energyCardCount = DataRipperScreen.this.menu.energyCardCount;
            double multiplier = DataRipperPowerUtils.getAdjustedExtraMultiplier(
                    DataRipperScreen.this.menu.multiplier,
                    DataRipperScreen.this.menu.inverterCardCount);
            int effectiveSpeed = DataRipperScreen.this.menu.effectiveSpeed;
            double finalPower = DataRipperPowerUtils.computeFinalPowerForProduct(effectiveSpeed, energyCardCount) * multiplier;
            double powerRatio = DataRipperPowerUtils.getRemainingRatio(energyCardCount);

            this.set(
                    "enable",
                    DataRipperScreen.this.menu.networkEnergySufficient == YesNo.YES ? null : this.translatable("warning_network_energy_insufficient"));
            this.set("speed", this.translatable("speed", effectiveSpeed));
            this.set("energy", this.translatable("energy", DataRipperPowerUtils.formatDataFlowCost(finalPower)));
            this.set("power_ratio", this.translatable("power_ratio", DataRipperPowerUtils.formatPercentage(powerRatio)));
            this.set("multiplier", this.translatable("multiplier", String.format("%.2fx", multiplier)));
        }

        private Component translatable(String key, Object... args) {
            return Component.translatable("screen.data_energistics.data_ripper." + key, args);
        }

        private void set(String id, Component component) {
            DataRipperScreen.this.setTextContent(id, component);
        }
    }
}
