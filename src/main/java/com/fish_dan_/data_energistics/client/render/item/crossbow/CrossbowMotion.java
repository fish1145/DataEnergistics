package com.fish_dan_.data_energistics.client.render.item.crossbow;

import org.joml.Matrix4f;
import org.joml.Vector3f;

enum CrossbowMotion {

    FIXED,
    LEFT_LIMB,
    RIGHT_LIMB,
    LEFT_STRING,
    RIGHT_STRING;

    Matrix4f transform(float draw) {
        return switch (this) {
            case FIXED -> new Matrix4f();
            case LEFT_LIMB -> limb(true, draw);
            case RIGHT_LIMB -> limb(false, draw);
            case LEFT_STRING -> string(true, draw);
            case RIGHT_STRING -> string(false, draw);
        };
    }

    private static Matrix4f limb(boolean left, float draw) {
        float x = (left ? 5.0F : 11.0F) / 16.0F;
        float z = -4.5F / 16.0F;
        float angle = (float) Math.toRadians((left ? 6.0F : -6.0F) * draw);
        return new Matrix4f().translation(x, 0.0F, z).rotateY(angle).translate(-x, 0.0F, -z);
    }

    private static Matrix4f string(boolean left, float draw) {
        float anchorX = left ? -3.0F : 19.0F;
        float endX = left ? 7.0F : 9.0F;
        float length = endX - anchorX;
        Vector3f anchor = new Vector3f(anchorX / 16.0F, 7.0F / 16.0F, 5.57F / 16.0F);
        Vector3f pulledAnchor = limb(left, draw).transformPosition(new Vector3f(anchor));
        Vector3f end = new Vector3f(endX / 16.0F, anchor.y, (5.57F + 8.0F * draw) / 16.0F);
        Vector3f direction = end.sub(pulledAnchor).mul(16.0F / length);
        float angle = -(float) Math.atan2(direction.z, direction.x);
        return new Matrix4f().translation(pulledAnchor)
                .rotateY(angle).scale(direction.length(), 1.0F, 1.0F)
                .translate(-anchor.x, -anchor.y, -anchor.z);
    }
}
