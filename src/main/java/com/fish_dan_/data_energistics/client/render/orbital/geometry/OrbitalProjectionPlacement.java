package com.fish_dan_.data_energistics.client.render.orbital.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Render-only depth compression retains angular size while keeping distant constructs inside the camera far plane. */
public record OrbitalProjectionPlacement(Vec3 cameraOffset, float scale, AABB bounds) {

    public static final double MAX_DISTANCE = 16_384;

    /**
     * Computes placement without querying chunks. Bounds are local to the model origin and must include its moving
     * parts. The world-space result is suitable for Minecraft's frustum; cameraOffset is used for vertex transforms.
     */
    public static OrbitalProjectionPlacement create(Vec3 camera, Vec3 origin, AABB localBounds, double depthFar) {
        if (!Double.isFinite(depthFar) || depthFar <= 0) {
            throw new IllegalArgumentException("Orbital projection requires a finite positive far plane");
        }
        Vec3 offset = origin.subtract(camera);
        double radiusX = Math.max(Math.abs(localBounds.minX), Math.abs(localBounds.maxX));
        double radiusY = Math.max(Math.abs(localBounds.minY), Math.abs(localBounds.maxY));
        double radiusZ = Math.max(Math.abs(localBounds.minZ), Math.abs(localBounds.maxZ));
        double radius = Math.sqrt(radiusX * radiusX + radiusY * radiusY + radiusZ * radiusZ);
        float scale = (float) Math.min(1.0, depthFar * 0.8 / (offset.length() + radius));
        Vec3 cameraOffset = offset.scale(scale);
        Vec3 center = camera.add(cameraOffset);
        AABB bounds = new AABB(localBounds.minX * scale, localBounds.minY * scale, localBounds.minZ * scale,
                localBounds.maxX * scale, localBounds.maxY * scale, localBounds.maxZ * scale).move(center);
        return new OrbitalProjectionPlacement(cameraOffset, scale, bounds);
    }

    /** Chooses geometry complexity without changing the construct's world dimensions. */
    public static Detail detail(double distanceSquared, boolean allowFullDetail) {
        if (distanceSquared <= 1024.0 * 1024.0 && allowFullDetail) {
            return Detail.FULL;
        }
        return distanceSquared <= 4096.0 * 4096.0 ? Detail.REDUCED : Detail.DISTANT;
    }

    public enum Detail {
        FULL,
        REDUCED,
        DISTANT
    }
}
