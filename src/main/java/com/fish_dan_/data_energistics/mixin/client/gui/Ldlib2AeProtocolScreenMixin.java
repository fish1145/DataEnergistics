package com.fish_dan_.data_energistics.mixin.client.gui;

import com.fish_dan_.data_energistics.client.screen.Ldlib2AeProtocolScreen;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeItemSlot;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.slot.IOptionalSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses final AE2 visual passes and bridges carried-stack tooltips through LDLib2's renderer. */
@Mixin(AEBaseScreen.class)
public abstract class Ldlib2AeProtocolScreenMixin {

    @Inject(method = "renderTooltips", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$suppressLegacyTooltips(GuiGraphics guiGraphics,
                                                        int mouseX,
                                                        int mouseY,
                                                        CallbackInfo callbackInfo) {
        if ((Object) this instanceof Ldlib2AeProtocolScreen<?>) {
            callbackInfo.cancel();
            dataEnergistics$drawCarriedTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    /** Draws special carried-stack tooltips that LDLib2 intentionally skips in container screens. */
    @Unique
    private void dataEnergistics$drawCarriedTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AEBaseScreen<?> screen = (AEBaseScreen<?>) (Object) this;
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) {
            return;
        }
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null) {
            return;
        }
        IModularUIHolderMenu holder = (IModularUIHolderMenu) screen.getMenu();
        if (!(holder.getItemSlot(hoveredSlot) instanceof AeItemSlot itemSlot)) {
            return;
        }
        HoverTooltips tooltip = itemSlot.createTooltip(carried);
        if (tooltip == null || tooltip.tooltipTexts().isEmpty()) {
            return;
        }
        DrawerHelper.drawTooltip(
                guiGraphics,
                mouseX,
                mouseY,
                tooltip.tooltipTexts(),
                tooltip.tooltipStack(),
                tooltip.tooltipComponent(),
                tooltip.tooltipFont());
    }

    @Inject(method = "drawOptionalSlotBackground", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$suppressOptionalSlotBackground(GuiGraphics guiGraphics,
                                                                IOptionalSlot optionalSlot,
                                                                boolean alwaysDraw,
                                                                CallbackInfo callbackInfo) {
        if ((Object) this instanceof Ldlib2AeProtocolScreen<?>) {
            callbackInfo.cancel();
        }
    }
}
