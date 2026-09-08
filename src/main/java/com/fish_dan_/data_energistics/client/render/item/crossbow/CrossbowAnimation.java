package com.fish_dan_.data_energistics.client.render.item.crossbow;

/** Visual state for one entity hand, sampled on the render thread in game ticks. */
public final class CrossbowAnimation {

    private static final float DEPLOY_TICKS = 36.0F;
    private static final float RELEASE_TICKS = 3.0F;

    private float lastTime = Float.NaN;
    private float deployment;
    private float draw;
    private float releaseTime;
    private float releaseDraw;
    private boolean drawing;

    /** Samples visual motion without changing the item, ammunition, or charging duration. */
    public Pose sample(float time, boolean held, boolean using, boolean charged, float progress) {
        if (!Float.isFinite(time) || !Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Crossbow animation requires finite time and progress in [0, 1]");
        }
        if (Float.isNaN(this.lastTime) || time < this.lastTime || time - this.lastTime > 10.0F) {
            this.deployment = 0.0F;
            this.draw = charged ? 1.0F : using ? progress : 0.0F;
            this.releaseDraw = 0.0F;
            this.drawing = using || charged;
            this.lastTime = time;
        }

        float elapsed = time - this.lastTime;
        this.lastTime = time;
        this.deployment = clamp(this.deployment + (held || charged ? elapsed : -elapsed) / DEPLOY_TICKS);
        if (charged || using) {
            this.draw = charged ? 1.0F : progress;
        } else {
            if (this.drawing) {
                this.releaseTime = time;
                this.releaseDraw = this.draw;
            }
            this.draw = this.releaseDraw * (1.0F - smooth(clamp((time - this.releaseTime) / RELEASE_TICKS)));
        }
        this.drawing = using || charged;
        int stage = charged ? 3 : using ? Math.min(3, 1 + (int) (progress * 3.0F)) : 0;
        return new Pose(this.deployment, this.draw, stage);
    }

    private static float clamp(float value) {
        return Math.clamp(value, 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    public record Pose(float deployment, float draw, int stage) {

        public float frameDeployment() {
            return smooth(clamp((this.deployment * DEPLOY_TICKS - 10.0F) / 12.0F));
        }

        public float bowSlide() {
            return smooth(clamp(this.deployment * DEPLOY_TICKS / 10.0F));
        }

        public float railDeployment() {
            return smooth(clamp((this.deployment * DEPLOY_TICKS - 24.0F) / 12.0F));
        }

        public static Pose stationary(boolean charged) {
            return charged ? new Pose(1.0F, 1.0F, 3) : new Pose(0.0F, 0.0F, 0);
        }
    }
}
