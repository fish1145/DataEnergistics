package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

/** One hand's visual timeline. Update once per client tick and interpolate during rendering. */
public final class CrossbowAnimation {

    private static final float DEPLOY_TICKS = 36.0F;
    private static final int RELEASE_TICKS = 3;

    private boolean initialized;
    private boolean held;
    private boolean active;
    private boolean charged;
    private MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.GRENADE;
    private float previousDeployment;
    private float deployment;
    private float previousDraw;
    private float draw;
    private float releaseDraw;
    private int releaseTicks;
    private float previousRecoil;
    private float recoil;
    private int recoilTicks = CrossbowRailRecoil.DURATION_TICKS;

    /** Observes actual use/loaded state; never changes item components or gameplay timing. */
    public void tick(boolean held, boolean using, boolean charged, float progress, MatterConvergingCrossbowMode mode) {
        tick(held, using, charged, progress, mode, false);
    }

    public void tick(boolean held, boolean using, boolean charged, float progress, MatterConvergingCrossbowMode mode, boolean fired) {
        tick(held, using, charged, progress, mode, fired, -1);
    }

    public void tick(boolean held, boolean using, boolean charged, float progress, MatterConvergingCrossbowMode mode, boolean fired, int cooldownElapsed) {
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Crossbow charge progress must be in [0, 1]");
        }
        this.previousRecoil = this.recoil;
        this.recoilTicks = Math.min(CrossbowRailRecoil.DURATION_TICKS, this.recoilTicks + 1);
        if (!held || !this.held || mode != this.mode) {
            this.recoilTicks = CrossbowRailRecoil.DURATION_TICKS;
            this.previousRecoil = 0.0F;
        }
        if (!this.initialized || held && !this.held || mode != this.mode && held) {
            this.initialized = true;
            this.deployment = held ? initialDeployment(this.mode, mode) : 0.0F;
            this.previousDeployment = 0.0F;
            this.draw = charged ? 1.0F : using ? progress : 0.0F;
            this.previousDraw = this.draw;
        } else {
            this.previousDeployment = this.deployment;
            this.previousDraw = this.draw;
            float target = held ? targetDeployment(mode) : 0.0F;
            this.deployment += Math.signum(target - this.deployment) / DEPLOY_TICKS;
            if (Math.abs(target - this.deployment) < 1.0F / DEPLOY_TICKS) this.deployment = target;
        }
        if (charged || using) {
            this.draw = charged ? 1.0F : progress;
        } else if (mode == MatterConvergingCrossbowMode.CROSSBOW && held && this.held && mode == this.mode && this.active && (this.charged || this.draw >= 0.98F)) {
            // Automatic fire may never expose a charged frame on the client.
            this.releaseDraw = this.draw;
            this.releaseTicks = 0;
            this.recoilTicks = mode == MatterConvergingCrossbowMode.GRENADE ? CrossbowRailRecoil.DURATION_TICKS : 0;
        } else {
            this.releaseTicks = Math.min(RELEASE_TICKS, this.releaseTicks + 1);
            this.draw = this.releaseDraw * (1.0F - smooth(this.releaseTicks / (float) RELEASE_TICKS));
        }
        if (fired && held && mode == MatterConvergingCrossbowMode.RAIL) this.recoilTicks = 0;
        if (held && mode == MatterConvergingCrossbowMode.RAIL && cooldownElapsed >= 0) this.recoilTicks = cooldownElapsed;
        this.recoil = mode == MatterConvergingCrossbowMode.CROSSBOW ? CrossbowRailRecoil.bowRetraction(this.recoilTicks) : CrossbowRailRecoil.retraction(this.recoilTicks);
        this.held = held;
        this.active = using || charged;
        this.charged = charged;
        this.mode = mode;
    }

    /** Read-only frame sampling; all renders of a hand share the same tick state. */
    public Pose pose(float partialTick) {
        float partial = Math.clamp(partialTick, 0.0F, 1.0F);
        float draw = this.previousDraw + (this.draw - this.previousDraw) * partial;
        float recoil = this.previousRecoil + (this.recoil - this.previousRecoil) * partial;
        int stage = this.charged ? 3 : this.active ? Math.min(3,
                (this.mode == MatterConvergingCrossbowMode.CROSSBOW ? 1 : 0) + (int) (draw * 3.0F)) : 0;
        return new Pose(this.previousDeployment + (this.deployment - this.previousDeployment) * partial, draw, stage, recoil, this.mode);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float targetDeployment(MatterConvergingCrossbowMode mode) {
        return switch (mode) {
            case RAIL -> 12.0F / DEPLOY_TICKS;
            case CROSSBOW -> 1.0F;
            case GRENADE -> 0.0F;
        };
    }

    private static float initialDeployment(MatterConvergingCrossbowMode previousMode, MatterConvergingCrossbowMode mode) {
        return previousMode == MatterConvergingCrossbowMode.RAIL && mode == MatterConvergingCrossbowMode.CROSSBOW ? 12.0F / DEPLOY_TICKS : 0.0F;
    }

    public record Pose(float deployment, float draw, int stage, float recoil, MatterConvergingCrossbowMode mode) {

        public Pose(float deployment, float draw, int stage) {
            this(deployment, draw, stage, 0.0F, MatterConvergingCrossbowMode.GRENADE);
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
            return this.mode == MatterConvergingCrossbowMode.CROSSBOW ? this.draw * tipUnfold() : 0.0F;
        }

        private float phase(float start, float end) {
            return smooth(Math.clamp((this.deployment * DEPLOY_TICKS - start) / (end - start), 0.0F, 1.0F));
        }

        public static Pose stationary(boolean charged) {
            return charged ? new Pose(1.0F, 1.0F, 3, 0.0F, MatterConvergingCrossbowMode.CROSSBOW) : new Pose(0.0F, 0.0F, 0, 0.0F, MatterConvergingCrossbowMode.GRENADE);
        }
    }
}
