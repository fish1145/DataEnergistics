package com.fish_dan_.data_energistics.mixin.core.entity;

import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureAttachments;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureContext;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;

/** Owns only terminal-life health writes and its drop routine; normal healing and death hooks are unchanged. */
@Mixin(LivingEntity.class)
public abstract class OrbitalLivingRecoveryMixin extends Entity {

    @Shadow
    @Final
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;
    @Shadow
    protected int lastHurtByPlayerTime;

    protected OrbitalLivingRecoveryMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract float getMaxHealth();

    @Shadow
    protected abstract boolean shouldDropLoot();

    @Shadow
    protected abstract void dropFromLootTable(DamageSource source, boolean recentlyHit);

    @Shadow
    protected abstract void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit);

    @Shadow
    protected abstract void dropEquipment();

    @Shadow
    protected abstract void dropExperience(@Nullable Entity attacker);

    @WrapMethod(method = "setHealth")
    private void dataEnergistics$routeTerminalHealth(float health, Operation<Void> original) {
        if (!OrbitalErasureAttachments.blocksRecovery(this)) {
            original.call(health);
        } else if (health <= 0) {
            // Bypass cancellable HEAD injections for the owning termination, without entering the damage pipeline.
            this.entityData.set(DATA_HEALTH_ID, Mth.clamp(health, 0, getMaxHealth()));
        }
    }

    @WrapMethod(method = "heal")
    private void dataEnergistics$blockOldLifeHealing(float amount, Operation<Void> original) {
        if (!OrbitalErasureAttachments.blocksRecovery(this)) {
            original.call(amount);
        }
    }

    @WrapMethod(method = "dropAllDeathLoot")
    private void dataEnergistics$routeErasureDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player) || OrbitalErasureContext.current(player, source) == null) {
            original.call(level, source);
            return;
        }
        // Preserve the NeoForge 1.21.1 drop pipeline, including its final cancellable drops event and inventory rules.
        this.captureDrops(new ObjectArrayList<>());
        boolean recentlyHit = this.lastHurtByPlayerTime > 0;
        if (this.shouldDropLoot() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(source, recentlyHit);
            this.dropCustomDeathLoot(level, source, recentlyHit);
        }
        this.dropEquipment();
        this.dropExperience(source.getEntity());
        Collection<ItemEntity> drops = this.captureDrops(null);
        if (!CommonHooks.onLivingDrops(player, source, drops, recentlyHit)) {
            drops.forEach(level::addFreshEntity);
        }
    }
}
