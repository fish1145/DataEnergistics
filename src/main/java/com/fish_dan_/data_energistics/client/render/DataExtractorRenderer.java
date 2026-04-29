package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class DataExtractorRenderer implements BlockEntityRenderer<DataExtractorBlockEntity> {
    public DataExtractorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull DataExtractorBlockEntity blockEntity) {
        return blockEntity.isRangeDisplayEnabled();
    }

    @Override
    public void render(@NotNull DataExtractorBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!blockEntity.isRangeDisplayEnabled()) {
            return;
        }

        AABB aabb = blockEntity.getCoverageAabb().move(
                -blockEntity.getBlockPos().getX(),
                -blockEntity.getBlockPos().getY(),
                -blockEntity.getBlockPos().getZ()
        );

        var consumer = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, consumer, aabb, 1.0f, 0.35f, 0.2f, 0.9f);
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull DataExtractorBlockEntity blockEntity) {
        return blockEntity.getCoverageAabb();
    }
}
