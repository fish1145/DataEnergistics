package com.fish_dan_.data_energistics.mixin;

import appeng.menu.me.items.CraftingTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingTermMenu.class)
public abstract class CraftingTermMenuMixin {

    @Inject(method = "updateCurrentRecipeAndOutput", at = @At("TAIL"), remap = false)
    private void dataEnergistics$clearTerminalCraftResultWithoutEnergy(boolean forceUpdate, CallbackInfo ci) {}
}
