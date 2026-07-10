package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Aggregates every pattern processing core mounted in one Trinity crafting structure.
 *
 * <p>
 * The catalog owns stable route publication and dispatch validation so the AE provider does not depend on concrete
 * block entity types.
 * </p>
 */
public interface TrinityPatternCatalog {

    /**
     * Describes one core found during a host structure scan.
     *
     * @param position      world position used for deterministic ordering and diagnostics
     * @param blockCapacity capacity declared by the core block
     * @param core          persistent core state supplied by its block entity
     */
    record CoreMount(BlockPos position, int blockCapacity, TrinityPatternCore core) {

        /** Validates a scanned mount before it can enter a catalog. */
        public CoreMount {
            if (blockCapacity <= 0) {
                throw new IllegalArgumentException("A Trinity pattern core mount requires a positive block capacity");
            }
            position = position.immutable();
        }
    }

    /**
     * Reports whether a scanned structure can publish patterns.
     *
     * @param valid           true when every scanned core passed validation
     * @param changed         true when the mounted cores or their published patterns changed
     * @param failurePosition offending core position, or {@code null} after a successful rebuild
     * @param failureReason   human-readable diagnostic, empty after a successful rebuild
     */
    record RebuildResult(boolean valid, boolean changed, @Nullable BlockPos failurePosition, String failureReason) {

        /** Validates that success and failure diagnostics cannot contradict each other. */
        public RebuildResult {
            if (valid && (failurePosition != null || !failureReason.isEmpty())) {
                throw new IllegalArgumentException("A successful Trinity pattern catalog rebuild cannot have a failure");
            }
            if (!valid && failureReason.isBlank()) {
                throw new IllegalArgumentException("A failed Trinity pattern catalog rebuild requires a diagnostic");
            }
            if (failurePosition != null) {
                failurePosition = failurePosition.immutable();
            }
        }
    }

    /**
     * @return stable UUID of the host that owns every route in this catalog
     */
    UUID hostId();

    /**
     * Replaces the mounted-core snapshot after validating capacities, positions, and persistent core identities.
     *
     * @param mounts cores found in the crafting child structure
     * @return rebuild status and any formation-blocking diagnostic
     */
    RebuildResult rebuild(List<CoreMount> mounts);

    /**
     * Refreshes only core pattern caches whose runtime revision changed.
     *
     * @return true when the published AE pattern list changed or needs republishing
     */
    boolean refreshChangedPatterns();

    /**
     * @return immutable routed AE patterns in stable core-position and slot order
     */
    List<IPatternDetails> getAvailablePatterns();

    /**
     * Validates and enqueues one routed AE dispatch without partially consuming its input counters.
     *
     * @param patternDetails routed pattern selected by AE2
     * @param inputHolder    extracted pattern inputs
     * @param queuedTick     server tick on which the dispatch arrived
     * @return true only when a complete 3 by 3 snapshot was enqueued in the exact routed slot
     */
    boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, long queuedTick);

    /**
     * @return immutable mounted cores in stable world-position order
     */
    List<CoreMount> mountedCores();

    /**
     * @return whether any mounted core owns queued inputs or pending outputs
     */
    boolean hasWork();

    /** Clears all mounted cores and published patterns after structure invalidation. */
    void clear();
}
