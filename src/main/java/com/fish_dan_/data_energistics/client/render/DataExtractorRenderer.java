package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;

public class DataExtractorRenderer implements BlockEntityRenderer<DataExtractorBlockEntity> {

    public DataExtractorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(@NotNull DataExtractorBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {}

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull DataExtractorBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.25d, 0.5d, 0.25d);
    }
}
