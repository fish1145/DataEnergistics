package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.effect.RadixLossEffectLogic;
import com.fish_dan_.data_energistics.effect.WeaponBurn;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.network.action.RailChainPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** One collision, one damage/effect settlement. FE branches from that physical impact point. */
public final class RailImpact {

    private RailImpact() {}

    public static void apply(ServerLevel level, RailShot shot, Vec3 center, @Nullable LivingEntity target, @Nullable Entity owner) {
        var ammo = shot.ammunition();
        int cards = shot.cards();
        float damage = ammo.damage(cards) * shot.charge();
        if (ammo == RailAmmunition.FE) {
            List<Vec3> points = new ObjectArrayList<>();
            points.add(center);
            int width = cards == 0 ? 12 : 17;
            int maximumTargets = cards == 2 ? 16 : 8;
            for (LivingEntity chained : level.getEntitiesOfClass(LivingEntity.class, ElementalGrenade.area(center, width),
                    entity -> entity != owner && entity.isAlive() && !entity.isSpectator()).stream().limit(maximumTargets).toList()) {
                WeaponDamage.hurt(chained, WeaponDamage.source(chained, owner), damage);
                if (points.size() < 4096) points.add(chained.getBoundingBox().getCenter());
            }
            if (points.size() > 1) PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 128,
                    new RailChainPayload(points));
            return;
        }
        if (target == null || !target.isAlive()) return;
        float missing = Math.max(0, target.getMaxHealth() - target.getHealth());
        if (!WeaponDamage.hurt(target, WeaponDamage.source(target, owner),
                damage + (ammo == RailAmmunition.HEAVY && cards == 2 ? missing * 0.25F * shot.charge() : 0)))
            return;
        switch (ammo) {
            case BLAZE -> WeaponBurn.apply(target, owner, cards == 0 ? 100 : 200, cards == 2 ? 3 : 1.5F);
            case HEAVY -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, cards == 2 ? 9 : 3));
            case DATA -> {
                for (int layer = 0; layer < (cards == 0 ? 1 : 2) && target.isAlive(); layer++) {
                    target.invulnerableTime = 0;
                    RadixLossEffectLogic.applyOrBurst(target, cards == 0 ? 80 : 180, owner, damage, cards == 2, shot.charge());
                }
            }
            case FE -> throw new IllegalStateException("FE was already settled");
        }
    }
}
