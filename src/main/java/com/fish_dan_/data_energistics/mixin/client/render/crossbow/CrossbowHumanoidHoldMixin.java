package com.fish_dan_.data_energistics.mixin.client.render.crossbow;

import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowThirdPersonPose;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Left-button cannon charging never reaches vanilla's crossbow-use arm pose. */
@Mixin(HumanoidModel.class)
public abstract class CrossbowHumanoidHoldMixin {

    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void dataEnergistics$aimHeldWeapon(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                               float age, float headYaw, float headPitch, CallbackInfo callback) {
        boolean main = entity.getMainHandItem().getItem() instanceof MatterConvergingCrossbowItem;
        boolean off = entity.getOffhandItem().getItem() instanceof MatterConvergingCrossbowItem;
        if (!main && !off) return;
        if (entity.isUsingItem()) {
            var using = entity.getUseItem();
            // Preserve bow draw/string-pull and unrelated eating, blocking or item-use animations.
            if (!(using.getItem() instanceof MatterConvergingCrossbowItem) || MatterConvergingCrossbowItem.mode(using) == MatterConvergingCrossbowMode.CROSSBOW) return;
        }
        if (main && off) {
            CrossbowThirdPersonPose.aim(this.rightArm, this.head);
            CrossbowThirdPersonPose.aim(this.leftArm, this.head);
        } else {
            boolean right = (main ? entity.getMainArm() : entity.getMainArm().getOpposite()) == HumanoidArm.RIGHT;
            boolean support = (main ? entity.getOffhandItem() : entity.getMainHandItem()).isEmpty();
            CrossbowThirdPersonPose.hold(this.rightArm, this.leftArm, this.head, right, support);
        }
    }
}
