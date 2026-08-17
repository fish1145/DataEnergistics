package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable server-owned state for one confirmed attack. The escrow and exemption set are frozen at confirmation so
 * later reserve charging or authorization changes cannot alter a running attack.
 */
public record OrbitalAttackRecord(
                                  UUID attackId,
                                  UUID weaponId,
                                  OrbitalAttackMode mode,
                                  OrbitalAttackPhase phase,
                                  ResourceLocation dimensionId,
                                  BlockPos target,
                                  long configurationRevision,
                                  int warningTicksRemaining,
                                  long workCursor,
                                  boolean impactApplied,
                                  int cooldownTicksRemaining,
                                  int cooldownDurationTicks,
                                  long celestialEscrow,
                                  long aeEscrow,
                                  Set<UUID> damageExemptions) {

    public OrbitalAttackRecord {
        target = target.immutable();
        damageExemptions = Set.copyOf(damageExemptions);
        if (configurationRevision < 0L || warningTicksRemaining < 0 || workCursor < 0L || cooldownTicksRemaining < 0 || cooldownDurationTicks <= 0 || celestialEscrow < 0L || aeEscrow < 0L) {
            throw new IllegalArgumentException("Orbital attack state must not contain negative values");
        }
        if (phase == OrbitalAttackPhase.RESERVED_WARNING && warningTicksRemaining <= 0) {
            throw new IllegalArgumentException("A warning attack must have remaining warning ticks");
        }
        if (phase == OrbitalAttackPhase.COOLDOWN && cooldownTicksRemaining <= 0) {
            throw new IllegalArgumentException("A cooldown attack must have remaining cooldown ticks");
        }
    }

    /**
     * Creates the warning state after the caller has atomically debited both resource reserves.
     */
    public static OrbitalAttackRecord warning(
                                              UUID attackId,
                                              UUID weaponId,
                                              OrbitalAttackMode mode,
                                              ResourceLocation dimensionId,
                                              BlockPos target,
                                              long configurationRevision,
                                              int warningTicks,
                                              OrbitalAttackCost cost,
                                              Set<UUID> damageExemptions) {
        return new OrbitalAttackRecord(
                attackId,
                weaponId,
                mode,
                OrbitalAttackPhase.RESERVED_WARNING,
                dimensionId,
                target,
                configurationRevision,
                warningTicks,
                0L,
                false,
                0,
                cost.cooldownTicks(),
                cost.celestialEnergy(),
                cost.aeEnergy(),
                damageExemptions);
    }

    public OrbitalAttackRecord withWarningTicks(int remaining) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.RESERVED_WARNING,
                this.dimensionId,
                this.target,
                this.configurationRevision,
                remaining,
                this.workCursor,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord committed() {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.COMMITTED,
                this.dimensionId,
                this.target,
                this.configurationRevision,
                0,
                this.workCursor,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord withWorkCursor(long nextCursor) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.DELIVERY,
                this.dimensionId,
                this.target,
                this.configurationRevision,
                0,
                nextCursor,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord cooldown(int remainingTicks) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.COOLDOWN,
                this.dimensionId,
                this.target,
                this.configurationRevision,
                0,
                this.workCursor,
                this.impactApplied,
                remainingTicks,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord withCooldownTicks(int remaining) {
        return cooldown(remaining);
    }

    public OrbitalAttackRecord faulted() {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.FAULTED,
                this.dimensionId,
                this.target,
                this.configurationRevision,
                0,
                this.workCursor,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord markImpactApplied() {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.DELIVERY,
                this.dimensionId,
                this.target,
                this.configurationRevision,
                0,
                this.workCursor,
                true,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }
}
