package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

public class TrinityDataCoreScreen extends AEBaseScreen<TrinityDataCoreMenu> {

    public TrinityDataCoreScreen(TrinityDataCoreMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("dialog_title", Component.translatable("block.data_energistics.trinity_data_core"));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.menu.getHostUiExtension().handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
