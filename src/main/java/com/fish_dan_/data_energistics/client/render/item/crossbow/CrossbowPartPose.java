package com.fish_dan_.data_energistics.client.render.item.crossbow;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record CrossbowPartPose(Vector3f center, Quaternionf rotation, Vector3f size) {

    Matrix4f matrix() {
        return new Matrix4f().translation(this.center).rotate(this.rotation).scale(this.size);
    }

    CrossbowPartPose interpolateTo(CrossbowPartPose target, float progress) {
        return new CrossbowPartPose(new Vector3f(this.center).lerp(target.center, progress),
                new Quaternionf(this.rotation).slerp(target.rotation, progress),
                new Vector3f(this.size).lerp(target.size, progress));
    }

    CrossbowPartPose transformedBy(Matrix4f joint) {
        return new CrossbowPartPose(joint.transformPosition(new Vector3f(this.center)),
                joint.getNormalizedRotation(new Quaternionf()).mul(this.rotation), this.size);
    }
}
