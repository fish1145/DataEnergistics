package com.fish_dan_.data_energistics.mixin.client.render.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnimationUtils.class)
public abstract class CrossbowThirdPersonAnimationMixin {

    @WrapOperation(method = "animateCrossbowCharge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CrossbowItem;getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private static int dataEnergistics$useCrossbowChargeDuration(ItemStack stack, LivingEntity entity, Operation<Integer> original) {
        return stack.getItem() instanceof MatterConvergingCrossbowItem ? MatterConvergingCrossbowItem.getChargeDuration(stack, entity) : original.call(stack, entity);
    }
}
