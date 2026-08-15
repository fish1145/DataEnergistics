package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.entity.projectile.ThrownLightSaberEntity;
import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public class ThrownLightSaberRenderer extends ThrownItemRenderer<ThrownLightSaberEntity> {

    private static final float SANCTIFIER_SPIN_DEGREES_PER_TICK = 80.0F;
    private static final int SANCTIFIER_PRE_EMBED_SPIN_TICKS = 40;
    private final ItemRenderer itemRenderer;

    public ThrownLightSaberRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownLightSaberEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) - 90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        if (isSanctifier(stack) && hasSaberEnergyCard(stack)) {
            poseStack.scale(2.0F, 2.0F, 2.0F);
        }
        if (isSanctifier(stack)) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(getSanctifierSpinDegrees(entity, partialTick)));
        }
        if (entity.isEmbedded() && !isSanctifierPreEmbedSpinPhase(entity, stack)) {
            poseStack.translate(0.0D, 0.0D, 0.0D);
        }
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    private static boolean isSanctifier(ItemStack stack) {
        ResourceLocation itemId = stack.getItemHolder().getKey().location();
        return Data_Energistics.MODID.equals(itemId.getNamespace()) && "data_sanctifier".equals(itemId.getPath());
    }

    private static boolean hasSaberEnergyCard(ItemStack stack) {
        return stack.getItem() instanceof PoweredEnergyItem poweredEnergyItem && poweredEnergyItem.getSaberEnergyCardCount(stack) > 0;
    }

    private static float getSanctifierSpinDegrees(ThrownLightSaberEntity entity, float partialTick) {
        if (entity.isEmbedded()) {
            if (entity.getEmbeddedTime() < SANCTIFIER_PRE_EMBED_SPIN_TICKS) {
                return (entity.tickCount + partialTick) * SANCTIFIER_SPIN_DEGREES_PER_TICK;
            }
            return 0.0F;
        }
        return (entity.tickCount + partialTick) * SANCTIFIER_SPIN_DEGREES_PER_TICK;
    }

    private static boolean isSanctifierPreEmbedSpinPhase(ThrownLightSaberEntity entity, ItemStack stack) {
        return isSanctifier(stack) && entity.isEmbedded() && entity.getEmbeddedTime() < SANCTIFIER_PRE_EMBED_SPIN_TICKS;
    }
}
