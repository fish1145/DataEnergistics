package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.DataSanctumStatusMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

public class DataSanctumStatusScreen extends AEBaseScreen<DataSanctumStatusMenu> {

    public DataSanctumStatusScreen(DataSanctumStatusMenu menu, Inventory playerInventory, Component title,
                                   ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent("dialog_title", Component.translatable(
                this.menu.online ? "screen.data_energistics.data_sanctum_status.title.online" : "screen.data_energistics.data_sanctum_status.title.offline"));
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
    }
}
