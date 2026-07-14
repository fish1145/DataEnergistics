package com.fish_dan_.data_energistics.accessor;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.jetbrains.annotations.Nullable;

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

    /** Adds an input remainder to AE2's persistent send list. */
    void dataEnergistics$invokeAddToSendList(AEKey what, long amount);

    /** Immediately retries AE2's pending send list against its fixed target side. */
    boolean dataEnergistics$invokeSendStacksOut();

    /** Applies AE2's crafting-lock transition for one accepted physical submission. */
    void dataEnergistics$invokeOnPushPatternSuccess(IPatternDetails patternDetails);
}
