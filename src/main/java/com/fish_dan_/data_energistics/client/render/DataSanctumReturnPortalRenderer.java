package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.blockentity.DataSanctumReturnPortalBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public class DataSanctumReturnPortalRenderer implements BlockEntityRenderer<DataSanctumReturnPortalBlockEntity> {

    public DataSanctumReturnPortalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(@NotNull DataSanctumReturnPortalBlockEntity blockEntity, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight,
                       int packedOverlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(DataSanctumRenderer.PORTAL_MODEL);
        BlockState renderState = Blocks.IRON_BLOCK.defaultBlockState();
        renderModel(Minecraft.getInstance().getBlockRenderer(), model, renderState, poseStack, buffer, packedLight, packedOverlay);
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull DataSanctumReturnPortalBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(3.0D, 0.0D, 3.0D).expandTowards(0.0D, 5.0D, 0.0D);
    }

    private static void renderModel(BlockRenderDispatcher blockRenderer, BakedModel model, BlockState state,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                    int packedOverlay) {
        var random = RandomSource.create(42L);
        for (var renderType : model.getRenderTypes(state, random, ModelData.EMPTY)) {
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
