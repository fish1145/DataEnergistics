package com.fish_dan_.data_energistics.mixin.core.world;

import com.fish_dan_.data_energistics.common.resonance.WardenSonicBoomResonance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Settles resonance effects before vanilla can reject a direct Warden sonic-boom hit.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySonicBoomMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void dataEnergistics$processWardenSonicBoom(DamageSource source, float amount,
                                                        CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target.level() instanceof ServerLevel level &&
                source.is(DamageTypes.SONIC_BOOM) &&
                source.getDirectEntity() instanceof Warden warden) {
            WardenSonicBoomResonance.process(level, warden, target);
        }
    }
}
