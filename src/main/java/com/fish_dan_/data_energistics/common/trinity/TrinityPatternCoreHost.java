package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Transient ownership contract between one formed Trinity crafting structure and its mounted pattern cores.
 *
 * <p>
 * The binding is deliberately not persisted: the host rebuilds it from a successful structure scan. It gives a
 * mounted core a direct path to the host's elected AE lease without turning the core into a multiblock compartment or
 * relying on a world scan.
 */
public interface TrinityPatternCoreHost {

    /**
     * Immutable authority token captured from one published catalog range when a core is bound.
     *
     * <p>
     * The core retains this token until the host confirms release. In particular, {@code coreId} is the identity from
     * the published range, not a value read later from a core that may already have loaded replacement NBT.
     * </p>
     *
     * @param hostId         stable identity of the host that published the range
     * @param layoutRevision exact catalog generation that owns the range
     * @param coreId         persistent core identity captured by that generation
     * @param mountPosition  physical position captured by that generation
     * @param blockCapacity  physical capacity captured by that generation
     */
    record PatternCoreBinding(UUID hostId,
                              long layoutRevision,
                              UUID coreId,
                              BlockPos mountPosition,
                              int blockCapacity) {

        /** Validates immutable range identity before it is retained by a movable core. */
        public PatternCoreBinding {
            if (layoutRevision < 0L || blockCapacity <= 0) {
                throw new IllegalArgumentException("A Trinity pattern core binding requires a valid layout range");
            }
            mountPosition = mountPosition.immutable();
        }
    }

    /**
     * Exact release request emitted by a core that is leaving its bound structure.
     *
     * @param core    exact physical core instance issuing the release
     * @param binding immutable authority token captured when that instance was bound
     */
    record PatternCoreReleaseRequest(TrinityPatternCore core, PatternCoreBinding binding) {}

    /** Result of a host-authoritative pattern-core release request. */
    enum PatternCoreReleaseResult {

        /** The host has locked the catalog and completed the requested release. */
        REVOKED,
        /** The requested generation is already inactive, so no further host cleanup is required. */
        ALREADY_REVOKED,
        /** The host locked publication but must finish local release recovery before confirming. */
        RETRY_REQUIRED,
        /** The request belongs to another host or an obsolete catalog generation. */
        STALE_REQUEST;

        /** @return whether the requesting core may clear this exact local binding */
        public boolean confirmsRelease() {
            return this == REVOKED || this == ALREADY_REVOKED;
        }
    }

    /**
     * Verifies that one exact core instance and authority token still belong to the host's active catalog.
     *
     * @param core    physical core requesting validation
     * @param binding immutable catalog range captured by that core at bind time
     * @return whether the current catalog still owns that exact binding
     */
    boolean isPatternCoreMounted(TrinityPatternCore core, PatternCoreBinding binding);

    /**
     * Refunds one mounted core through the host's elected AE storage, then the player inventory and world fallback.
     *
     * @param core    exact mounted core whose queued state is being returned
     * @param binding immutable catalog range that currently authorizes this request
     * @param player  player requesting the operation
     * @return whether refundable queued state was committed and delivered
     */
    boolean tryRefundPatternCore(TrinityPatternCore core, PatternCoreBinding binding, Player player);

    /**
     * Delivers one typed mutation from an exact mounted core so the host can update only the affected runtime index.
     *
     * @param core    exact mounted core that changed
     * @param binding immutable catalog range that currently authorizes this callback
     * @param change  physical slot and changed state surface
     */
    void onPatternCoreChanged(TrinityPatternCore core, PatternCoreBinding binding, TrinityPatternSlot.Change change);

    /**
     * Withdraws publication when a bound core unloads or is removed before the next structure scan.
     *
     * @param request exact core and catalog generation requesting release
     * @return host confirmation, retry state, or stale-request result
     */
    PatternCoreReleaseResult onPatternCoreUnavailable(PatternCoreReleaseRequest request);
}
