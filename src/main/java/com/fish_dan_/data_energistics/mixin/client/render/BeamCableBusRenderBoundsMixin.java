package com.fish_dan_.data_energistics.mixin.client.render;

import com.fish_dan_.data_energistics.client.render.beam.BeamGeometryRenderer;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.CableBusTESR;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;

/** Extends only this client renderer's default one-block bounds, leaving the host block entity unchanged. */
@NullMarked
@Mixin(value = CableBusTESR.class, remap = false)
public abstract class BeamCableBusRenderBoundsMixin implements BlockEntityRenderer<CableBusBlockEntity> {

    @Override
    public AABB getRenderBoundingBox(CableBusBlockEntity blockEntity) {
        AABB bounds = new AABB(blockEntity.getBlockPos());
        for (Direction facing : Direction.values()) {
            if (blockEntity.getPart(facing) instanceof BeamEndpoint endpoint) {
                bounds = bounds.minmax(BeamGeometryRenderer.bounds(endpoint));
            }
        }
        return bounds;
    }
}
