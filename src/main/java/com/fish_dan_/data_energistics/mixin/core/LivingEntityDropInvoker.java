package com.fish_dan_.data_energistics.mixin.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the protected vanilla drop phase used by simulated biology output.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDropInvoker {

    /**
     * Marks the simulated entity as dead so drop listeners observe vanilla death-phase state.
     *
     * @param dead true while producing simulated death drops
     */
    @Accessor("dead")
    void dataEnergistics$setDead(boolean dead);

    /**
     * Invokes vanilla loot-table, custom, equipment, experience and living-drops event handling.
     *
     * @param level        server level used by vanilla drop handling
     * @param damageSource simulated player-caused damage source
     */
    @Invoker("dropAllDeathLoot")
    void dataEnergistics$invokeDropAllDeathLoot(ServerLevel level, DamageSource damageSource);
}
