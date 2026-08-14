package com.fish_dan_.data_energistics.client.screen.trinity;

import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/** Thin vanilla container shell whose complete presentation is owned by LDLib2. */
public final class TrinityInformationExchangeDepotScreen
                                                         extends AbstractContainerScreen<TrinityInformationExchangeDepotMenu> {

    public TrinityInformationExchangeDepotScreen(
                                                 TrinityInformationExchangeDepotMenu menu,
                                                 Inventory playerInventory,
                                                 Component title) {
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private ModularUI modularUI() {
        IModularUIHolderMenu holder = (IModularUIHolderMenu) (Object) this.menu;
        ModularUI modularUI = holder.getModularUI();
        if (modularUI != null) {
            return modularUI;
        }
        throw new IllegalStateException("Trinity information exchange depot screen requires a mounted LDLib2 UI");
    }
}
