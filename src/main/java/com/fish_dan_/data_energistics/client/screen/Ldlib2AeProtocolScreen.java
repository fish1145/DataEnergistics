package com.fish_dan_.data_energistics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;

/**
 * Retains AE2's fake-slot, carried-stack, and keyboard protocols while a mounted LDLib2 tree owns every visual.
 */
public abstract class Ldlib2AeProtocolScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {

    protected Ldlib2AeProtocolScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        style.getText().keySet().forEach(textId -> setTextHidden(textId, true));
    }

    /**
     * Prevents AE2 from registering its legacy vertical toolbar in the widget container.
     */
    @Override
    protected final boolean shouldAddToolbar() {
        return false;
    }

    /**
     * Prevents the legacy ScreenStyle background from drawing beneath the mounted LDLib2 root.
     */
    @Override
    public final void drawBG(GuiGraphics guiGraphics,
                             int offsetX,
                             int offsetY,
                             int mouseX,
                             int mouseY,
                             float partialTicks) {}

    /**
     * Prevents legacy ScreenStyle labels from drawing over LDLib2-owned text.
     */
    @Override
    public final void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {}
}
