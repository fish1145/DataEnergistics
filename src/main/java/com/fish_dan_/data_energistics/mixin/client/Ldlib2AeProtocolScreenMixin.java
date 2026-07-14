package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.screen.Ldlib2AeProtocolScreen;

import net.minecraft.client.gui.GuiGraphics;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.slot.IOptionalSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses AE2's final optional-slot visual pass for screens whose complete surface is owned by LDLib2. */
@Mixin(AEBaseScreen.class)
public abstract class Ldlib2AeProtocolScreenMixin {

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
