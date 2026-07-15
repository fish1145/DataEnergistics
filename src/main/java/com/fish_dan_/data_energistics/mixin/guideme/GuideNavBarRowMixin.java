package com.fish_dan_.data_energistics.mixin.guideme;

import com.fish_dan_.data_energistics.client.guideme.GuideNavBarHierarchySupport;

import guideme.document.LytRect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "guideme.internal.screen.GuideNavBar$Row", remap = false)
public abstract class GuideNavBarRowMixin {

    @Inject(method = "isVisible()Z", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$checkAllParents(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(GuideNavBarHierarchySupport.isVisible(this));
    }

    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$indentNestedBounds(CallbackInfoReturnable<LytRect> cir) {
        cir.setReturnValue(GuideNavBarHierarchySupport.indentBounds(this, cir.getReturnValue()));
    }
}
