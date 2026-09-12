package com.fish_dan_.data_energistics.client.render.entity.cannon;

import com.fish_dan_.data_energistics.entity.projectile.MatterConvergingBoltEntity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;

/** Flight-only visuals; projectile collision, damage and detonation remain server-owned. */
public final class ElementalGrenadeRenderer {

    private static final ResourceLocation WIND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/wind_charge.png");
    private final ModelPart windRoot;
    private final ModelPart wind;
    private final ModelPart windCharge;
    private final ThrownItemRenderer<MatterConvergingBoltEntity> fireball;

    public ElementalGrenadeRenderer(EntityRendererProvider.Context context) {
        this.windRoot = context.bakeLayer(ModelLayers.WIND_CHARGE).getChild("bone");
        this.wind = this.windRoot.getChild("wind");
        this.windCharge = this.windRoot.getChild("wind_charge");
        this.fireball = new ThrownItemRenderer<>(context, 3.0F, true);
    }

    /** Matches vanilla WindChargeModel rotation and WindChargeRenderer texture scrolling. */
    public void wind(float age, PoseStack pose, MultiBufferSource buffers, int light) {
        this.windCharge.yRot = -age * 16 * Mth.DEG_TO_RAD;
        this.wind.yRot = age * 16 * Mth.DEG_TO_RAD;
        var vertices = buffers.getBuffer(RenderType.breezeWind(WIND_TEXTURE, age * 0.03F % 1, 0));
        this.windRoot.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
    }

    /** Uses the same renderer, scale and block-light policy as vanilla EntityType.FIREBALL. */
    public void fire(MatterConvergingBoltEntity entity, float yaw, float partialTick, PoseStack pose,
                     MultiBufferSource buffers) {
        this.fireball.render(entity, yaw, partialTick, pose, buffers, this.fireball.getPackedLightCoords(entity, partialTick));
    }
}
