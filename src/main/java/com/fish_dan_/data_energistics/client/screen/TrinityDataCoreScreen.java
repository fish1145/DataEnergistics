package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

/** Keeps Trinity host input on AE2 while its mounted LDLib2 tree owns the complete presentation. */
public class TrinityDataCoreScreen extends Ldlib2AeProtocolScreen<TrinityDataCoreMenu> {

    public TrinityDataCoreScreen(TrinityDataCoreMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.menu.getHostUiExtension().handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
