package com.fish_dan_.data_energistics.client.render.item.crossbow;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record CrossbowPartPose(Vector3f center, Quaternionf rotation, Vector3f size) {

    Matrix4f transformTo(CrossbowPartPose target, CrossbowDeployment group, CrossbowMotion motion,
                         CrossbowAnimation.Pose pose) {
        float progress = group.progress(pose);
        Vector3f position = new Vector3f(this.center);
        if (group == CrossbowDeployment.BOW) {
            // Move the still-folded bow out of its housing as one assembly.
            position.z -= pose.bowSlide() * 0.5F;
        }
        position.lerp(target.center, progress);
        Quaternionf orientation = new Quaternionf(this.rotation).slerp(target.rotation, progress);
        Vector3f dimensions = new Vector3f(this.size).lerp(target.size, progress);
        if (motion == CrossbowMotion.LEFT_STRING || motion == CrossbowMotion.RIGHT_STRING) {
            // Follow the moving attachment points instead of rotating each string about its center.
            Vector3f span = new Vector3f(Math.abs(this.size.x), 0.0F, 0.0F).rotate(this.rotation);
            span.lerp(new Vector3f(Math.abs(target.size.x), 0.0F, 0.0F).rotate(target.rotation), progress);
            dimensions.x = Math.copySign(span.length(), target.size.x);
            orientation.rotationY(-(float) Math.atan2(span.z, span.x));
        }
        return motion.transform(pose.draw() * pose.railDeployment())
                .translate(position).rotate(orientation).scale(dimensions);
    }
}
