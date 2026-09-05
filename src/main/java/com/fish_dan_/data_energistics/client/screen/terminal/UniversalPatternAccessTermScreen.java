package com.fish_dan_.data_energistics.client.screen.terminal;

import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternAccessTermMenu;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class UniversalPatternAccessTermScreen extends PatternAccessTermScreen<UniversalPatternAccessTermMenu>
                                              implements Ae2NativeSlotHighlight {

    public UniversalPatternAccessTermScreen(UniversalPatternAccessTermMenu menu, Inventory playerInventory,
                                            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
