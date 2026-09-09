package com.fish_dan_.data_energistics.mixin.client.render.crossbow;

import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowAnimationStates;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowFirstPersonPose;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class CrossbowFirstPersonAnimationMixin {

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                    boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int light);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$renderAimedCrossbow(AbstractClientPlayer player, float partialTicks, float pitch,
                                                     InteractionHand hand, float swing, ItemStack stack,
                                                     float equipped, PoseStack poseStack, MultiBufferSource buffer,
                                                     int light, CallbackInfo callback) {
        if (!(stack.getItem() instanceof MatterConvergingCrossbowItem) || player.isScoping()) {
            return;
        }
        boolean rightHand = (hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite()) == HumanoidArm.RIGHT;
        InteractionHand aimedHand = player.isUsingItem() ? player.getUsedItemHand() : player.getMainHandItem().getItem() instanceof MatterConvergingCrossbowItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        float progress = hand == aimedHand ? CrossbowAnimationStates.pose(player, hand, partialTicks).draw() : 0.0F;
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(
                stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.GRENADE.id()));
        float pitchLag = 0.0F;
        float yawLag = 0.0F;
        if (player instanceof LocalPlayer localPlayer) {
            pitchLag = (localPlayer.getViewXRot(partialTicks) - Mth.lerp(partialTicks, localPlayer.xBobO, localPlayer.xBob)) * 0.1F;
            yawLag = (localPlayer.getViewYRot(partialTicks) - Mth.lerp(partialTicks, localPlayer.yBobO, localPlayer.yBob)) * 0.1F;
        }
        poseStack.pushPose();
        poseStack.mulPose(CrossbowFirstPersonPose.transform(rightHand, CrossbowFirstPersonPose.aim(mode, progress),
                equipped, swing, pitchLag, yawLag));
        this.renderItem(player, stack, rightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !rightHand, poseStack, buffer, light);
        poseStack.popPose();
        callback.cancel();
    }
}
