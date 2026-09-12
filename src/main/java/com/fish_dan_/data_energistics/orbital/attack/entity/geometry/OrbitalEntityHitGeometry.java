package com.fish_dan_.data_energistics.orbital.attack.entity.geometry;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Contact tests use the occupied body, including head/edge contact, instead of an entity's feet or center point. */
public final class OrbitalEntityHitGeometry {

    public static final double CONTACT_EPSILON = 1.0E-7;

    private OrbitalEntityHitGeometry() {}

    public static boolean intersectsSphere(AABB body, Vec3 center, double radius) {
        double x = outsideDistance(center.x, body.minX, body.maxX);
        double y = outsideDistance(center.y, body.minY, body.maxY);
        double z = outsideDistance(center.z, body.minZ, body.maxZ);
        double contactRadius = radius + CONTACT_EPSILON;
        return x * x + y * y + z * z <= contactRadius * contactRadius;
    }

    /** Matches the complete block cells of the kinetic disk column, including their outer half-block faces. */
    public static boolean intersectsVerticalColumn(AABB body, Vec3 center, int radius, double bottom, double top) {
        if (body.maxY < bottom - CONTACT_EPSILON || body.minY > top + CONTACT_EPSILON) {
            return false;
        }
        double x = nearestColumnCell(body.minX - center.x, body.maxX - center.x);
        double z = nearestColumnCell(body.minZ - center.z, body.maxZ - center.z);
        return x * x + z * z <= (long) radius * radius;
    }

    /** Reuses the captured crater profile; its highest intersected layer has the widest removed disk. */
    public static boolean intersectsCrater(AABB body, BlockPos target, OrbitalAttackGeometry.Kinetic geometry, int bottom) {
        return intersectsCrater(body, target, geometry, bottom, target.getY() - 1);
    }

    public static boolean intersectsCrater(AABB body, BlockPos target, OrbitalAttackGeometry.Kinetic geometry, int bottom, int top) {
        if (body.maxY < bottom - CONTACT_EPSILON || body.minY > top + 1 + CONTACT_EPSILON) {
            return false;
        }
        double x = nearestColumnCell(body.minX - target.getX() - 0.5, body.maxX - target.getX() - 0.5);
        double z = nearestColumnCell(body.minZ - target.getZ() - 0.5, body.maxZ - target.getZ() - 0.5);
        if (x * x + z * z > (long) geometry.craterRadius() * geometry.craterRadius()) {
            return false;
        }
        int y = (int) Math.min(top, Math.floor(body.maxY + CONTACT_EPSILON));
        return geometry.containsCraterPosition(target,
                new BlockPos(target.getX() + (int) x, y, target.getZ() + (int) z), top, bottom);
    }

    private static double outsideDistance(double point, double minimum, double maximum) {
        return Math.max(Math.max(minimum - point, point - maximum), 0);
    }

    private static double nearestColumnCell(double minimum, double maximum) {
        double first = Math.ceil(minimum - 0.5 - CONTACT_EPSILON);
        double last = Math.floor(maximum + 0.5 + CONTACT_EPSILON);
        return first > 0 ? first : Math.min(last, 0);
    }
}
