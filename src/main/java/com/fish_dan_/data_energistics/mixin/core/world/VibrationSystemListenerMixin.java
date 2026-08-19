package com.fish_dan_.data_energistics.mixin.core.world;

import com.fish_dan_.data_energistics.common.resonance.SculkVibrationResonance;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts only already-validated sculk-sensor vibrations immediately before vanilla schedules them.
 */
@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationSystemListenerMixin {

    @Shadow
    @Final
    private VibrationSystem system;

    @Inject(
            method = "handleGameEvent",
            at = @At(
                     value = "INVOKE",
                     target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Listener;scheduleVibration(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"),
            cancellable = true)
    private void dataEnergistics$interceptSculkVibration(ServerLevel level, Holder<GameEvent> gameEvent,
                                                         GameEvent.Context context, Vec3 pos,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (this.system instanceof SculkSensorBlockEntity sensor &&
                SculkVibrationResonance.intercept(level, gameEvent, pos, sensor.getBlockPos())) {
            cir.setReturnValue(false);
        }
    }
}
