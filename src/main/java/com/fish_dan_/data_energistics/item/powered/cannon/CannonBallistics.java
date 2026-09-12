package com.fish_dan_.data_energistics.item.powered.cannon;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.world.phys.Vec3;

/** Shared launch and discrete flight rules for the server projectile and its client preview. */
public final class CannonBallistics {

    public static final double GRENADE_GRAVITY = 0.045D;
    public static final int PREVIEW_TICKS = 120;

    private CannonBallistics() {}

    public static float charge(long elapsedTicks, int durationTicks) {
        return Math.clamp((float) elapsedTicks / durationTicks, 0.0F, 1.0F);
    }

    public static Vec3 launchVelocity(MatterConvergingCrossbowMode mode, float charge, Vec3 direction, float ammoSpeed) {
        double speed = mode == MatterConvergingCrossbowMode.GRENADE ? 0.65D + (ammoSpeed - 0.65D) * charge : Math.max(8.0D, ammoSpeed * 2.0D);
        return direction.normalize().scale(speed);
    }

    /** Vanilla projectiles first move by velocity, then apply medium drag and gravity. */
    public static Vec3 nextVelocity(Vec3 velocity, MatterConvergingCrossbowMode mode, boolean inWater, boolean saberAmmo) {
        double drag = inWater && !saberAmmo ? (double) 0.8F : (double) 0.99F;
        return velocity.scale(drag).add(0.0D, mode == MatterConvergingCrossbowMode.GRENADE ? -GRENADE_GRAVITY : 0.0D, 0.0D);
    }

    /** The client may suggest a rendered muzzle, but never an arbitrary launch position or firing direction. */
    public static boolean validAim(Vec3 eyeOffset, Vec3 direction, Vec3 serverLook) {
        return finite(eyeOffset) && finite(direction) && eyeOffset.lengthSqr() <= 6.25D && eyeOffset.dot(serverLook) >= -0.2D && direction.lengthSqr() >= 0.99D && direction.lengthSqr() <= 1.01D && direction.dot(serverLook) >= 0.7D;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
