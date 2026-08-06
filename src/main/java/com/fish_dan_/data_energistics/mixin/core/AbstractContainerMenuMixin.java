package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceSession;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void dataEnergistics$clearPatternPreferenceSession(Player player, CallbackInfo ci) {
        if (this instanceof PatternEncodingPreferenceMenu) {
            PatternEncodingPreferenceSession.clearForMenu(this);
        }
    }
}
