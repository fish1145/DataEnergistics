package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.effect.WeaponBurn;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.AmmunitionRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/** Collision payloads for wind charges and fire charges; never replace terrain to spawn a projectile. */
public final class ElementalGrenade {

    private ElementalGrenade() {}

    public static boolean accepts(ItemStack ammo) {
        return ammo.is(Items.WIND_CHARGE) || ammo.is(Items.FIRE_CHARGE);
    }

    public static void detonate(ServerLevel level, ItemStack ammo, Vec3 center, @Nullable LivingEntity owner, int cards) {
        if (ammo.is(Items.WIND_CHARGE)) {
            var wind = AmmunitionRules.wind(cards);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area(center, wind.width()), LivingEntity::isAlive)) {
                WeaponDamage.hurt(target, WeaponDamage.source(target, owner), wind.damage());
                double gravity = target.getGravity();
                if (gravity > 0 && !target.isNoGravity()) {
                    Vec3 motion = target.getDeltaMovement();
                    target.setDeltaMovement(motion.x, AmmunitionRules.launchSpeed(wind.height(), gravity), motion.z);
                    target.hurtMarked = true;
                }
            }
            level.sendParticles(ParticleTypes.GUST, center.x, center.y, center.z, 12, wind.width() / 3.0, 0.3, wind.width() / 3.0, 0);
        } else {
            var flame = AmmunitionRules.flame(cards);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area(center, flame.width()), LivingEntity::isAlive)) {
                WeaponDamage.hurt(target, WeaponDamage.source(target, owner), flame.damage());
                WeaponBurn.apply(target, owner, flame.burnTicks(), flame.burnDamage());
            }
            BlockPos origin = BlockPos.containing(center);
            int radius = flame.width() / 2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = 2; y >= -2; y--) {
                        BlockPos pos = origin.offset(x, y, z);
                        if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                            level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                            break;
                        }
                    }
                }
            }
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 30, radius, 0.2, radius, 0.02);
        }
    }

    public static AABB area(Vec3 center, double width) {
        return new AABB(center, center).inflate(width / 2.0);
    }
}
