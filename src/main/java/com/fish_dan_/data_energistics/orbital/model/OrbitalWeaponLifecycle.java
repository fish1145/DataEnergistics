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

    /** Compatibility constructor for records created before the redeployment countdown was persisted. */
    public OrbitalWeaponLifecycle(OrbitalWeaponLifecycleState state, int graceTicksRemaining) {
        this(state, graceTicksRemaining, 0);
    }

    public OrbitalWeaponLifecycle {
        if (graceTicksRemaining < 0 || redeploymentTicksRemaining < 0) {
            throw new IllegalArgumentException("Orbital lifecycle state is invalid");
        }
        if (state != OrbitalWeaponLifecycleState.RESERVE_GRACE && graceTicksRemaining != 0) {
            throw new IllegalArgumentException("Only reserve grace may carry a grace countdown");
        }
        if (state != OrbitalWeaponLifecycleState.REDEPLOYING && redeploymentTicksRemaining != 0) {
            throw new IllegalArgumentException("Only redeployment may carry a rebuild countdown");
        }
        if (state == OrbitalWeaponLifecycleState.RESERVE_GRACE && graceTicksRemaining == 0) {
            throw new IllegalArgumentException("Reserve grace must have at least one remaining tick");
        }
        if (state == OrbitalWeaponLifecycleState.REDEPLOYING && redeploymentTicksRemaining == 0) {
            throw new IllegalArgumentException("Redeployment must have at least one remaining tick");
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

    /** Returns whether this record may confirm a new attack. */
    public boolean allowsNewAttacks() {
        return this.state == OrbitalWeaponLifecycleState.DEPLOYED;
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
            case DEPLOYED -> reserve.hasZeroResource() ? reserveGrace(settings.reserveGraceTicks()) : this;
            case RESERVE_GRACE -> thresholdReached ? deployed() : (this.graceTicksRemaining <= 1 ? dormant() : reserveGrace(this.graceTicksRemaining - 1));
            case REDEPLOYING -> this.redeploymentTicksRemaining <= 1 ? (thresholdReached ? deployed() : dormant()) : redeploying(this.redeploymentTicksRemaining - 1);
        };
    }

    /** Immediately enters grace when an attack escrow consumes the last unit of either reserve. */
    public OrbitalWeaponLifecycle afterDebit(OrbitalEnergyReserve reserve, DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        if (this.state == OrbitalWeaponLifecycleState.DEPLOYED && reserve.hasZeroResource()) {
            return reserveGrace(settings.reserveGraceTicks());
        }
        return this;
    }

    /** Starts a server-authoritative teardown/rebuild window after the primary anchor changes. */
    public OrbitalWeaponLifecycle beginRedeployment(int redeploymentTicks) {
        if (redeploymentTicks <= 0) {
            return this;
        }
        return redeploying(redeploymentTicks);
    }
}
