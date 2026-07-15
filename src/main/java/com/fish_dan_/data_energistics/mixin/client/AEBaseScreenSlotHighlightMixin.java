package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.slot.ResizableSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenSlotHighlightMixin {

    @Unique
    private static final String DATA_ENERGISTICS_SCREEN_PACKAGE = "com.fish_dan_.data_energistics.client.screen";
    @Unique
    private static final int DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER = 0xFF57E0BB;
    @Unique
    private static final int DATA_ENERGISTICS_SLOT_HIGHLIGHT_FILL = 0x6693FFDE;

    @Inject(method = "renderSlotHighlight", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY,
                                                     float partialTick, CallbackInfo ci) {
        if (this instanceof Ae2NativeSlotHighlight) {
            return;
        }

        String screenPackage = this.getClass().getPackageName();
        if (!screenPackage.equals(DATA_ENERGISTICS_SCREEN_PACKAGE) && !screenPackage.startsWith(DATA_ENERGISTICS_SCREEN_PACKAGE + ".")) {
            return;
        }

        ci.cancel();
        if (!slot.isHighlightable()) {
            return;
        }

        int width = 16;
        int height = 16;
        if (slot instanceof ResizableSlot resizableSlot) {
            width = resizableSlot.getWidth();
            height = resizableSlot.getHeight();
        }

        int x = slot.x;
        int y = slot.y;
        guiGraphics.hLine(x, x + width, y - 1, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.hLine(x - 1, x + width, y + height, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.vLine(x - 1, y - 2, y + height, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.vLine(x + width, y - 2, y + height, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.fillGradient(RenderType.guiOverlay(), x, y, x + width, y + height,
                DATA_ENERGISTICS_SLOT_HIGHLIGHT_FILL, DATA_ENERGISTICS_SLOT_HIGHLIGHT_FILL, 0);
    }
}
