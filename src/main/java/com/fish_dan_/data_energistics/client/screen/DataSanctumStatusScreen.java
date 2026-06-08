package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.client.widget.DataSanctumModeButton;
import com.fish_dan_.data_energistics.menu.DataSanctumStatusMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;

public class DataSanctumStatusScreen extends AEBaseScreen<DataSanctumStatusMenu> {

    private final DataSanctumModeButton modeButton;
    private final DataExtractorToggleButton rangeVisibleButton;

    public DataSanctumStatusScreen(DataSanctumStatusMenu menu, Inventory playerInventory, Component title,
                                   ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.modeButton = new DataSanctumModeButton(this.menu::sendSetMode);
        addToLeftToolbar(this.modeButton);
        this.rangeVisibleButton = new DataExtractorToggleButton(
                Icon.PATTERN_TERMINAL_ALL,
                Icon.PATTERN_TERMINAL_VISIBLE,
                "button.data_energistics.range_visible",
                "button.data_energistics.data_sanctum_status.range_visible.enabled",
                "button.data_energistics.data_sanctum_status.range_visible.disabled",
                this.menu::sendSetRangeVisible);
        addToLeftToolbar(this.rangeVisibleButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent("dialog_title", Component.translatable("block.data_energistics.data_sanctum"));
        setTextContent("online", Component.translatable(
                this.menu.online ? "screen.data_energistics.status.online" : "screen.data_energistics.status.offline"));
        setTextContent("active", Component.translatable(
                "screen.data_energistics.data_sanctum_status.active",
                Component.translatable(this.menu.active ? "text.data_energistics.on" : "text.data_energistics.off")));
        setTextContent("mode", Component.translatable(
                "screen.data_energistics.data_sanctum_status.mode",
                Component.translatable("screen.data_energistics.data_sanctum_status.mode." + this.menu.mode)));
        setTextContent("network_port", Component.translatable(
                "screen.data_energistics.data_sanctum_status.network_port",
                Component.translatable(this.menu.networkPortAvailable ? "text.data_energistics.available" : "text.data_energistics.unavailable")));
        this.modeButton.setCurrentMode(this.menu.mode);
        this.rangeVisibleButton.setVisibility(this.menu.mode == 1);
        this.rangeVisibleButton.setState(this.menu.rangeVisible);
    }
}
