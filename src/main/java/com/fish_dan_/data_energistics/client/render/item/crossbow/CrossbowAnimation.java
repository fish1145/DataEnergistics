package com.fish_dan_.data_energistics.client.render.item.crossbow;

/** One hand's visual timeline. Update once per client tick and interpolate during rendering. */
public final class CrossbowAnimation {

    private static final float DEPLOY_TICKS = 36.0F;
    private static final int RELEASE_TICKS = 3;

    private boolean initialized;
    private boolean held;
    private boolean active;
    private boolean charged;
    private float previousDeployment;
    private float deployment;
    private float previousDraw;
    private float draw;
    private float releaseDraw;
    private int releaseTicks;
    private float previousRecoil;
    private float recoil;
    private int recoilTicks;

    /** Observes actual use/loaded state; never changes item components or gameplay timing. */
    public void tick(boolean held, boolean using, boolean charged, float progress) {
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Crossbow charge progress must be in [0, 1]");
        }
        if (!this.initialized || held && !this.held) {
            this.initialized = true;
            this.deployment = held ? 1.0F / DEPLOY_TICKS : 0.0F;
            this.previousDeployment = 0.0F;
            this.draw = charged ? 1.0F : using ? progress : 0.0F;
            this.previousDraw = this.draw;
        } else {
            this.previousDeployment = this.deployment;
            this.previousDraw = this.draw;
            this.deployment = Math.clamp(this.deployment + (held ? 1.0F : -1.0F) / DEPLOY_TICKS, 0.0F, 1.0F);
        }
        if (charged || using) {
            this.draw = charged ? 1.0F : progress;
        } else if (this.active && (this.charged || this.draw >= 0.98F)) {
            // Automatic fire may never expose a charged frame on the client.
            this.releaseDraw = this.draw;
            this.releaseTicks = 0;
            this.recoilTicks = 0;
            this.previousRecoil = this.recoil;
            this.recoil = 0.16F;
        } else {
            this.releaseTicks = Math.min(RELEASE_TICKS, this.releaseTicks + 1);
            this.draw = this.releaseDraw * (1.0F - smooth(this.releaseTicks / (float) RELEASE_TICKS));
            this.recoilTicks = Math.min(8, this.recoilTicks + 1);
            this.previousRecoil = this.recoil;
            this.recoil = recoilOffset(this.recoilTicks);
        }
        this.held = held;
        this.active = using || charged;
        this.charged = charged;
    }

    /** Read-only frame sampling; all renders of a hand share the same tick state. */
    public Pose pose(float partialTick) {
        float partial = Math.clamp(partialTick, 0.0F, 1.0F);
        float draw = this.previousDraw + (this.draw - this.previousDraw) * partial;
        float recoil = this.previousRecoil + (this.recoil - this.previousRecoil) * partial;
        int stage = this.charged ? 3 : this.active ? Math.min(3, 1 + (int) (draw * 3.0F)) : 0;
        return new Pose(this.previousDeployment + (this.deployment - this.previousDeployment) * partial, draw, stage, recoil);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float recoilOffset(int ticks) {
        if (ticks <= 1) return 0.16F;
        if (ticks == 2) return 0.10F;
        if (ticks == 3) return 0.055F;
        if (ticks == 4) return -0.025F;
        if (ticks == 5) return 0.018F;
        return 0.0F;
    }

    public record Pose(float deployment, float draw, int stage, float recoil) {

        public Pose(float deployment, float draw, int stage) {
            this(deployment, draw, stage, 0.0F);
        }

        public float railDeployment() {
            return phase(0.0F, 12.0F);
        }

        public float bowSlide() {
            return phase(12.0F, 20.0F);
        }

        public float armExtension() {
            return phase(20.0F, 26.0F);
        }

        public float elbowUnfold() {
            return phase(26.0F, 32.0F);
        }

        public float tipUnfold() {
            return phase(32.0F, DEPLOY_TICKS);
        }

        public float drawAmount() {
            return this.draw * tipUnfold();
        }

        private float phase(float start, float end) {
            return smooth(Math.clamp((this.deployment * DEPLOY_TICKS - start) / (end - start), 0.0F, 1.0F));
        }

        public static Pose stationary(boolean charged) {
            return charged ? new Pose(1.0F, 1.0F, 3, 0.0F) : new Pose(0.0F, 0.0F, 0, 0.0F);
        }
    }
}
