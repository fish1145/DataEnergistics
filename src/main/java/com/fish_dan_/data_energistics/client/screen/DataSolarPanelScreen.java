package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.DataSolarPanelMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;

public class DataSolarPanelScreen extends UpgradeableScreen<DataSolarPanelMenu> {

    private final DataExtractorToggleButton redstoneControlButton;

    public DataSolarPanelScreen(DataSolarPanelMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.redstoneControlButton = new DataExtractorToggleButton(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.redstone_control",
                "button.data_energistics.me_solar_panel.redstone_control.enabled",
                "button.data_energistics.me_solar_panel.redstone_control.disabled",
                this.menu::sendSetRedstoneControlled);
        this.addToLeftToolbar(this.redstoneControlButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.setTextContent("time", Component.translatable(
                "screen.data_energistics.me_solar_panel.time",
                Component.translatable(this.menu.daytime ? "text.data_energistics.time.day" : "text.data_energistics.time.night")));
        this.setTextContent("generation", Component.translatable(
                "screen.data_energistics.me_solar_panel.generation",
                this.menu.generatedPower));
        this.redstoneControlButton.setState(this.menu.redstoneControlled);
    }
}
