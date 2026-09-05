package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.menu.machine.DataRipperReassemblerMenu;

import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DataRipperReassemblerMainScreen extends DataRipperReassemblerScreen<DataRipperReassemblerMenu> {

    public DataRipperReassemblerMainScreen(DataRipperReassemblerMenu menu,
                                           Inventory playerInventory,
                                           Component title,
                                           ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
