package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.entity.MatterConvergingBoltEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

public class MatterConvergingBoltRenderer extends EntityRenderer<MatterConvergingBoltEntity> {

    private static final ResourceLocation NORMAL_ARROW_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    private static final ResourceLocation TIPPED_ARROW_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/tipped_arrow.png");

    public MatterConvergingBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MatterConvergingBoltEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));

        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.scale(0.05625F, 0.05625F, 0.05625F);
        poseStack.translate(-4.0F, 0.0F, 0.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(this.getTextureLocation(entity)));
        PoseStack.Pose pose = poseStack.last();
        int color = entity.getColor();
        this.vertex(pose, consumer, -7, -2, -2, 0.0F, 0.15625F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, -2, 2, 0.15625F, 0.15625F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, 2, 0.15625F, 0.3125F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, -2, 0.0F, 0.3125F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, -2, 0.0F, 0.15625F, 1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, 2, 0.15625F, 0.15625F, 1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, -2, 2, 0.15625F, 0.3125F, 1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, -2, -2, 0.0F, 0.3125F, 1, 0, 0, packedLight, color);

        for (int i = 0; i < 4; ++i) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            this.vertex(pose, consumer, -8, -2, 0, 0.0F, 0.0F, 0, 1, 0, packedLight, color);
            this.vertex(pose, consumer, 8, -2, 0, 0.5F, 0.0F, 0, 1, 0, packedLight, color);
            this.vertex(pose, consumer, 8, 2, 0, 0.5F, 0.15625F, 0, 1, 0, packedLight, color);
            this.vertex(pose, consumer, -8, 2, 0, 0.0F, 0.15625F, 0, 1, 0, packedLight, color);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void vertex(PoseStack.Pose pose, VertexConsumer consumer, int x, int y, int z, float u, float v,
                        int normalX, int normalY, int normalZ, int packedLight, int color) {
        consumer.addVertex(pose, x, y, z)
                .setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalZ, normalY);
    }

    @Override
    public ResourceLocation getTextureLocation(MatterConvergingBoltEntity entity) {
        return entity.getColor() >= 0 ? TIPPED_ARROW_LOCATION : NORMAL_ARROW_LOCATION;
    }
}
