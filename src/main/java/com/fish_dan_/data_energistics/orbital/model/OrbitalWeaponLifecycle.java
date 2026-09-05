package com.fish_dan_.data_energistics.orbital.model;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

/**
 * Immutable deployment state carried by an orbital weapon record.
 *
 * <p>
 * The state machine is deliberately kept at the reserve boundary: charging, maintenance and attack escrow all
 * publish a complete record, so consumers never need to infer deployment from two mutable resource counters.
 * </p>
 */
public record OrbitalWeaponLifecycle(
                                     OrbitalWeaponLifecycleState state,
                                     int graceTicksRemaining,
                                     int redeploymentTicksRemaining) {

    public OrbitalWeaponLifecycle {
        if (graceTicksRemaining < 0 || redeploymentTicksRemaining < 0) {
            throw new IllegalArgumentException("Orbital lifecycle state is invalid");
        }
        switch (state) {
            case DORMANT, DEPLOYED -> {
                if (graceTicksRemaining != 0 || redeploymentTicksRemaining != 0) {
                    throw new IllegalArgumentException("Stable lifecycle state cannot carry a countdown");
                }
            }
            case RESERVE_GRACE -> {
                if (graceTicksRemaining == 0 || redeploymentTicksRemaining != 0) {
                    throw new IllegalArgumentException("Reserve grace has invalid countdowns");
                }
            }
            case REDEPLOYING -> {
                if (redeploymentTicksRemaining == 0) {
                    throw new IllegalArgumentException("Redeployment must have at least one remaining tick");
                }
            }
        }
    }

    public static OrbitalWeaponLifecycle dormant() {
        return new OrbitalWeaponLifecycle(OrbitalWeaponLifecycleState.DORMANT, 0, 0);
    }

    public static OrbitalWeaponLifecycle deployed() {
        return new OrbitalWeaponLifecycle(OrbitalWeaponLifecycleState.DEPLOYED, 0, 0);
    }

    public static OrbitalWeaponLifecycle reserveGrace(int graceTicks) {
        return graceTicks <= 0 ? dormant() : new OrbitalWeaponLifecycle(OrbitalWeaponLifecycleState.RESERVE_GRACE, graceTicks, 0);
    }

    public static OrbitalWeaponLifecycle redeploying(int redeploymentTicks) {
        return redeploymentTicks <= 0 ? dormant() : new OrbitalWeaponLifecycle(OrbitalWeaponLifecycleState.REDEPLOYING, 0, redeploymentTicks);
    }

    private static OrbitalWeaponLifecycle redeployingWithGrace(
                                                               int redeploymentTicks,
                                                               int graceTicks) {
        if (redeploymentTicks <= 0) {
            return dormant();
        }
        return graceTicks <= 0 ? redeploying(redeploymentTicks) : new OrbitalWeaponLifecycle(
                OrbitalWeaponLifecycleState.REDEPLOYING,
                graceTicks,
                redeploymentTicks);
    }

    /** Returns whether this record may confirm a new attack. */
    public boolean allowsNewAttacks() {
        return this.state == OrbitalWeaponLifecycleState.DEPLOYED;
    }

    /** Returns whether a primary projection still exists and therefore requires an anchor and maintenance. */
    public boolean hasProjection() {
        return this.state != OrbitalWeaponLifecycleState.DORMANT;
    }

    /** Returns whether an anchor teardown/rebuild countdown is active. */
    public boolean isRedeploying() {
        return this.state == OrbitalWeaponLifecycleState.REDEPLOYING;
    }

    /** Returns whether this projection is consuming its frozen reserve-grace countdown. */
    public boolean hasReserveGrace() {
        return this.graceTicksRemaining > 0;
    }

    /** Returns whether the still-present projection must pay both configured upkeep resources. */
    public boolean requiresMaintenance() {
        return hasProjection();
    }

    /**
     * Reconciles deployment after one reserve tick. This is the only place that starts, resumes or expires a
     * deployment; callers pass the already-normalized reserve and immutable configuration snapshot.
     */
    public OrbitalWeaponLifecycle reconcile(
                                            OrbitalEnergyReserve reserve,
                                            DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        boolean thresholdReached = reserve.meetsDeploymentThreshold(settings);
        return switch (this.state) {
            case DORMANT -> thresholdReached ? deployed() : this;
            case DEPLOYED -> reserve.hasZeroResource() ? reserveGrace(settings.reserveGraceTicks) : this;
            case RESERVE_GRACE -> thresholdReached ? deployed() : (this.graceTicksRemaining == 1 ? dormant() : reserveGrace(this.graceTicksRemaining - 1));
            case REDEPLOYING -> reconcileRedeployment(reserve, thresholdReached, settings.reserveGraceTicks);
        };
    }

    private OrbitalWeaponLifecycle reconcileRedeployment(
                                                         OrbitalEnergyReserve reserve,
                                                         boolean thresholdReached,
                                                         int configuredGraceTicks) {
        if (hasReserveGrace()) {
            if (thresholdReached) {
                return redeploying(this.redeploymentTicksRemaining);
            }
            return this.graceTicksRemaining == 1 ? dormant() : redeployingWithGrace(
                    this.redeploymentTicksRemaining,
                    this.graceTicksRemaining - 1);
        }
        if (reserve.hasZeroResource()) {
            return configuredGraceTicks <= 0 ? dormant() : redeployingWithGrace(this.redeploymentTicksRemaining, configuredGraceTicks);
        }
        return this.redeploymentTicksRemaining == 1 ? deployed() : redeploying(this.redeploymentTicksRemaining - 1);
    }

    /** Immediately enters grace when an attack escrow consumes the last unit of either reserve. */
    public OrbitalWeaponLifecycle afterDebit(OrbitalEnergyReserve reserve, DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        if (this.state == OrbitalWeaponLifecycleState.DEPLOYED && reserve.hasZeroResource()) {
            return reserveGrace(settings.reserveGraceTicks);
        }
        return this;
    }

    /** Starts a server-authoritative teardown/rebuild window after the primary anchor changes. */
    public OrbitalWeaponLifecycle beginRedeployment(int redeploymentTicks) {
        if (redeploymentTicks <= 0 || !hasProjection()) {
            return this;
        }
        return redeployingWithGrace(redeploymentTicks, this.graceTicksRemaining);
    }
}
