package com.fish_dan_.data_energistics.client.screen.storage;

import com.fish_dan_.data_energistics.client.screen.base.CompartmentScreen;
import com.fish_dan_.data_energistics.menu.CompositeWarehouseMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

public class CompositeWarehouseScreen extends CompartmentScreen<CompositeWarehouseMenu> {

    public CompositeWarehouseScreen(CompositeWarehouseMenu menu, Inventory playerInventory, Component title,
                                    ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
