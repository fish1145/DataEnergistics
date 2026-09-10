package com.fish_dan_.data_energistics.client.render.entity.cannon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/** Flight-only visuals; projectile collision, damage and detonation remain server-owned. */
public final class ElementalGrenadeRenderer {

    private static final ResourceLocation WIND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/wind_charge.png");
    private static final ResourceLocation FIRE_TEXTURE = ResourceLocation.withDefaultNamespace("block/fire_0");
    private static final int LATITUDES = 12;
    private static final int LONGITUDES = 20;
    private final ModelPart windRoot;
    private final ModelPart wind;
    private final ModelPart windCharge;

    public ElementalGrenadeRenderer(EntityRendererProvider.Context context) {
        this.windRoot = context.bakeLayer(ModelLayers.WIND_CHARGE).getChild("bone");
        this.wind = this.windRoot.getChild("wind");
        this.windCharge = this.windRoot.getChild("wind_charge");
    }

    /** Matches vanilla WindChargeModel rotation and WindChargeRenderer texture scrolling. */
    public void wind(float age, PoseStack pose, MultiBufferSource buffers, int light) {
        this.windCharge.yRot = -age * 16 * Mth.DEG_TO_RAD;
        this.wind.yRot = age * 16 * Mth.DEG_TO_RAD;
        var vertices = buffers.getBuffer(RenderType.breezeWind(WIND_TEXTURE, age * 0.03F % 1, 0));
        this.windRoot.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
    }

    public void fire(float age, PoseStack pose, MultiBufferSource buffers) {
        // Resolve the current atlas sprite on each render so resource reloads cannot retain stale UVs.
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(FIRE_TEXTURE);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 9));
        sphere(pose.last(), vertices, sprite, 0.18F, 255);
        pose.mulPose(Axis.XP.rotationDegrees(age * 5));
        sphere(pose.last(), vertices, sprite, 0.25F + 0.012F * Mth.sin(age * 0.7F), 210);
        pose.popPose();
    }

    private static void sphere(PoseStack.Pose pose, VertexConsumer vertices, TextureAtlasSprite sprite,
                               float radius, int alpha) {
        for (int latitude = 0; latitude < LATITUDES; latitude++) {
            float v0 = latitude / (float) LATITUDES;
            float v1 = (latitude + 1) / (float) LATITUDES;
            for (int longitude = 0; longitude < LONGITUDES; longitude++) {
                float u0 = longitude / (float) LONGITUDES;
                float u1 = (longitude + 1) / (float) LONGITUDES;
                vertex(pose, vertices, sprite, radius, alpha, u0, v0);
                vertex(pose, vertices, sprite, radius, alpha, u1, v0);
                vertex(pose, vertices, sprite, radius, alpha, u1, v1);
                vertex(pose, vertices, sprite, radius, alpha, u0, v1);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, TextureAtlasSprite sprite,
                               float radius, int alpha, float u, float v) {
        float ring = Mth.sin(v * Mth.PI);
        float x = ring * Mth.cos(u * Mth.TWO_PI);
        float y = Mth.cos(v * Mth.PI);
        float z = ring * Mth.sin(u * Mth.TWO_PI);
        vertices.addVertex(pose, radius * x, radius * y, radius * z)
                .setColor(255, 255, 255, alpha)
                .setUv(sprite.getU(u), sprite.getV(v))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, x, y, z);
    }
}
