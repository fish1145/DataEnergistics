package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.MePatternBufferMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

public class MePatternBufferScreen extends CompartmentScreen<MePatternBufferMenu> {

    public MePatternBufferScreen(MePatternBufferMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
