package com.fish_dan_.data_energistics.client.screen.terminal;

import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.menu.universal.UniversalMEStorageMenu;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class UniversalMEStorageScreen extends MEStorageScreen<UniversalMEStorageMenu> implements Ae2NativeSlotHighlight {

    public UniversalMEStorageScreen(UniversalMEStorageMenu menu, Inventory playerInventory,
                                    Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
