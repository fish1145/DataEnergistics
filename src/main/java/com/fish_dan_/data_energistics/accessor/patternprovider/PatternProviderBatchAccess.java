package com.fish_dan_.data_energistics.accessor.patternprovider;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Stable access contract for reproducing AE2's ordinary external-inventory pattern dispatch.
 *
 * <p>
 * Counted batching needs the same target cache, round-robin cursor and success callbacks as
 * {@code PatternProviderLogic}. Keeping those operations behind this interface prevents business logic from depending
 * on a Mixin accessor type.
 * </p>
 */
public interface PatternProviderBatchAccess {

    /** Returns the host whose adjacent targets receive pattern inputs. */
    PatternProviderLogicHost dataEnergistics$getHost();

    /** Returns the managed node used to validate provider activity. */
    IManagedGridNode dataEnergistics$getMainNode();

    /** Returns the currently published patterns. */
    List<IPatternDetails> dataEnergistics$getPatterns();

    /** Returns the normalized input keys used by AE2 Blocking Mode. */
    Set<AEKey> dataEnergistics$getPatternInputs();

    /** Returns pending inputs that AE2 still needs to send to the selected target. */
    List<GenericStack> dataEnergistics$getSendList();

    /** Returns the current target round-robin cursor. */
    int dataEnergistics$getRoundRobinIndex();

    /** Replaces the target round-robin cursor after a successful submission. */
    void dataEnergistics$setRoundRobinIndex(int roundRobinIndex);

    /** Fixes the side used to flush any input remainder from the accepted batch. */
    void dataEnergistics$setSendDirection(Direction direction);

    /** Resolves the sides AE2 may use after filtering same-grid connections. */
    Set<Direction> dataEnergistics$invokeGetActiveSides();

    /** Resolves AE2's cached external-inventory adapter for one side. */
    @Nullable
    PatternProviderTarget dataEnergistics$invokeFindAdapter(Direction side);

    /**
     * Wakes the provider after a complete counted batch has been placed in its persistent send list.
     *
     * <p>
     * Counted dispatch installs the full list in one operation before transferring CPU ownership, so it cannot use
     * AE2's per-stack {@code addToSendList} helper to perform this wake-up.
     * </p>
     */
    default void dataEnergistics$alertPendingSendList() {
        this.dataEnergistics$getMainNode().ifPresent(
                (grid, node) -> grid.getTickManager().alertDevice(node));
    }

    /** Immediately retries AE2's pending send list against its fixed target side. */
    boolean dataEnergistics$invokeSendStacksOut();

    /** Applies AE2's crafting-lock transition for one accepted physical submission. */
    void dataEnergistics$invokeOnPushPatternSuccess(IPatternDetails patternDetails);
}
