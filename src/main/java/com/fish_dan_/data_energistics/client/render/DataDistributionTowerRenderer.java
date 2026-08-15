package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.tower.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

public class DataDistributionTowerRenderer implements BlockEntityRenderer<DataDistributionTowerBlockEntity> {

    private static final float CRYSTAL_BASE_Y = 3.6875f;
    private static final float CRYSTAL_ONLINE_FLOAT_RANGE = 0.14f;
    private static final float CRYSTAL_ONLINE_FLOAT_SPEED = 0.14f;
    private static final float CRYSTAL_MODEL_OFFSET_X = -0.5f;
    private static final float CRYSTAL_MODEL_OFFSET_Y = -1.75f;
    private static final float CRYSTAL_MODEL_OFFSET_Z = -0.5f;
    private static final double RENDER_BOX_HEIGHT = 4.0d;
    private static final ModelResourceLocation CRYSTAL_OFFLINE_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_off"));
    private static final ModelResourceLocation CRYSTAL_ONLINE_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_on"));

    @SuppressWarnings("unused")
    public DataDistributionTowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(DataDistributionTowerBlockEntity blockEntity) {
        return false;
    }

    @Override
    public void render(DataDistributionTowerBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        renderCrystal(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
    }

    @Override
    public AABB getRenderBoundingBox(DataDistributionTowerBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0.0d, RENDER_BOX_HEIGHT, 0.0d);
    }

    private void renderCrystal(DataDistributionTowerBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                               MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        BlockState state = blockEntity.getBlockState();
        boolean online = blockEntity.isNetworkNodeOnline();
        BakedModel model = minecraft.getModelManager().getModel(online ? CRYSTAL_ONLINE_MODEL : CRYSTAL_OFFLINE_MODEL);
        float bobOffset = 0.0f;
        if (online) {
            float phase = (Util.getMillis() * 0.001f) * (CRYSTAL_ONLINE_FLOAT_SPEED * 20.0f);
            bobOffset = (Mth.sin(phase) * 0.5f + 0.5f) * CRYSTAL_ONLINE_FLOAT_RANGE;
        }

        poseStack.pushPose();
        poseStack.translate(0.5f, CRYSTAL_BASE_Y + bobOffset, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(getCrystalYRotation(state)));
        poseStack.translate(CRYSTAL_MODEL_OFFSET_X, CRYSTAL_MODEL_OFFSET_Y, CRYSTAL_MODEL_OFFSET_Z);
        renderModel(blockRenderer, model, state, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static float getCrystalYRotation(BlockState state) {
        if (!state.hasProperty(DataDistributionTowerBlock.FACING)) {
            return 0.0f;
        }

        return switch (state.getValue(DataDistributionTowerBlock.FACING)) {
            case EAST -> -90.0f;
            case SOUTH -> -180.0f;
            case WEST -> 90.0f;
            default -> 0.0f;
        };
    }

    private static void renderModel(BlockRenderDispatcher blockRenderer, BakedModel model, BlockState state, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight, int packedOverlay) {
        RandomSource random = RandomSource.create(42L);
        for (RenderType renderType : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            VertexConsumer consumer = buffer.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false));
            blockRenderer.getModelRenderer().renderModel(
                    poseStack.last(),
                    consumer,
                    state,
                    model,
                    1.0f,
                    1.0f,
                    1.0f,
                    packedLight,
                    packedOverlay,
                    ModelData.EMPTY,
                    renderType);
        }
    }
}
