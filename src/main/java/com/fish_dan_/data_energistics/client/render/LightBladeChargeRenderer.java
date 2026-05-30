package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.entity.LightBladeChargeEntity;

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

public class LightBladeChargeRenderer extends EntityRenderer<LightBladeChargeEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("data_energistics", "textures/entity/blade_charge.png");
    private static final float HALF_LENGTH = 0.8F;
    private static final float HALF_WIDTH = 0.25F;
    private static final float CROSS_ANGLE = 45.0F;

    public LightBladeChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LightBladeChargeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.scale(2.0F, 2.0F, 2.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
        int color = entity.getColor();
        renderBladePlane(poseStack, consumer, packedLight, color, CROSS_ANGLE);
        renderBladePlane(poseStack, consumer, packedLight, color, -CROSS_ANGLE);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void renderBladePlane(PoseStack poseStack, VertexConsumer consumer, int packedLight, int color,
                                         float angleDegrees) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(angleDegrees));
        PoseStack.Pose pose = poseStack.last();

        // front
        vertex(consumer, pose, -HALF_LENGTH, -HALF_WIDTH, 0.0F, 0.0F, 1.0F, packedLight, color);
        vertex(consumer, pose, HALF_LENGTH, -HALF_WIDTH, 0.0F, 1.0F, 1.0F, packedLight, color);
        vertex(consumer, pose, HALF_LENGTH, HALF_WIDTH, 0.0F, 1.0F, 0.0F, packedLight, color);
        vertex(consumer, pose, -HALF_LENGTH, HALF_WIDTH, 0.0F, 0.0F, 0.0F, packedLight, color);

        // back
        vertex(consumer, pose, -HALF_LENGTH, HALF_WIDTH, 0.0F, 0.0F, 0.0F, packedLight, color);
        vertex(consumer, pose, HALF_LENGTH, HALF_WIDTH, 0.0F, 1.0F, 0.0F, packedLight, color);
        vertex(consumer, pose, HALF_LENGTH, -HALF_WIDTH, 0.0F, 1.0F, 1.0F, packedLight, color);
        vertex(consumer, pose, -HALF_LENGTH, -HALF_WIDTH, 0.0F, 0.0F, 1.0F, packedLight, color);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u,
                               float v, int packedLight, int color) {
        consumer.addVertex(pose, x, y, z)
                .setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(LightBladeChargeEntity entity) {
        return TEXTURE;
    }
}
