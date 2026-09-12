package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import org.joml.Matrix4f;

/** Places the grip in camera space, keeping the authored -Z firing axis aligned when aimed. */
public final class CrossbowFirstPersonPose {

    private CrossbowFirstPersonPose() {}

    public static Matrix4f transform(boolean rightHand, float aim, float equipped, float swing,
                                     float pitchLag, float yawLag) {
        float side = rightHand ? 1.0F : -1.0F;
        float relaxed = 1.0F - aim;
        float swingArc = (float) Math.sin(Math.sqrt(swing) * Math.PI) * relaxed;
        // Undo the inherited hand-follow rotations in reverse order when fully aimed.
        return new Matrix4f()
                .rotateY(radians(-yawLag * aim)).rotateX(radians(-pitchLag * aim))
                .translate(side * (0.50F * relaxed - 0.12F * swingArc),
                        -0.52F - 0.23F * relaxed - 0.60F * equipped * relaxed + 0.05F * swingArc,
                        -0.68F - 0.12F * relaxed - 0.06F * swingArc)
                .rotateXYZ(radians(8.0F * relaxed), radians(side * 8.0F * relaxed), radians(side * -6.0F * relaxed));
    }

    public static float aim(MatterConvergingCrossbowMode mode, float progress) {
        // Cannon charging drives lights, not the crossbow's draw-to-aim movement.
        if (mode != MatterConvergingCrossbowMode.CROSSBOW) {
            return 0.0F;
        }
        float value = Math.clamp(progress, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
