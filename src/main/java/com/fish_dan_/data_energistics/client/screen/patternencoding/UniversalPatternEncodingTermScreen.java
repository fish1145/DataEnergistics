package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;

import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class UniversalPatternEncodingTermScreen
                                                extends PatternEncodingPreviewScreen<UniversalPatternEncodingTermMenu> {

    public UniversalPatternEncodingTermScreen(UniversalPatternEncodingTermMenu menu, Inventory playerInventory,
                                              Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
