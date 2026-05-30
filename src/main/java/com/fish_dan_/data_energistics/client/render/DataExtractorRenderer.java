package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;

public class DataExtractorRenderer implements BlockEntityRenderer<DataExtractorBlockEntity> {

    public DataExtractorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(@NotNull DataExtractorBlockEntity blockEntity) {
        return blockEntity.isRangeDisplayEnabled();
    }

    @Override
    public void render(@NotNull DataExtractorBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isRangeDisplayEnabled()) {
            AABB aabb = blockEntity.getCoverageAabb().move(
                    -blockEntity.getBlockPos().getX(),
                    -blockEntity.getBlockPos().getY(),
                    -blockEntity.getBlockPos().getZ());

            var consumer = buffer.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, consumer, aabb, 1.0f, 0.35f, 0.2f, 0.9f);
        }
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull DataExtractorBlockEntity blockEntity) {
        if (!blockEntity.isRangeDisplayEnabled()) {
            return new AABB(blockEntity.getBlockPos()).inflate(0.25d, 0.5d, 0.25d);
        }
        return blockEntity.getCoverageAabb();
    }
}
