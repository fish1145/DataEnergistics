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
import org.joml.Matrix4f;

public class DataSanctumRenderer implements BlockEntityRenderer<DataSanctumBlockEntity> {

    public static final ModelResourceLocation BLACK_HOLE_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/data_sanctum/hd"));
    public static final ModelResourceLocation PORTAL_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/data_sanctum/csm"));
    private static final int BLACK_HOLE_MODE = 1;

    public DataSanctumRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DataSanctumBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(DataSanctumBlock.ACTIVE) || !state.hasProperty(DataSanctumBlock.MODE) || !state.hasProperty(DataSanctumBlock.FACING)) {
            return;
        }
        if (!state.getValue(DataSanctumBlock.ACTIVE)) {
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
        if (state.getValue(DataSanctumBlock.MODE) == BLACK_HOLE_MODE) {
            renderBlackHolePortal(poseStack, buffer);
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DataSanctumBlockEntity blockEntity) {
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

    private static void renderBlackHolePortal(PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.endPortal());
        Matrix4f pose = poseStack.last().pose();
        renderPortalCube(pose, consumer, 0.0625F, 1.96875F, 0.0625F, 0.9375F, 2.78125F, 0.9375F);
        renderPortalCube(pose, consumer, 0.125F, 2.0F, 0.125F, 0.875F, 2.75F, 0.875F);
    }

    private static void renderPortalCube(Matrix4f pose, VertexConsumer consumer,
                                         float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ) {
        addQuad(pose, consumer, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ);
        addQuad(pose, consumer, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ);
        addQuad(pose, consumer, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ);
        addQuad(pose, consumer, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ);
        addQuad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        addQuad(pose, consumer, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ);
    }

    private static void addQuad(Matrix4f pose, VertexConsumer consumer,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3) {
        consumer.addVertex(pose, x0, y0, z0);
        consumer.addVertex(pose, x1, y1, z1);
        consumer.addVertex(pose, x2, y2, z2);
        consumer.addVertex(pose, x3, y3, z3);
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
