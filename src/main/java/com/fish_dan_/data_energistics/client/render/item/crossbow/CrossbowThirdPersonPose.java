package com.fish_dan_.data_energistics.client.render.item.crossbow;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Uses the rendered head pose, so remote players and inventory previews share the same interpolated aim. */
public final class CrossbowThirdPersonPose {

    private CrossbowThirdPersonPose() {}

    public static void aim(ModelPart arm, ModelPart head) {
        arm.xRot = -Mth.HALF_PI + head.xRot;
        arm.yRot = head.yRot;
        arm.zRot = 0;
    }

    public static void hold(ModelPart rightArm, ModelPart leftArm, ModelPart head, boolean rightHanded, boolean support) {
        ModelPart holding = rightHanded ? rightArm : leftArm;
        ModelPart supporting = rightHanded ? leftArm : rightArm;
        aim(holding, head);
        if (support) {
            supporting.xRot = -1.5F + head.xRot;
            supporting.yRot = (rightHanded ? 0.6F : -0.6F) + head.yRot;
            supporting.zRot = 0;
        }
    }
}
