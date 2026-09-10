package com.fish_dan_.data_energistics.item.powered.cannon.rail;

import com.fish_dan_.data_energistics.effect.RadixLossEffectLogic;
import com.fish_dan_.data_energistics.effect.WeaponBurn;
import com.fish_dan_.data_energistics.entity.projectile.cannon.ElementalGrenade;
import com.fish_dan_.data_energistics.entity.projectile.cannon.WeaponDamage;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Server ray contacts shared by damage and beam presentation. A wall always bounds the first hit. */
public final class RailBeam {

    private RailBeam() {}

    public record Contact(Vec3 end, @Nullable LivingEntity target, List<LivingEntity> chained) {}

    public static Contact trace(ServerLevel level, Player owner, RailAmmunition ammo, int cards) {
        Vec3 start = owner.getEyePosition();
        Vec3 end = start.add(owner.getViewVector(1).scale(256));
        end = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level, owner, start, end, new AABB(start, end).inflate(0.3),
                entity -> entity != owner && entity.isAlive() && entity.isPickable() && !entity.isSpectator());
        LivingEntity target = null;
        if (hit != null) {
            end = hit.getLocation();
            if (hit.getEntity() instanceof LivingEntity living) target = living;
            else if (hit.getEntity() instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity living) target = living;
        }
        List<LivingEntity> chained = ammo == RailAmmunition.FE ? level.getEntitiesOfClass(LivingEntity.class, ElementalGrenade.area(end, cards == 0 ? 4 : 7),
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator()) : List.of();
        return new Contact(end, target, chained);
    }

    public static void hit(Player owner, RailSession session, Contact contact, int continuousTicks) {
        RailAmmunition ammo = session.ammunition();
        int cards = session.cards();
        if (ammo == RailAmmunition.FE) {
            int period = ammo.intervalHalfTicks(cards);
            if (session.elapsed() * 2 / period == (session.elapsed() - 1) * 2 / period) return;
            for (LivingEntity target : contact.chained()) WeaponDamage.hurt(target, WeaponDamage.source(target, owner), ammo.damage(cards));
            return;
        }
        LivingEntity target = contact.target();
        if (target == null) return;
        float missing = Math.max(0, target.getMaxHealth() - target.getHealth());
        boolean damaged = WeaponDamage.hurt(target, WeaponDamage.source(target, owner),
                ammo.damage(cards) + (ammo == RailAmmunition.HEAVY && cards == 2 ? missing * 0.25F : 0));
        if (!damaged) return;
        switch (ammo) {
            case BLAZE -> WeaponBurn.apply(target, owner, cards == 0 ? 100 : 200, cards == 2 ? 3 : 1.5F);
            case HEAVY -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, cards == 2 ? 9 : 3));
            case DATA -> {
                if (continuousTicks % (cards == 0 ? 60 : 20) == 0) {
                    target.invulnerableTime = 0;
                    RadixLossEffectLogic.applyOrBurst(target, 30, owner, ammo.damage(cards), cards == 2);
                }
            }
            case FE -> throw new IllegalStateException("FE contacts were already handled");
        }
    }
}
