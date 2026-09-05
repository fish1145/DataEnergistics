package com.fish_dan_.data_energistics.common.solar.energy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;

/**
 * Stable AE node-service port for one solar panel's membership in a connected array.
 *
 * <p>
 * The port is intended to replace the powered block entity's default {@link IAEPowerStorage} service while its
 * managed node is still being constructed. Its identity then remains stable for the node lifetime while the membership
 * resolves the current array. The storage is deliberately private to the node: AE's energy service must not register
 * every panel as a public battery and count the shared capacity once per panel.
 * </p>
 *
 * <p>
 * This adapter only exposes the array's private energy buffer. It does not create node connections, expose additional
 * sides, consume channels, or carry channels between panels.
 * </p>
 */
public final class SolarPanelArrayPort implements IAEPowerStorage {

    private final SolarPanelArray.Membership membership;

    /**
     * Creates a stable service adapter for the supplied array membership.
     *
     * @param membership lifecycle-stable handle that resolves the panel's current array
     */
    public SolarPanelArrayPort(SolarPanelArray.Membership membership) {
        this.membership = membership;
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        return amount - this.membership.insert(amount, mode);
    }

    @Override
    public double getAEMaxPower() {
        return this.membership.snapshot().capacity();
    }

    @Override
    public double getAECurrentPower() {
        return this.membership.snapshot().stored();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return false;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        double convertedAmount = multiplier.multiply(amount);
        double extracted = this.membership.extract(convertedAmount, mode);
        return multiplier.divide(extracted);
    }
}
