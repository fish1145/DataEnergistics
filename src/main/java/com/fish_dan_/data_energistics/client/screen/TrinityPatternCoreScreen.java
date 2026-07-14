package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.TrinityPatternCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

/** Retains AE2's container input protocol while LDLib2 owns the complete visual tree. */
public final class TrinityPatternCoreScreen extends AEBaseScreen<TrinityPatternCoreMenu> {

    /** Creates the thin AE2 screen shell required by its existing slot-click packet protocol. */
    public TrinityPatternCoreScreen(TrinityPatternCoreMenu menu, Inventory playerInventory, Component title,
                                    ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
