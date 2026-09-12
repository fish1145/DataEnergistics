package com.fish_dan_.data_energistics.mixin.client.render.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimationUtils.class)
public abstract class CrossbowThirdPersonAnimationMixin {

    @Inject(method = "animateCrossbowCharge", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$holdChargingCannon(ModelPart rightArm, ModelPart leftArm,
                                                           LivingEntity entity, boolean rightHanded, CallbackInfo callback) {
        ItemStack stack = entity.getUseItem();
        if (!(stack.getItem() instanceof MatterConvergingCrossbowItem) || MatterConvergingCrossbowMode.fromId(stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(),
                MatterConvergingCrossbowMode.GRENADE.id())) == MatterConvergingCrossbowMode.CROSSBOW) {
            return;
        }
        ModelPart holding = rightHanded ? rightArm : leftArm;
        ModelPart supporting = rightHanded ? leftArm : rightArm;
        float yaw = Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot) * Mth.DEG_TO_RAD;
        float pitch = entity.getXRot() * Mth.DEG_TO_RAD;
        // Keep a two-handed hold throughout charging; the supporting hand never pulls a string.
        holding.yRot = (rightHanded ? -0.3F : 0.3F) + yaw;
        supporting.yRot = (rightHanded ? 0.6F : -0.6F) + yaw;
        holding.xRot = -Mth.HALF_PI + pitch + 0.1F;
        supporting.xRot = -1.5F + pitch;
        holding.zRot = 0.0F;
        supporting.zRot = 0.0F;
        callback.cancel();
    }

    @WrapOperation(method = "animateCrossbowCharge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CrossbowItem;getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private static int dataEnergistics$useCrossbowChargeDuration(ItemStack stack, LivingEntity entity, Operation<Integer> original) {
        return stack.getItem() instanceof MatterConvergingCrossbowItem ? MatterConvergingCrossbowItem.getChargeDuration(stack, entity) : original.call(stack, entity);
    }
}
