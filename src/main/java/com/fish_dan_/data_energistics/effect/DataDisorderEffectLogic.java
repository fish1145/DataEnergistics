package com.fish_dan_.data_energistics.effect;

import com.fish_dan_.data_energistics.registry.ModMobEffects;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

public final class DataDisorderEffectLogic {

    private static final int MAX_AMPLIFIER = 9;
    private static final float WEAPON_DAMAGE_BONUS_PER_LEVEL = 0.05F;
    private static final float MISSING_HEALTH_DAMAGE_RATIO = 0.25F;

    private DataDisorderEffectLogic() {}

    public static void applyOrBurst(LivingEntity target, int durationTicks, @Nullable Entity attacker, float weaponDamage) {
        MobEffectInstance existingEffect = target.getEffect(ModMobEffects.DATA_DISORDER);
        if (existingEffect == null) {
            target.addEffect(new MobEffectInstance(ModMobEffects.DATA_DISORDER, durationTicks, 0, false, true, true));
            return;
        }

        dealWeaponBonusDamage(target, attacker, weaponDamage, existingEffect.getAmplifier() + 1);
        if (!target.isAlive()) {
            return;
        }

        int nextAmplifier = existingEffect.getAmplifier() + 1;
        if (nextAmplifier >= MAX_AMPLIFIER) {
            target.removeEffect(ModMobEffects.DATA_DISORDER);
            dealBurstDamage(target, attacker);
            return;
        }

        int duration = Math.max(existingEffect.getDuration(), durationTicks);
        target.addEffect(new MobEffectInstance(ModMobEffects.DATA_DISORDER, duration, nextAmplifier, false, true, true));
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
