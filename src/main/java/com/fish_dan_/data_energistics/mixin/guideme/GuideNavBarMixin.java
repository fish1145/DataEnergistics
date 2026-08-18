package com.fish_dan_.data_energistics.mixin.guideme;

import com.fish_dan_.data_energistics.integration.guideme.client.GuideNavBarHierarchySupport;

import guideme.internal.screen.GuideNavBar;
import guideme.navigation.NavigationTree;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GuideNavBar.class, remap = false)
public abstract class GuideNavBarMixin {

    @Shadow
    private NavigationTree navTree;

    @Shadow
    @Final
    private List<Object> rows;

    @Inject(
            method = "recreateRows",
            at = @At(
                     value = "INVOKE",
                     target = "Lguideme/internal/screen/GuideNavBar;updateLayout()V"))
    private void dataEnergistics$createNestedRows(CallbackInfo ci) {
        this.rows.clear();
        GuideNavBarHierarchySupport.populateRows(
                (GuideNavBar) (Object) this,
                this.navTree.getRootNodes(),
                this.rows);
    }

    @Inject(method = "updateLayout", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$layoutNestedRows(CallbackInfo ci) {
        GuideNavBarHierarchySupport.layoutRows(this.rows);
        ci.cancel();
    }
}
