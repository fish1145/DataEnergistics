package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.orbital.animation.OrbitalAnimationClock;
import com.fish_dan_.data_energistics.client.render.orbital.model.OrbitalConstructModel;
import com.fish_dan_.data_energistics.client.render.orbital.model.OrbitalModelRenderer;
import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.PoseStack;

/** Textured containment hardware and emissive digital core for the descending server-authoritative payload. */
public final class OrbitalAnnihilatorRenderer extends EntityRenderer<OrbitalAnnihilatorProjectileEntity> {

    private static final ResourceLocation TEXTURE = Data_Energistics.id("textures/block/orbital/construct.png");

    public OrbitalAnnihilatorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(OrbitalAnnihilatorProjectileEntity entity, float entityYaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int packedLight) {
        double time = entity.level().getGameTime() + (double) partialTick;
        float rotation = OrbitalAnimationClock.angle(time, entity.getId(), 100);
        OrbitalModelRenderer body = new OrbitalModelRenderer(buffers.getBuffer(OrbitalModelRenderer.SOLID),
                true, false, packedLight, 1, 1, 1, 1);
        OrbitalConstructModel.payload(poses, body, 0, 0.6, 0, 1.4F, rotation);
        OrbitalModelRenderer light = new OrbitalModelRenderer(buffers.getBuffer(OrbitalModelRenderer.EMISSIVE),
                true, true, packedLight, 1, 1, 1, 0.9F);
        OrbitalConstructModel.payload(poses, light, 0, 0.6, 0, 1.4F, rotation);
        super.render(entity, entityYaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public boolean shouldRender(OrbitalAnnihilatorProjectileEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return entity.shouldRender(cameraX, cameraY, cameraZ) && frustum.isVisible(entity.getBoundingBox().inflate(1.2));
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitalAnnihilatorProjectileEntity entity) {
        return TEXTURE;
    }
}
