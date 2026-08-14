package com.fish_dan_.data_energistics.common.trinity.autobuild;

import net.minecraft.core.BlockPos;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Tracks the runtime validation state of each independently gated Trinity structure.
 *
 * <p>
 * Persisted formation fields remain the last confirmed structural snapshot. This contract separates that durable
 * snapshot from runtime availability so an unloaded chunk can suspend capabilities without being recorded as damage.
 * </p>
 */
public interface TrinityStructureValidation {

    /** Identifies one independently validated part of the Trinity multiblock. */
    enum Structure {
        /** Main structure that owns storage and information exchange depots. */
        MAIN,
        /** Optional CPU child structure. */
        CPU,
        /** Optional crafting and pattern-core child structure. */
        CRAFTING
    }

    /** Describes whether a structure can currently expose its capabilities. */
    enum State {
        /** A validation pass has been requested but has not completed. */
        PENDING,
        /** The most recent complete validation pass succeeded. */
        VALID,
        /** Validation stopped at an unloaded world position and must resume later. */
        DEFERRED,
        /** The most recent complete validation pass found a real mismatch. */
        INVALID
    }

    /**
     * Immutable runtime status for one structure.
     *
     * @param state           current validation state
     * @param waitingPosition unloaded position awaited by a deferred validation
     */
    record Status(State state, @Nullable BlockPos waitingPosition) {}

    /**
     * Returns the current status of one structure.
     *
     * @param structure structure to inspect
     * @return immutable validation status
     */
    Status status(Structure structure);

    /**
     * Tests whether one structure may currently publish its capability.
     *
     * @param structure structure to inspect
     * @return whether its last complete validation succeeded
     */
    boolean isValid(Structure structure);

    /**
     * Marks one structure for a fresh validation pass.
     *
     * @param structure structure whose world state changed
     */
    void markPending(Structure structure);

    /**
     * Records a successful complete validation pass.
     *
     * @param structure validated structure
     */
    void markValid(Structure structure);

    /**
     * Records a definitive mismatch after every required world position was available.
     *
     * @param structure invalid structure
     */
    void markInvalid(Structure structure);

    /**
     * Defers a failed validation only when MDLib reported {@code mdlib:unloaded} or the tracking world view observed
     * an unloaded position that a fallback diagnostic omitted.
     *
     * @param structure                structure being validated
     * @param diagnostic               matcher diagnostic, when retained by the matcher
     * @param observedUnloadedPosition first unloaded position observed by the tracking world view
     * @return whether the failure was classified as an unloaded-world deferral
     */
    boolean deferIfUnloaded(Structure structure,
                            @Nullable PatternDiagnostic diagnostic,
                            @Nullable BlockPos observedUnloadedPosition);

    /**
     * Polls only the stored waiting position and schedules one retry when it becomes loaded.
     *
     * @param structure structure whose deferred position should be checked
     * @param isLoaded  direct world-loaded predicate
     * @return whether the structure moved from deferred to pending
     */
    boolean resumeIfLoaded(Structure structure, Predicate<BlockPos> isLoaded);

    /** Resets every structure to an unvalidated runtime state without changing persisted formation snapshots. */
    void reset();
}
