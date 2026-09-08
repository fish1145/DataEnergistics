package com.fish_dan_.data_energistics.client.render.item.crossbow;

enum CrossbowDeployment {

    FRAME,
    BOW,
    STRAP,
    RAIL;

    float progress(CrossbowAnimation.Pose pose) {
        return switch (this) {
            case FRAME -> 0.0F;
            case BOW -> pose.tipUnfold();
            case STRAP -> pose.bowSlide();
            case RAIL -> pose.railDeployment();
        };
    }
}
