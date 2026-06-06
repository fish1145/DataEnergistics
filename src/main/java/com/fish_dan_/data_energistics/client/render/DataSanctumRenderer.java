package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;

public class DataSanctumRenderer implements BlockEntityRenderer<DataSanctumBlockEntity> {

    public static final ModelResourceLocation BLACK_HOLE_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/data_sanctum/hd"));
    public static final ModelResourceLocation PORTAL_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/data_sanctum/csm"));

    @SuppressWarnings("unused")
    public DataSanctumRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(@NotNull DataSanctumBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(DataSanctumBlock.MODE) || !state.hasProperty(DataSanctumBlock.FACING)) {
            return;
        }

        BakedModel model = getEffectModel(state.getValue(DataSanctumBlock.MODE));
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(getYRotation(state.getValue(DataSanctumBlock.FACING))));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderModel(Minecraft.getInstance().getBlockRenderer(), model, state, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull DataSanctumBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(3.0D, 0.0D, 3.0D).expandTowards(0.0D, 5.0D, 0.0D);
    }

    private static BakedModel getEffectModel(int mode) {
        Minecraft minecraft = Minecraft.getInstance();
        return switch (mode) {
            case 1 -> minecraft.getModelManager().getModel(BLACK_HOLE_MODEL);
            case 2 -> minecraft.getModelManager().getModel(PORTAL_MODEL);
            default -> null;
        };
    }

    private static float getYRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
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
                    1.0F,
                    1.0F,
                    1.0F,
                    packedLight,
                    packedOverlay,
                    ModelData.EMPTY,
                    renderType);
        }
    }
}
