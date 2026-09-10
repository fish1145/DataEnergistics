package com.fish_dan_.data_energistics.item.powered.cannon.bow;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** A center-first symmetric volley for the weapon's bow mode, independent of vanilla weapon inheritance. */
public final class BowShotPattern {

    private BowShotPattern() {}

    public static float angle(int index, int count, float spread) {
        if (count <= 0 || index < 0 || index >= count) throw new IllegalArgumentException("Invalid volley index");
        if (count == 1) return 0;
        float step = 2 * spread / (count - 1);
        float center = ((count - 1) & 1) * step / 2;
        return center + ((index & 1) == 0 ? 1 : -1) * ((index + 1) / 2) * step;
    }

    public static Vector3f direction(LivingEntity shooter, Projectile projectile, float angle, @Nullable LivingEntity target) {
        Vec3 aim = shooter.getViewVector(1);
        Vec3 up = shooter.getUpVector(1);
        if (target != null) {
            double x = target.getX() - shooter.getX(), z = target.getZ() - shooter.getZ();
            Vec3 offset = new Vec3(x, target.getY(1.0 / 3) - projectile.getY() + Math.sqrt(x * x + z * z) * 0.2, z);
            if (offset.lengthSqr() > 1.0E-7) {
                aim = offset.normalize();
                Vec3 side = aim.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1.0E-7) side = aim.cross(up);
                up = side.normalize().cross(aim).normalize();
            }
        }
        return aim.toVector3f().rotateAxis(angle * Mth.DEG_TO_RAD, (float) up.x, (float) up.y, (float) up.z);
    }
}
