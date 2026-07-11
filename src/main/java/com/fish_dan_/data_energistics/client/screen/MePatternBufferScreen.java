package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.MePatternBufferMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;

public class MePatternBufferScreen extends CompartmentScreen<MePatternBufferMenu> {

    public MePatternBufferScreen(MePatternBufferMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.isActive() && slot.getItem().isEmpty() && this.menu.getSlotSemantic(slot) == CompartmentMenu.COMPARTMENT_PATTERN) {
            Icon.BACKGROUND_BLANK_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        }
        super.renderSlot(guiGraphics, slot);
    }
}
