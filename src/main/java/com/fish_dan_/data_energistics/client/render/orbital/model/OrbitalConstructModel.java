package com.fish_dan_.data_energistics.client.render.orbital.model;

import com.fish_dan_.data_energistics.client.render.orbital.animation.OrbitalAnimationClock;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalProjectionPlacement.Detail;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Shared module assembly for the persistent construct, the three firing echoes, and the physical payload. */
public final class OrbitalConstructModel {

    public static final AABB BOUNDS = new AABB(-266, -84, -64, 266, 64, 64);
    public static final AABB ECHO_BOUNDS = new AABB(-64, -84, -64, 64, 64, 64);

    private OrbitalConstructModel() {}

    /** Renders one material pass in construct-local coordinates; the pose stack is restored before return. */
    public static void render(PoseStack poses, OrbitalModelRenderer renderer, Detail detail,
                              double time, long seed, boolean redeploying) {
        float turn = OrbitalAnimationClock.angle(time, seed, 720);
        float slowTurn = OrbitalAnimationClock.angle(time, seed, 2400);
        float spread = redeploying ? 5 + 5 * Mth.sin(turn) : 0;
        int sections = detail == Detail.DISTANT ? 2 : 8;
        float sectionLength = 512.0F / sections;
        for (int section = 0; section < sections; section++) {
            if (redeploying && detail == Detail.FULL && (section + (long) (time / 6)) % 5 == 0) {
                continue;
            }
            double x = -256 + sectionLength * (section + 0.5);
            renderer.part(poses, OrbitalModelPart.RAIL, x, 0, -18 - spread, sectionLength, 12, 12);
            renderer.part(poses, OrbitalModelPart.RAIL, x, 0, 18 + spread, sectionLength, 12, 12);
        }
        renderer.part(poses, OrbitalModelPart.CORE, -32, 0, 0, 84, 52, 64);
        renderer.part(poses, OrbitalModelPart.CRADLE, -32, -49, 0, 44, 48, 44);
        payload(poses, renderer, -32, -50 + Mth.sin(turn) * 1.2, 0, 16, slowTurn);
        for (int ring = 0; ring < 4; ring++) {
            float radius = 38 + (ring == 1 || ring == 2 ? 5 : 0) + spread;
            float rotation = ring % 2 == 0 ? slowTurn : -slowTurn;
            ring(poses, renderer, detail, 72 + ring * 42, radius, 7, rotation);
        }
        if (detail == Detail.FULL) {
            for (int side = -1; side <= 1; side += 2) {
                renderer.part(poses, OrbitalModelPart.RAIL, -116, side * 20, side * 28, 80, 3, 14);
                renderer.part(poses, OrbitalModelPart.CORE, -242, 0, side * 18, 18, 18, 18);
            }
        }
    }

    /** Uses the same authored modules for each target-side array; only cosmetic motion is interpolated. */
    public static void echo(PoseStack poses, OrbitalModelRenderer renderer, Detail detail,
                            OrbitalAttackMode mode, double time, long seed, boolean charging) {
        float turn = OrbitalAnimationClock.angle(time, seed, 480);
        float charge = charging ? 0.5F + 0.5F * Mth.sin(turn * 2) : 1;
        switch (mode) {
            case KINETIC -> {
                poses.pushPose();
                poses.mulPose(Axis.ZP.rotationDegrees(90));
                renderer.part(poses, OrbitalModelPart.CORE, 20, 0, 0, 28, 30, 40);
                for (int section = 0; section < 3; section++) {
                    float x = -28 + section * 24;
                    float gap = 9 + (1 - charge) * 4;
                    renderer.part(poses, OrbitalModelPart.RAIL, x, 0, -gap, 24, 8, 8);
                    renderer.part(poses, OrbitalModelPart.RAIL, x, 0, gap, 24, 8, 8);
                }
                poses.popPose();
            }
            case DIRECTED_ENERGY -> {
                poses.pushPose();
                poses.mulPose(Axis.ZP.rotationDegrees(90));
                for (int index = 0; index < 3; index++) {
                    ring(poses, renderer, detail, -18 + index * 14, 22 + index * 9 + charge * 3,
                            5, index % 2 == 0 ? turn : -turn);
                }
                renderer.part(poses, OrbitalModelPart.CORE, 28, 0, 0, 16, 24, 24);
                poses.popPose();
            }
            case DIGITAL_ANNIHILATION -> {
                renderer.part(poses, OrbitalModelPart.CORE, 0, 22, 0, 40, 24, 40);
                renderer.part(poses, OrbitalModelPart.CRADLE, 0, -10, 0, 44, 48, 44);
                payload(poses, renderer, 0, -12, 0, 16, turn);
            }
        }
    }

    private static void ring(PoseStack poses, OrbitalModelRenderer renderer, Detail detail,
                             double x, float radius, float thickness, float rotation) {
        int segments = detail == Detail.FULL ? 16 : 8;
        float tangentSize = 2 * radius * (float) Math.tan(Math.PI / segments) * 0.93F;
        for (int index = 0; index < segments; index++) {
            poses.pushPose();
            poses.translate(x, 0, 0);
            poses.mulPose(Axis.XP.rotation(rotation + index * Mth.TWO_PI / segments));
            renderer.part(poses, OrbitalModelPart.RING_SEGMENT, 0, radius, 0, thickness, 8, tangentSize);
            poses.popPose();
        }
    }

    /** Draws the volume of the descending payload; unlike a billboard it remains visible from every direction. */
    public static void payload(PoseStack poses, OrbitalModelRenderer renderer, double x, double y, double z,
                               float size, float rotation) {
        poses.pushPose();
        poses.translate(x, y, z);
        poses.mulPose(Axis.YP.rotation(rotation));
        poses.mulPose(Axis.ZP.rotationDegrees(12));
        renderer.part(poses, OrbitalModelPart.PAYLOAD, 0, 0, 0, size, size, size);
        poses.popPose();
    }
}
