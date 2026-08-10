package com.fish_dan_.data_energistics.effect;

import com.fish_dan_.data_energistics.registry.DEMobEffects;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

public final class RadixLossEffectLogic {

    private static final int MAX_AMPLIFIER = 9;
    private static final float WEAPON_DAMAGE_BONUS_PER_LEVEL = 0.05F;
    private static final float MISSING_HEALTH_DAMAGE_RATIO = 0.25F;

    private RadixLossEffectLogic() {}

    public static void applyOrBurst(LivingEntity target, int durationTicks, @Nullable Entity attacker, float weaponDamage) {
        MobEffectInstance existingEffect = target.getEffect(DEMobEffects.RADIX_LOSS);
        if (existingEffect == null) {
            target.addEffect(new MobEffectInstance(DEMobEffects.RADIX_LOSS, durationTicks, 0, false, true, true));
            return;
        }

        dealWeaponBonusDamage(target, attacker, weaponDamage, existingEffect.getAmplifier() + 1);
        if (!target.isAlive()) {
            return;
        }

        int nextAmplifier = existingEffect.getAmplifier() + 1;
        if (nextAmplifier >= MAX_AMPLIFIER) {
            target.removeEffect(DEMobEffects.RADIX_LOSS);
            dealBurstDamage(target, attacker);
            return;
        }

        int duration = Math.max(existingEffect.getDuration(), durationTicks);
        target.addEffect(new MobEffectInstance(DEMobEffects.RADIX_LOSS, duration, nextAmplifier, false, true, true));
    }

    private static void dealWeaponBonusDamage(LivingEntity target, @Nullable Entity attacker, float weaponDamage, int level) {
        float damage = Math.max(0.0F, weaponDamage) * WEAPON_DAMAGE_BONUS_PER_LEVEL * level;
        if (damage <= 0.0F) {
            return;
        }

        target.hurt(createDamageSource(target, attacker), damage);
    }

    private static void dealBurstDamage(LivingEntity target, @Nullable Entity attacker) {
        float missingHealth = Math.max(0.0F, target.getMaxHealth() - target.getHealth());
        float damage = missingHealth * MISSING_HEALTH_DAMAGE_RATIO;
        if (damage <= 0.0F) {
            return;
        }

        target.hurt(createDamageSource(target, attacker), damage);
    }

    private static DamageSource createDamageSource(LivingEntity target, @Nullable Entity attacker) {
        return attacker instanceof Player player ? target.damageSources().playerAttack(player) : attacker instanceof LivingEntity livingAttacker ? target.damageSources().mobAttack(livingAttacker) : target.damageSources().magic();
    }
}
