package com.fish_dan_.data_energistics.client.ui.orbital.state;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlDashboard;

import java.util.Objects;

/**
 * Client-only raw input: partially typed numbers survive navigation without becoming authorized fire-control drafts.
 */
public record OrbitalTerminalFormState(String dimension, String x, String z, String y,
                                       OrbitalAttackMode mode, OrbitalTargetYMode yMode,
                                       int radius, OrbitalDirectedEnergyDepth depth) {

    public static OrbitalTerminalFormState capture(OrbitalControlDashboard dashboard) {
        return new OrbitalTerminalFormState(dashboard.dimension.getRawText(), dashboard.targetX.getRawText(),
                dashboard.targetZ.getRawText(), dashboard.targetYValue.getRawText(), dashboard.mode.getValue(),
                Objects.requireNonNull(dashboard.targetYMode.getValue()), Objects.requireNonNull(dashboard.radius.getValue()),
                Objects.requireNonNull(dashboard.depth.getValue()));
    }

    public void apply(OrbitalControlDashboard dashboard) {
        dashboard.dimension.setText(dimension, false);
        dashboard.targetX.setText(x, false);
        dashboard.targetZ.setText(z, false);
        dashboard.targetYValue.setText(y, false);
        dashboard.mode.setSelected(mode, false);
        dashboard.targetYMode.setSelected(yMode, false);
        dashboard.radius.setSelected(radius, false);
        dashboard.depth.setSelected(depth, false);
    }
}
