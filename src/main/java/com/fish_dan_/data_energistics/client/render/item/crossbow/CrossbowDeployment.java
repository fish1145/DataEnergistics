package com.fish_dan_.data_energistics.client.render.item.crossbow;

enum CrossbowDeployment {

    FRAME,
    RAIL;

    float progress(CrossbowAnimation.Pose pose) {
        return this == FRAME ? pose.frameDeployment() : pose.railDeployment();
    }
}
