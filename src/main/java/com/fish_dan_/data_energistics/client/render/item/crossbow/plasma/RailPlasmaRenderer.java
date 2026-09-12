package com.fish_dan_.data_energistics.client.render.item.crossbow.plasma;

import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

/** Small volumetric plasma with a bright core, additive shells and intermittent surface lightning. */
public final class RailPlasmaRenderer {

    private static final int LATITUDES = 6;
    private static final int LONGITUDES = 12;
    private static final float[] SHELL = sphere();
    private static final RenderType PLASMA = RenderType.create("data_energistics_rail_plasma",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));

    private RailPlasmaRenderer() {}

    public static void draw(RailAmmunition ammo, double time, PoseStack matrix, MultiBufferSource buffers) {
        PlasmaPalette color = PlasmaTextureColors.palette(ammo);
        float phase = (float) time + (ammo == RailAmmunition.FE ? 7.3F : 0);
        float pulse = 1 + (float) Math.sin(phase * 0.35) * 0.045F;
        matrix.pushPose();
        matrix.mulPose(Axis.YP.rotation(phase * 0.032F));
        matrix.mulPose(Axis.ZP.rotation((float) Math.sin(phase * 0.07) * 0.22F));
        VertexConsumer vertices = buffers.getBuffer(PLASMA);
        shell(vertices, matrix.last(), color, 0.125F * pulse, 0, 0.10F);
        shell(vertices, matrix.last(), color, 0.104F * pulse, 0.08F, 0.26F);
        shell(vertices, matrix.last(), color, 0.070F * pulse, 0.80F, 0.82F);
        int flash = (int) (phase / 2);
        for (int arc = 0; arc < 5; arc++) {
            float angle = arc * 1.2566F + flash * 0.31F;
            float tilt = arc * 0.91F + flash * 0.13F;
            float length = 0.9F + (float) Math.sin(flash + arc) * 0.25F;
            float x = 0, y = 0, z = 0;
            for (int step = 0; step <= 6; step++) {
                float a = angle + length * step / 6;
                float radius = 0.143F + (float) Math.sin(step * 5.7 + flash * 2.1 + arc) * 0.012F;
                float nextX = (float) Math.cos(a) * radius;
                float nextY = (float) Math.sin(a) * (float) Math.cos(tilt) * radius;
                float nextZ = (float) Math.sin(a) * (float) Math.sin(tilt) * radius;
                if (step > 0) {
                    bolt(vertices, matrix.last(), color, x, y, z, nextX, nextY, nextZ, 0.007F, 0.12F, 0.35F);
                    bolt(vertices, matrix.last(), color, x, y, z, nextX, nextY, nextZ, 0.0025F, 0.72F, 0.95F);
                }
                x = nextX;
                y = nextY;
                z = nextZ;
            }
        }
        matrix.popPose();
    }

    private static void shell(VertexConsumer out, PoseStack.Pose pose, PlasmaPalette color, float size, float white, float alpha) {
        for (int i = 0; i < SHELL.length; i += 3) vertex(out, pose, color, SHELL[i] * size, SHELL[i + 1] * size, SHELL[i + 2] * size, white, alpha);
    }

    private static float[] sphere() {
        float[] vertices = new float[LATITUDES * LONGITUDES * 12];
        int offset = 0;
        for (int ring = 0; ring < LATITUDES; ring++) for (int segment = 0; segment < LONGITUDES; segment++) {
            for (int corner = 0; corner < 4; corner++) {
                double latitude = Math.PI * ((corner >= 2 ? ring + 1 : ring) / (double) LATITUDES);
                double longitude = Math.PI * 2 * ((corner == 1 || corner == 2 ? segment + 1 : segment) / (double) LONGITUDES);
                double lobe = 1 + 0.035 * Math.sin(longitude * 3 + latitude * 2);
                vertices[offset++] = (float) (Math.sin(latitude) * Math.cos(longitude) * lobe);
                vertices[offset++] = (float) (Math.cos(latitude) * lobe);
                vertices[offset++] = (float) (Math.sin(latitude) * Math.sin(longitude) * lobe);
            }
        }
        return vertices;
    }

    private static void bolt(VertexConsumer out, PoseStack.Pose pose, PlasmaPalette color,
                             float x, float y, float z, float ex, float ey, float ez, float width, float white, float alpha) {
        float dx = ex - x, dy = ey - y, dz = ez - z;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float sx = horizontal > 0.00001F ? dz / horizontal * width : width;
        float sz = horizontal > 0.00001F ? -dx / horizontal * width : 0;
        ribbon(out, pose, color, x, y, z, ex, ey, ez, sx, 0, sz, white, alpha);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.00001F) ribbon(out, pose, color, x, y, z, ex, ey, ez,
                dy * sz / length, (dz * sx - dx * sz) / length, -dy * sx / length, white, alpha);
    }

    private static void ribbon(VertexConsumer out, PoseStack.Pose pose, PlasmaPalette color, float x, float y, float z,
                               float ex, float ey, float ez, float sx, float sy, float sz, float white, float alpha) {
        vertex(out, pose, color, x - sx, y - sy, z - sz, white, alpha);
        vertex(out, pose, color, ex - sx, ey - sy, ez - sz, white, alpha);
        vertex(out, pose, color, ex + sx, ey + sy, ez + sz, white, alpha);
        vertex(out, pose, color, x + sx, y + sy, z + sz, white, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, PlasmaPalette color,
                               float x, float y, float z, float white, float alpha) {
        out.addVertex(pose, x, y, z).setColor(color.red(white), color.green(white), color.blue(white), alpha);
    }
}
