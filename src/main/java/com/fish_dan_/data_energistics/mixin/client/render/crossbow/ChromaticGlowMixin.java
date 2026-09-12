package com.fish_dan_.data_energistics.mixin.client.render.crossbow;

import com.fish_dan_.data_energistics.effect.ChromaticGlow;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ChromaticGlowMixin {

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$outlineColor(CallbackInfoReturnable<Integer> callback) {
        int color = ChromaticGlow.color((Entity) (Object) this);
        if (color >= 0) callback.setReturnValue(color);
    }
}
