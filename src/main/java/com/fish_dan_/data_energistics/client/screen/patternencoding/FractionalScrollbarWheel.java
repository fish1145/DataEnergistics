package com.fish_dan_.data_energistics.client.screen.patternencoding;

import appeng.client.Point;
import appeng.client.gui.widgets.Scrollbar;

/** Accumulates high-resolution wheel deltas before applying discrete AE2 scrollbar rows. */
final class FractionalScrollbarWheel {

    private double accumulatedDelta;

    void apply(Scrollbar scrollbar, Point mousePosition, double delta) {
        if (!Double.isFinite(delta) || delta == 0.0D) {
            reset();
            return;
        }
        if (this.accumulatedDelta != 0.0D && Math.signum(this.accumulatedDelta) != Math.signum(delta)) {
            this.accumulatedDelta = 0.0D;
        }
        this.accumulatedDelta += delta;
        int steps = (int) Math.floor(Math.abs(this.accumulatedDelta));
        if (steps == 0) {
            return;
        }
        double stepDelta = Math.copySign(1.0D, this.accumulatedDelta);
        for (int step = 0; step < steps; step++) {
            scrollbar.onMouseWheel(mousePosition, stepDelta);
        }
        this.accumulatedDelta -= stepDelta * steps;
    }

    void reset() {
        this.accumulatedDelta = 0.0D;
    }
}
