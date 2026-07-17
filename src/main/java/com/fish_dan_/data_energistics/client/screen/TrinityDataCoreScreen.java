package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/** Vanilla container shell whose complete presentation and interaction tree is owned by LDLib2. */
public class TrinityDataCoreScreen extends AbstractContainerScreen<TrinityDataCoreMenu> {

    public TrinityDataCoreScreen(TrinityDataCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        ModularUI modularUI = modularUI();
        this.imageWidth = (int) modularUI.getWidth();
        this.imageHeight = (int) modularUI.getHeight();
        super.init();
        setFocused(modularUI.getWidget());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.menu.getHostUiExtension().handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private ModularUI modularUI() {
        if (this.menu instanceof IModularUIHolderMenu holder) {
            ModularUI modularUI = holder.getModularUI();
            if (modularUI != null) {
                return modularUI;
            }
        }
        throw new IllegalStateException("Trinity Data Core screen requires a mounted LDLib2 ModularUI");
    }
}
