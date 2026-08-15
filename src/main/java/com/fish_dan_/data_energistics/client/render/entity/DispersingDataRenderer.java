package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.entity.resource.DispersingDataEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class DispersingDataRenderer extends EntityRenderer<DispersingDataEntity> {

    private static final float DISPLAY_HALF_SIZE = 0.125F;
    private static final ResourceLocation[] ORB_TEXTURES = new ResourceLocation[] {
            Data_Energistics.id("textures/entity/dispersing_data_0.png"),
            Data_Energistics.id("textures/entity/dispersing_data_1.png"),
            Data_Energistics.id("textures/entity/dispersing_data_2.png"),
            Data_Energistics.id("textures/entity/dispersing_data_3.png")
    };

    public DispersingDataRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.04F;
        this.shadowStrength = 0.4F;
    }

    @Override
    protected int getBlockLightLevel(DispersingDataEntity entity, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(entity, pos) + 7, 0, 15);
    }

    @Override
    public void render(DispersingDataEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float bob = Mth.sin((entity.tickCount + partialTick) * 0.15F) * 0.03F;
        float pulse = 1.0F + Mth.sin((entity.tickCount + partialTick) * 0.25F) * 0.05F;
        poseStack.translate(0.0F, bob, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
        PoseStack.Pose pose = poseStack.last();
        float halfSize = DISPLAY_HALF_SIZE * entity.getSizeScale() * pulse;
        vertex(vertexConsumer, pose, -halfSize, -halfSize, 0.0F, 1.0F, packedLight);
        vertex(vertexConsumer, pose, halfSize, -halfSize, 1.0F, 1.0F, packedLight);
        vertex(vertexConsumer, pose, halfSize, halfSize, 1.0F, 0.0F, packedLight);
        vertex(vertexConsumer, pose, -halfSize, halfSize, 0.0F, 0.0F, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v,
                               int packedLight) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(DispersingDataEntity entity) {
        return ORB_TEXTURES[Math.floorMod(entity.getTextureVariant(), ORB_TEXTURES.length)];
    }
}
