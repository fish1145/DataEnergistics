package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

/** Retains AEBaseScreen's fake-slot and carried-stack protocols while LDLib2 owns compartment presentation. */
public class CompartmentScreen<T extends CompartmentMenu> extends AEBaseScreen<T> {

    public CompartmentScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** Suppresses the legacy ScreenStyle surface; the mounted LDLib2 root owns the complete background. */
    @Override
    public void drawBG(GuiGraphics guiGraphics,
                       int offsetX,
                       int offsetY,
                       int mouseX,
                       int mouseY,
                       float partialTicks) {
        if (this.menu.getCompartmentType() == CompartmentType.PATTERN_BUFFER) {
            super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    /** Suppresses legacy title and inventory labels now supplied by the mounted LDLib2 root. */
    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.menu.getCompartmentType() == CompartmentType.PATTERN_BUFFER) {
            super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        }
    }
}
