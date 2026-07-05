package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.menu.CompartmentSlotLabel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.IOptionalSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
public abstract class CompartmentOptionalSlotLabelMixin<T extends AEBaseMenu> extends AbstractContainerScreen<T> {

    @Unique
    private static final ResourceLocation COMPOSITE_WAREHOUSE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "textures/guis/composite_warehouse.png");
    @Unique
    private static final int SLOT_SOURCE_LEFT = 151;
    @Unique
    private static final int SLOT_SOURCE_TOP = 28;
    @Unique
    private static final int SLOT_SOURCE_STEP = 18;
    @Unique
    private static final int SLOT_TEXTURE_SIZE = 18;
    @Unique
    private static final int TEXTURE_WIDTH = 176;
    @Unique
    private static final int TEXTURE_HEIGHT = 253;

    protected CompartmentOptionalSlotLabelMixin(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void dataEnergistics$drawCompartmentOptionalSlotLabels(GuiGraphics guiGraphics,
                                                                   float f,
                                                                   int x,
                                                                   int y,
                                                                   CallbackInfo ci) {
        for (Slot slot : this.menu.slots) {
            if (slot instanceof CompartmentSlotLabel labeledSlot && slot instanceof IOptionalSlot optionalSlot) {
                dataEnergistics$drawCompartmentOptionalSlotTexture(
                        guiGraphics,
                        slot,
                        optionalSlot,
                        labeledSlot.slotTextureRow());
            }
        }
    }

    @Unique
    private void dataEnergistics$drawCompartmentOptionalSlotTexture(GuiGraphics guiGraphics,
                                                                    Slot slot,
                                                                    IOptionalSlot optionalSlot,
                                                                    int textureRow) {
        float alpha = optionalSlot.isSlotEnabled() ? 1.0f : 0.2f;
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        guiGraphics.blit(
                COMPOSITE_WAREHOUSE_TEXTURE,
                this.leftPos + slot.x - 1,
                this.topPos + slot.y - 1,
                0,
                SLOT_SOURCE_LEFT,
                SLOT_SOURCE_TOP + textureRow * SLOT_SOURCE_STEP,
                SLOT_TEXTURE_SIZE,
                SLOT_TEXTURE_SIZE,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
