package com.fish_dan_.data_energistics.mixin.core.world;

import com.fish_dan_.data_energistics.common.resonance.WardenSonicBoomResonance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.monster.warden.Warden;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Settles resonance effects at the emitted sonic boom's damage call and suppresses a successfully intercepted hit.
 */
@Mixin(SonicBoom.class)
public abstract class SonicBoomMixin {

    @WrapOperation(
                   method = "lambda$tick$2(Lnet/minecraft/world/entity/monster/warden/Warden;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
                   at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean dataEnergistics$processResonanceBeforeDamage(
                                                                        LivingEntity target,
                                                                        DamageSource source,
                                                                        float amount,
                                                                        Operation<Boolean> original) {
        if (target.level() instanceof ServerLevel level &&
                source.is(DamageTypes.SONIC_BOOM) &&
                source.getDirectEntity() instanceof Warden warden &&
                WardenSonicBoomResonance.process(level, warden, target)) {
            return false;
        }
        return original.call(target, source, amount);
    }
}
