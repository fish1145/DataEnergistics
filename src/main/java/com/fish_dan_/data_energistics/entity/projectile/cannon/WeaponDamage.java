package com.fish_dan_.data_energistics.entity.projectile.cannon;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/** Weapon-local rapid hit boundary; ordinary damage events, armor and immunities still apply. */
public final class WeaponDamage {

    private WeaponDamage() {}

    public static DamageSource source(LivingEntity target, @Nullable Entity owner) {
        return owner instanceof Player player ? target.damageSources().playerAttack(player) : owner instanceof LivingEntity living ? target.damageSources().mobAttack(living) : target.damageSources().magic();
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !target.isAlive()) return false;
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }
}
