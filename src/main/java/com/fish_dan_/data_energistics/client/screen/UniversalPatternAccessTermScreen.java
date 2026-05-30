package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.universal.UniversalPatternAccessTermMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.style.ScreenStyle;

public class UniversalPatternAccessTermScreen extends PatternAccessTermScreen<UniversalPatternAccessTermMenu> {

    public UniversalPatternAccessTermScreen(UniversalPatternAccessTermMenu menu, Inventory playerInventory,
                                            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
