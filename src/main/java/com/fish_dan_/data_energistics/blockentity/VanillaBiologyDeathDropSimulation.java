package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.mixin.core.accessor.LivingEntityDropInvoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Produces biology-carrier drops through the vanilla living-entity drop phase while excluding the real-world death
 * lifecycle.
 *
 * <p>
 * A data mimetic field needs vanilla loot tables, equipment and custom drops, plus the standard living-drops event. It
 * does not represent an entity dying in the world, so the complete death lifecycle is deliberately not invoked.
 * </p>
 */
public final class VanillaBiologyDeathDropSimulation {

    /**
     * Prepares player-kill loot context and invokes only {@code dropAllDeathLoot}.
     *
     * @param level      server level used to resolve loot and fire drop events
     * @param entity     initialized simulated entity
     * @param fakePlayer player context used by loot conditions
     */
    public void generateDrops(ServerLevel level, LivingEntity entity, Player fakePlayer) {
        DamageSource damageSource = level.damageSources().playerAttack(fakePlayer);
        entity.tickCount = 100;
        entity.setLastHurtByPlayer(fakePlayer);
        entity.skipDropExperience();
        LivingEntityDropInvoker dropInvoker = (LivingEntityDropInvoker) entity;
        dropInvoker.dataEnergistics$setDead(true);
        dropInvoker.dataEnergistics$invokeDropAllDeathLoot(level, damageSource);
    }
}
