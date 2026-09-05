package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Minimal placeholder renderer for the server-authoritative orbital payload. */
public final class OrbitalAnnihilatorRenderer extends EntityRenderer<OrbitalAnnihilatorProjectileEntity> {

    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public OrbitalAnnihilatorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(OrbitalAnnihilatorProjectileEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.18F, 0.8F, 0.18F);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(WHITE_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float progress = (entity.flightTicks() + partialTick) / OrbitalAnnihilatorProjectileEntity.FLIGHT_TICKS;
        int red = 80 + (int) (progress * 120.0F);
        int green = 180 + (int) (progress * 60.0F);
        vertex(consumer, pose, -1.0F, 0.0F, 0.0F, red, green, 255, packedLight);
        vertex(consumer, pose, 1.0F, 0.0F, 0.0F, red, green, 255, packedLight);
        vertex(consumer, pose, 1.0F, 1.0F, 0.0F, red, green, 255, packedLight);
        vertex(consumer, pose, -1.0F, 1.0F, 0.0F, red, green, 255, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitalAnnihilatorProjectileEntity entity) {
        return WHITE_TEXTURE;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               int red, int green, int blue, int packedLight) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 230)
                .setUv(x < 0.0F ? 0.0F : 1.0F, y)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
