package com.fish_dan_.data_energistics.client.render.item.crossbow;

/** A fast half-stroke retraction, a brake hold, and a controlled return without overshooting. */
public final class CrossbowRailRecoil {

    /** The rail brake is deliberately stretched across the full four-second item cooldown. */
    public static final int DURATION_TICKS = 80;

    private CrossbowRailRecoil() {}

    /** Fraction of the authored rail deployment stroke to retract at each client tick. */
    public static float retraction(int tick) {
        if (tick <= 0 || tick > DURATION_TICKS) return 0.0F;
        if (tick == 1) return 0.36F;
        if (tick <= 32) return 0.50F;
        float progress = (tick - 32.0F) / (DURATION_TICKS - 32.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        return 0.50F * (1.0F - eased);
    }

    /** Particle thrust is emitted while the rail is held at its half-stroke brake position. */
    public static boolean isJetPhase(int tick) {
        return tick >= 4 && tick <= 32;
    }

}
