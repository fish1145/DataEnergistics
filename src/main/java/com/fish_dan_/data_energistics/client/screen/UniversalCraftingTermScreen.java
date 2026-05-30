package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.me.items.CraftingTermScreen;
import appeng.client.gui.style.ScreenStyle;

public class UniversalCraftingTermScreen extends CraftingTermScreen<UniversalCraftingTermMenu> {

    public UniversalCraftingTermScreen(UniversalCraftingTermMenu menu, Inventory playerInventory,
                                       Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
