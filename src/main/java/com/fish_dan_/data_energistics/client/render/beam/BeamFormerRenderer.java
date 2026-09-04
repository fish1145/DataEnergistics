package com.fish_dan_.data_energistics.client.render.beam;

import com.fish_dan_.data_energistics.blockentity.beam.BeamFormerBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.PoseStack;

/** Renders the server-published edges of both full-block beam devices. */
public final class BeamFormerRenderer implements BlockEntityRenderer<BeamFormerBlockEntity> {

    @Override
    public void render(BeamFormerBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BeamGeometryRenderer.render(blockEntity, poseStack, buffers, partialTicks);
    }

    @Override
    public AABB getRenderBoundingBox(BeamFormerBlockEntity blockEntity) {
        return BeamGeometryRenderer.bounds(blockEntity);
    }

    @Override
    public int getViewDistance() {
        return 1040;
    }
}
