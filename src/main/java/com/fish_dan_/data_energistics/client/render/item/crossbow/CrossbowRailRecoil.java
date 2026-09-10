package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailRecovery;

/** A fast half-stroke retraction and a controlled return without overshooting. */
public final class CrossbowRailRecoil {

    public static final int DURATION_TICKS = RailRecovery.DURATION_TICKS;
    public static final int BOW_DURATION_TICKS = 12;

    private CrossbowRailRecoil() {}

    /** Fraction of the authored rail deployment stroke to retract at each client tick. */
    public static float retraction(int tick) {
        return retraction(tick, DURATION_TICKS);
    }

    /** Two ticks braking and twelve returning; the heavy core stretches both phases threefold. */
    public static float retraction(int tick, int duration) {
        return RailRecovery.retraction(tick, duration, 0);
    }

    /** The ordinary crossbow keeps its original short recoil. */
    public static float bowRetraction(int tick) {
        return switch (tick) {
            case 1 -> 0.36F;
            case 2, 3, 4 -> 0.50F;
            case 5 -> 0.47F;
            case 6 -> 0.40F;
            case 7 -> 0.31F;
            case 8 -> 0.22F;
            case 9 -> 0.13F;
            case 10 -> 0.06F;
            case 11 -> 0.016F;
            default -> 0.0F;
        };
    }
}
