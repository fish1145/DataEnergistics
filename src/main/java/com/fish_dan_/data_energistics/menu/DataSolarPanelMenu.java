package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;

import appeng.api.util.IConfigManager;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;

public class DataSolarPanelMenu extends UpgradeableMenu<DataSolarPanelMenuHost> {

    private static final String ACTION_SET_REDSTONE_CONTROL = "set_redstone_control";

    @GuiSync(790)
    public boolean online;
    @GuiSync(791)
    public boolean daytime;
    @GuiSync(792)
    public int currentPower;
    @GuiSync(793)
    public int maxPower;
    @GuiSync(794)
    public int generatedPower;
    @GuiSync(795)
    public int speedCardCount;
    @GuiSync(796)
    public int energyCardCount;
    @GuiSync(797)
    public boolean redstoneControlled;

    public DataSolarPanelMenu(int id, Inventory playerInventory, DataSolarPanelMenuHost host) {
        super(ModMenus.DATA_SOLAR_PANEL.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_REDSTONE_CONTROL, Boolean.class, this::setRedstoneControlled);
    }

    @Override
    protected void setupInventorySlots() {}

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            var host = this.getHost();
            this.online = host.isOnline();
            this.daytime = host.isDaytime();
            this.currentPower = (int) Math.round(host.getAECurrentPower());
            this.maxPower = (int) Math.round(host.getAEMaxPower());
            this.generatedPower = (int) Math.round(host.getGeneratedPowerPerTick());
            this.speedCardCount = com.fish_dan_.data_energistics.blockentity.DataSolarPanelBlockEntity.getSpeedCardCount(host.getUpgrades());
            this.energyCardCount = com.fish_dan_.data_energistics.blockentity.DataSolarPanelBlockEntity.getEnergyCardCount(host.getUpgrades());
            this.redstoneControlled = host.isRedstoneControlled();
        }
        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // This menu only exposes upgrade slots and synced status text.
    }

    public void sendSetRedstoneControlled(boolean enabled) {
        sendClientAction(ACTION_SET_REDSTONE_CONTROL, enabled);
    }

    private void setRedstoneControlled(Boolean enabled) {
        if (enabled == null || this.getHost() == null) {
            return;
        }

        this.redstoneControlled = this.getHost().setRedstoneControlled(enabled);
        broadcastChanges();
    }
}
