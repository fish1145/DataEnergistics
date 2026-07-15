package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.universal.UniversalMEStorageMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;

public class UniversalMEStorageScreen extends MEStorageScreen<UniversalMEStorageMenu> implements Ae2NativeSlotHighlight {

    public UniversalMEStorageScreen(UniversalMEStorageMenu menu, Inventory playerInventory,
                                    Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
