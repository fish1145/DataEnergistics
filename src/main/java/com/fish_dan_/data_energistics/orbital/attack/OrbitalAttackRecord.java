package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.orbital.attack.work.OrbitalAttackWorkState;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

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
                                  OrbitalAttackGeometry geometry,
                                  long configurationRevision,
                                  int warningTicksRemaining,
                                  long workCursor,
                                  OrbitalAttackWorkState workState,
                                  @Nullable String faultReason,
                                  @Nullable UUID payloadEntityId,
                                  boolean payloadArrived,
                                  boolean impactApplied,
                                  int cooldownTicksRemaining,
                                  int cooldownDurationTicks,
                                  long celestialEscrow,
                                  long aeEscrow,
                                  Set<UUID> damageExemptions) {

    public OrbitalAttackRecord {
        target = target.immutable();
        damageExemptions = Set.copyOf(damageExemptions);
        if (payloadArrived && payloadEntityId == null) {
            throw new IllegalArgumentException("A digital payload cannot arrive without an entity identity");
        }
        if (geometry.mode() != mode) {
            throw new IllegalArgumentException("Orbital attack geometry does not match its mode");
        }
        if (configurationRevision < 0L || warningTicksRemaining < 0 || workCursor < 0L || cooldownTicksRemaining < 0 || cooldownDurationTicks <= 0 || celestialEscrow < 0L || aeEscrow < 0L) {
            throw new IllegalArgumentException("Orbital attack state must not contain negative values");
        }
        if (phase == OrbitalAttackPhase.RESERVED_WARNING && warningTicksRemaining == 0) {
            throw new IllegalArgumentException("A warning attack must have remaining warning ticks");
        }
        if (phase == OrbitalAttackPhase.COOLDOWN && cooldownTicksRemaining == 0) {
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
                                              OrbitalAttackGeometry geometry,
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
                geometry,
                configurationRevision,
                warningTicks,
                0L,
                OrbitalAttackWorkState.INACTIVE,
                null,
                null,
                false,
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
                this.geometry,
                this.configurationRevision,
                remaining,
                this.workCursor,
                this.workState,
                this.faultReason,
                this.payloadEntityId,
                this.payloadArrived,
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
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                OrbitalAttackWorkState.INACTIVE,
                null,
                this.payloadEntityId,
                this.payloadArrived,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord withWorkCursor(long nextCursor) {
        return withWork(nextCursor, OrbitalAttackWorkState.WORKING);
    }

    /** Updates the resumable cursor and its persisted scheduler boundary without altering frozen attack geometry. */
    public OrbitalAttackRecord withWork(long nextCursor, OrbitalAttackWorkState nextWorkState) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.DELIVERY,
                this.dimensionId,
                this.target,
                this.geometry,
                this.configurationRevision,
                0,
                nextCursor,
                nextWorkState,
                this.faultReason,
                this.payloadEntityId,
                this.payloadArrived,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    public OrbitalAttackRecord withWorkState(OrbitalAttackWorkState nextWorkState) {
        return withWork(this.workCursor, nextWorkState);
    }

    public OrbitalAttackRecord cooldown(int remainingTicks) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.COOLDOWN,
                this.dimensionId,
                this.target,
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                OrbitalAttackWorkState.INACTIVE,
                null,
                this.payloadEntityId,
                this.payloadArrived,
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
        return faulted(this.faultReason);
    }

    /** Moves the task into an administrator-visible terminal state with one stable diagnostic reason. */
    public OrbitalAttackRecord faulted(@Nullable String reason) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.FAULTED,
                this.dimensionId,
                this.target,
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                OrbitalAttackWorkState.INACTIVE,
                reason,
                this.payloadEntityId,
                this.payloadArrived,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    /**
     * Marks a committed attack as aborted without refunding its already debited escrow.
     * The scheduler advances this diagnostic phase into the configured cooldown on its next tick.
     */
    public OrbitalAttackRecord aborted() {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.ABORTED,
                this.dimensionId,
                this.target,
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                OrbitalAttackWorkState.INACTIVE,
                this.faultReason,
                this.payloadEntityId,
                this.payloadArrived,
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
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                this.workState,
                this.faultReason,
                this.payloadEntityId,
                this.payloadArrived,
                true,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    /** Records the UUID of the currently flying payload or materialized annihilator. */
    public OrbitalAttackRecord withPayloadEntity(UUID entityId, boolean arrived) {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.DELIVERY,
                this.dimensionId,
                this.target,
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                this.workState,
                this.faultReason,
                entityId,
                arrived,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }

    /** Returns a delivery record after the orbital payload has materialized as the fuse entity. */
    public OrbitalAttackRecord markDigitalPayloadArrived(UUID entityId) {
        return withPayloadEntity(entityId, true);
    }

    /** Re-enters delivery after an administrator retry while preserving the deterministic cursor and escrow. */
    public OrbitalAttackRecord retryAfterFault() {
        return new OrbitalAttackRecord(
                this.attackId,
                this.weaponId,
                this.mode,
                OrbitalAttackPhase.DELIVERY,
                this.dimensionId,
                this.target,
                this.geometry,
                this.configurationRevision,
                0,
                this.workCursor,
                this.mode == OrbitalAttackMode.DIGITAL_ANNIHILATION ? OrbitalAttackWorkState.INACTIVE : OrbitalAttackWorkState.WAITING_FOR_CHUNK,
                null,
                null,
                false,
                this.impactApplied,
                0,
                this.cooldownDurationTicks,
                this.celestialEscrow,
                this.aeEscrow,
                this.damageExemptions);
    }
}
