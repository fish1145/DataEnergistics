package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.menu.machine.DataAsynchronousProcessingFactoryMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

public final class DataAsynchronousProcessingFactoryScreen
                                                           extends DataRipperReassemblerScreen<DataAsynchronousProcessingFactoryMenu> {

    public DataAsynchronousProcessingFactoryScreen(DataAsynchronousProcessingFactoryMenu menu,
                                                   Inventory playerInventory,
                                                   Component title,
                                                   ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
