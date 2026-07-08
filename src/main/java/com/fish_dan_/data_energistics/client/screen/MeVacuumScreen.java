package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.MeVacuumMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

public class MeVacuumScreen extends AEBaseScreen<MeVacuumMenu> {

    public MeVacuumScreen(MeVacuumMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), menu.getHost()));
    }
}
