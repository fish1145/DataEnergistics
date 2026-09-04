package com.fish_dan_.data_energistics.mixin.client.render;

import com.fish_dan_.data_energistics.client.render.beam.BeamGeometryRenderer;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;
import com.fish_dan_.data_energistics.part.beam.BeamFormerPart;

import net.minecraft.client.renderer.MultiBufferSource;

import appeng.api.parts.IPartItem;
import appeng.parts.automation.UpgradeablePart;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps client method signatures out of the part class inspected by AE2's server-side model registration. */
@NullMarked
@Mixin(value = BeamFormerPart.class, remap = false)
public abstract class BeamFormerPartRenderMixin extends UpgradeablePart implements BeamEndpoint {

    protected BeamFormerPartRenderMixin(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public void renderDynamic(float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BeamGeometryRenderer.render(this, poseStack, buffers, partialTicks);
    }
}
