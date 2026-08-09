package com.fish_dan_.data_energistics.client.screen.base;

import com.fish_dan_.data_energistics.client.screen.Ldlib2AeProtocolScreen;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

/** Retains AEBaseScreen's fake-slot and carried-stack protocols while LDLib2 owns compartment presentation. */
public class CompartmentScreen<T extends CompartmentMenu> extends Ldlib2AeProtocolScreen<T> {

    public CompartmentScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
