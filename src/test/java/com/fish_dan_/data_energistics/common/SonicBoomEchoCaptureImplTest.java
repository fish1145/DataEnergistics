package com.fish_dan_.data_energistics.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonicBoomEchoCaptureImplTest {

    @Test
    void capturesTenEchoOnlyForRollsBelowSeventyPercent() {
        assertTrue(SonicBoomEchoCaptureImpl.shouldCaptureEcho(0.0D));
        assertTrue(SonicBoomEchoCaptureImpl.shouldCaptureEcho(0.699999D));
        assertFalse(SonicBoomEchoCaptureImpl.shouldCaptureEcho(0.70D));
        assertFalse(SonicBoomEchoCaptureImpl.shouldCaptureEcho(0.75D));
    }

    @Test
    void extendsSevenBlocksPastTargetAndAcceptsOnlyFrontFaceIntersections() {
        Vec3 start = new Vec3(0.5D, 0.5D, -2.0D);
        Vec3 targetEye = new Vec3(0.5D, 0.5D, 2.0D);
        Vec3 end = SonicBoomEchoCaptureImpl.extendPastTarget(start, targetEye);

        assertEquals(7.0D, end.distanceTo(targetEye), 1.0E-9D);
        assertEquals(new Vec3(0.5D, 0.5D, 9.0D), end);
        assertTrue(SonicBoomEchoCaptureImpl.intersectsFrontFace(
                start,
                end,
                BlockPos.ZERO,
                Direction.NORTH));
        assertFalse(SonicBoomEchoCaptureImpl.intersectsFrontFace(
                end,
                start,
                BlockPos.ZERO,
                Direction.NORTH));
        assertFalse(SonicBoomEchoCaptureImpl.intersectsFrontFace(
                new Vec3(1.5D, 0.5D, -2.0D),
                new Vec3(1.5D, 0.5D, 9.0D),
                BlockPos.ZERO,
                Direction.NORTH));
    }
}
