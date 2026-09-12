package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.integration.ae2lt.orbital.CelestweaveErasureHooks;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional resource/protection gate for the verified LT 2.1.0-beta.5 handler. Vanilla mixins own death execution;
 * these independent event/tick entry points must also stop before charging equipment for a terminated old life.
 */
@Pseudo
@Mixin(targets = CelestweaveErasureHooks.TARGET_CLASS, remap = false)
public abstract class OrbitalCelestweaveProtectionMixin {

    @Inject(method = {
            CelestweaveErasureHooks.FORCED_DEATH,
            CelestweaveErasureHooks.DEATH_SIDE_EFFECT
    }, at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void dataEnergistics$skipOldLifeProtection(@Nullable ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        if (player != null && OrbitalErasureAttachments.blocksRecovery(player)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = CelestweaveErasureHooks.PROTECTED_TICK, at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void dataEnergistics$ignoreOldProtectionWindow(@Nullable LivingEntity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity != null && OrbitalErasureAttachments.blocksRecovery(entity)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = {
            CelestweaveErasureHooks.WINDOW,
            CelestweaveErasureHooks.TRIGGER
    }, at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void dataEnergistics$skipOldLifeResourcePayment(ServerPlayer player, long now, CallbackInfoReturnable<Boolean> callback) {
        if (OrbitalErasureAttachments.blocksRecovery(player)) {
            callback.setReturnValue(false);
        }
    }
}
