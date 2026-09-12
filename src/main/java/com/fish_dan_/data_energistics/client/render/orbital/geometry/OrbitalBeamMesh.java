package com.fish_dan_.data_energistics.client.render.orbital.geometry;

import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamVolume;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Textured beam volumes and horizontal target rings, batched independently of the construct's material passes. */
public final class OrbitalBeamMesh {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    public static final RenderType RENDER_TYPE = RenderType.beaconBeam(TEXTURE, true);

    private OrbitalBeamMesh() {}

    /** Draws an aimed beam from its actual muzzle to its server-completed frontier. */
    public static void beam(PoseStack poses, VertexConsumer consumer, Vec3 start, Vec3 end, float width,
                            double time, float red, float green, float blue, float alpha) {
        Vec3 delta = end.subtract(start);
        float length = (float) delta.length();
        if (length < 0.01F) {
            return;
        }
        Vec3 direction = delta.scale(1.0 / length);
        poses.pushPose();
        poses.translate(start.x, start.y, start.z);
        poses.mulPose(OrbitalBeamVolume.orientation(direction));
        beam(poses, consumer, 0, 0, 0, length, width, time, red, green, blue, alpha);
        poses.popPose();
    }

    public static void beam(PoseStack poses, VertexConsumer consumer, double x, double z, double startY,
                            double endY, float width, double time, float red, float green, float blue, float alpha) {
        float bottom = (float) Math.min(startY, endY);
        float top = (float) Math.max(startY, endY);
        if (top - bottom < 0.01F) {
            return;
        }
        poses.pushPose();
        poses.translate(x, 0, z);
        float scroll = (float) (time % 200) * -0.05F;
        shell(poses.last(), consumer, bottom, top, width, scroll, red, green, blue, alpha * 0.25F);
        shell(poses.last(), consumer, bottom, top, width * 0.36F, scroll, red, green, blue, alpha);
        poses.popPose();
    }

    private static void shell(PoseStack.Pose pose, VertexConsumer consumer, float bottom, float top,
                              float width, float scroll, float red, float green, float blue, float alpha) {
        for (int face = 0; face < 4; face++) {
            float angle = face * Mth.HALF_PI + Mth.PI * 0.25F;
            float next = angle + Mth.HALF_PI;
            float x0 = Mth.cos(angle) * width, z0 = Mth.sin(angle) * width;
            float x1 = Mth.cos(next) * width, z1 = Mth.sin(next) * width;
            vertex(pose, consumer, x0, bottom, z0, 0, scroll, red, green, blue, alpha);
            vertex(pose, consumer, x0, top, z0, 0, scroll + (top - bottom) * 0.25F, red, green, blue, alpha);
            vertex(pose, consumer, x1, top, z1, 1, scroll + (top - bottom) * 0.25F, red, green, blue, alpha);
            vertex(pose, consumer, x1, bottom, z1, 1, scroll, red, green, blue, alpha);
        }
    }

    public static void targetRing(PoseStack poses, VertexConsumer consumer, double x, double y, double z,
                                  float radius, float red, float green, float blue, float alpha) {
        poses.pushPose();
        poses.translate(x, y, z);
        float inner = Math.max(0, radius - Math.max(0.35F, radius * 0.025F));
        for (int segment = 0; segment < 48; segment++) {
            float angle = segment * Mth.TWO_PI / 48;
            float next = (segment + 1) * Mth.TWO_PI / 48;
            float c0 = Mth.cos(angle), s0 = Mth.sin(angle);
            float c1 = Mth.cos(next), s1 = Mth.sin(next);
            vertex(poses.last(), consumer, c0 * inner, 0, s0 * inner, 0, 0, red, green, blue, alpha);
            vertex(poses.last(), consumer, c1 * inner, 0, s1 * inner, 0, 1, red, green, blue, alpha);
            vertex(poses.last(), consumer, c1 * radius, 0, s1 * radius, 1, 1, red, green, blue, alpha);
            vertex(poses.last(), consumer, c0 * radius, 0, s0 * radius, 1, 0, red, green, blue, alpha);
        }
        poses.popPose();
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
                               float u, float v, float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
