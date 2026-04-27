package com.fish_dan_.data_energistics.client.screen;

import appeng.api.config.YesNo;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.CommonButtons;
import appeng.util.Platform;
import com.fish_dan_.data_energistics.ae2.DataRipperSettings;
import com.fish_dan_.data_energistics.client.widget.DataRipperSettingToggleButton;
import com.fish_dan_.data_energistics.menu.DataRipperMenu;
import com.fish_dan_.data_energistics.util.DataRipperPowerUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DataRipperScreen extends UpgradeableScreen<DataRipperMenu> {
    private final DataRipperSettingToggleButton accelerateButton;
    private final DataRipperSettingToggleButton redstoneControlButton;
    private final TextUpdater textUpdater = new TextUpdater();

    public DataRipperScreen(DataRipperMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.addToLeftToolbar(CommonButtons.togglePowerUnit());

        this.accelerateButton = new DataRipperSettingToggleButton(
                DataRipperSettings.ACCELERATE,
                YesNo.YES,
                Icon.OVERLAY_ON,
                Icon.OVERLAY_OFF,
                "button.data_energistics.data_ripper.accelerate",
                "button.data_energistics.data_ripper.accelerate.enabled",
                "button.data_energistics.data_ripper.accelerate.disabled",
                "button.data_energistics.data_ripper.accelerate.blocked"
        );
        this.addToLeftToolbar(this.accelerateButton);

        this.redstoneControlButton = new DataRipperSettingToggleButton(
                DataRipperSettings.REDSTONE_CONTROL,
                YesNo.NO,
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.data_ripper.redstone_control",
                "button.data_energistics.data_ripper.redstone_control.enabled",
                "button.data_energistics.data_ripper.redstone_control.disabled",
                "button.data_energistics.data_ripper.redstone_control.blocked"
        );
        this.addToLeftToolbar(this.redstoneControlButton);
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
            this.set("energy", this.translatable("energy", Platform.formatPower(0, false)));
            this.set("power_ratio", this.translatable("power_ratio", DataRipperPowerUtils.formatPercentage(0.0D)));
            this.set("multiplier", this.translatable("multiplier", "0.00x"));
        }

        private void updateNormal() {
            int energyCardCount = DataRipperScreen.this.menu.energyCardCount;
            double multiplier = DataRipperScreen.this.menu.multiplier;
            int effectiveSpeed = DataRipperScreen.this.menu.effectiveSpeed;
            double finalPower = DataRipperPowerUtils.computeFinalPowerForProduct(effectiveSpeed, energyCardCount);
            double powerRatio = DataRipperPowerUtils.getRemainingRatio(energyCardCount);

            this.set(
                    "enable",
                    DataRipperScreen.this.menu.networkEnergySufficient == YesNo.YES
                            ? null
                            : this.translatable("warning_network_energy_insufficient")
            );
            this.set("speed", this.translatable("speed", effectiveSpeed));
            this.set("energy", this.translatable("energy", Platform.formatPower(finalPower, false)));
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
