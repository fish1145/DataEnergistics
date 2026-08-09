package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.screen.base.CompartmentScreen;
import com.fish_dan_.data_energistics.menu.MePatternBufferMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

/** Keeps AEBaseScreen input routing while the mounted LDLib2 tree owns all pattern-buffer presentation. */
public class MePatternBufferScreen extends CompartmentScreen<MePatternBufferMenu> {

    public MePatternBufferScreen(MePatternBufferMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
