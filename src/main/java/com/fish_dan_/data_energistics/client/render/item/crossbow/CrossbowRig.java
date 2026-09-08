package com.fish_dan_.data_energistics.client.render.item.crossbow;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Articulated transforms relative to the supplied, fully deployed and unstrung on model. */
final class CrossbowRig {

    private final CrossbowAnimation.Pose pose;
    private final Matrix4f[] left;
    private final Matrix4f[] right;

    CrossbowRig(CrossbowAnimation.Pose pose) {
        this.pose = pose;
        this.left = joints(true);
        this.right = joints(false);
    }

    Matrix4f transform(CrossbowPartPose folded, CrossbowPartPose deployed,
                       CrossbowDeployment group, CrossbowMotion motion) {
        if (this.pose.deployment() == 0.0F) {
            return folded.matrix();
        }
        if (motion == CrossbowMotion.FIXED) {
            return folded.interpolateTo(deployed, group.progress(this.pose)).matrix();
        }
        if (group == CrossbowDeployment.STRAP) {
            return strap(folded, deployed, motion).matrix();
        }
        if (motion == CrossbowMotion.LEFT_STRING || motion == CrossbowMotion.RIGHT_STRING) {
            return string(folded, deployed, motion == CrossbowMotion.LEFT_STRING).matrix();
        }
        if (motion == CrossbowMotion.BOW_BRIDGE) {
            // The receiver bridge belongs to the bow carriage, not the extending barrel or string.
            return folded.interpolateTo(deployed, this.pose.armExtension()).matrix();
        }
        CrossbowPartPose rigged = switch (motion) {
            case LEFT_ROOT -> deployed.transformedBy(this.left[0]);
            case LEFT_ELBOW -> deployed.transformedBy(this.left[1]);
            case LEFT_TIP -> deployed.transformedBy(this.left[2]);
            case RIGHT_ROOT -> deployed.transformedBy(this.right[0]);
            case RIGHT_ELBOW -> deployed.transformedBy(this.right[1]);
            case RIGHT_TIP -> deployed.transformedBy(this.right[2]);
            default -> throw new IllegalArgumentException("Unexpected crossbow rig part: " + motion);
        };
        if (motion == CrossbowMotion.LEFT_ROOT || motion == CrossbowMotion.RIGHT_ROOT) {
            return rigged.matrix();
        }
        boolean isLeft = motion == CrossbowMotion.LEFT_ELBOW || motion == CrossbowMotion.LEFT_TIP;
        Matrix4f slide = new Matrix4f().translation((isLeft ? -10.0F : 10.0F) * this.pose.bowSlide() / 16.0F,
                0.0F, -8.0F * this.pose.armExtension() / 16.0F);
        // The nested sections translate out of the fixed outer rail before either hinge turns.
        return folded.transformedBy(slide).interpolateTo(rigged, this.pose.armExtension()).matrix();
    }

    private Matrix4f[] joints(boolean left) {
        float side = left ? 1.0F : -1.0F;
        Matrix4f root = hinge(new Matrix4f(), left ? 5.0F : 11.0F, -4.5F, side * 6.0F * this.pose.drawAmount());
        root.translate(side * 10.0F * (1.0F - this.pose.bowSlide()) / 16.0F, 0.0F,
                8.0F * (1.0F - this.pose.armExtension()) / 16.0F);
        Matrix4f elbow = hinge(new Matrix4f(root), left ? -3.44975F : 19.44975F, 0.19975F,
                side * 45.0F * (1.0F - this.pose.elbowUnfold()));
        Matrix4f tip = hinge(new Matrix4f(elbow), left ? 1.0F : 15.0F, -4.5F,
                side * 45.0F * (1.0F - this.pose.tipUnfold()));
        return new Matrix4f[] { root, elbow, tip };
    }

    private static Matrix4f hinge(Matrix4f parent, float x, float z, float degrees) {
        return parent.translate(x / 16.0F, 0.0F, z / 16.0F)
                .rotateY((float) Math.toRadians(degrees)).translate(-x / 16.0F, 0.0F, -z / 16.0F);
    }

    private CrossbowPartPose string(CrossbowPartPose folded, CrossbowPartPose deployed, boolean left) {
        Vector3f tip = new Vector3f((left ? -3.0F : 19.0F) / 16.0F, 7.0F / 16.0F, 5.57F / 16.0F);
        (left ? this.left[0] : this.right[0]).transformPosition(tip);
        Vector3f latch = new Vector3f((left ? 7.0F : 9.0F) / 16.0F, 7.0F / 16.0F,
                (5.57F + 8.0F * this.pose.drawAmount()) / 16.0F);
        Vector3f foldedSpan = new Vector3f(5.0F / 16.0F, 0.0F, 0.0F).rotate(folded.rotation());
        Vector3f foldedTip = new Vector3f(folded.center()).fma(left ? -1.0F : 1.0F, foldedSpan);
        Vector3f foldedLatch = new Vector3f(folded.center()).fma(left ? 1.0F : -1.0F, foldedSpan);
        Vector3f packedTip = new Vector3f((left ? 7.0F : 9.0F) / 16.0F, 7.0F / 16.0F, 13.57F / 16.0F);
        tip.fma(1.0F - this.pose.bowSlide(), foldedTip.sub(packedTip));
        latch = foldedLatch.lerp(latch, this.pose.bowSlide());
        Vector3f span = left ? new Vector3f(latch).sub(tip) : new Vector3f(tip).sub(latch);
        Vector3f center = new Vector3f(tip).add(latch).mul(0.5F);
        Vector3f size = new Vector3f(deployed.size());
        float outlineMargin = Math.abs(size.x) - 10.0F / 16.0F;
        size.x = Math.copySign(span.length() + outlineMargin, size.x);
        Quaternionf rotation = new Quaternionf().rotationY(-(float) Math.atan2(span.z, span.x));
        return new CrossbowPartPose(center, rotation, size);
    }

    private CrossbowPartPose strap(CrossbowPartPose folded, CrossbowPartPose deployed, CrossbowMotion motion) {
        float progress = this.pose.bowSlide();
        if (progress == 0.0F) {
            return folded;
        }
        if (progress == 1.0F || motion == CrossbowMotion.STRAP_TOP) {
            return deployed;
        }
        float height = (0.25F + 7.5F * progress) / 16.0F;
        Vector3f center = new Vector3f(deployed.center());
        Vector3f size = new Vector3f(deployed.size());
        if (motion == CrossbowMotion.STRAP_BOTTOM) {
            center.y = 5.0F / 16.0F - height;
        } else {
            center.y = 5.0F / 16.0F - height * 0.5F;
            size.y = height + 0.0002F / 16.0F;
        }
        CrossbowPartPose extended = new CrossbowPartPose(center, new Quaternionf(), size);
        return folded.interpolateTo(extended, Math.min(1.0F, progress * 5.0F));
    }
}
