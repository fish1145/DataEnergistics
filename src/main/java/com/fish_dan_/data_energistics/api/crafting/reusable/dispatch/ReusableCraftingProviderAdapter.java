package com.fish_dan_.data_energistics.api.crafting.reusable.dispatch;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;

import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional extension for providers that can persist, resume and close reusable-input sessions. A factory may return
 * this alongside its existing counted behavior. Legacy methods retain their original multiplication semantics.
 * All calls occur on the logical server thread. Adapters must persist assets outside transient adapter instances.
 */
public interface ReusableCraftingProviderAdapter extends CountedCraftingProviderAdapter {

    /** @return immutable concrete targets; discovery is read-only and must not retain the live query references */
    List<Target> reusableTargets(IPatternDetails pattern, IActionSource source, ServerLevel level);

    /** @return one read-only prepared open/append admission, or null when the exact contract cannot be accepted */
    @Nullable
    ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request);

    /** @return current immutable state, or empty when the owning executor cannot currently be reached */
    Optional<ReusableCraftingSessionView> reusableSession(UUID sessionId);

    /**
     * Read-only evidence for this exact CPU owner, including acknowledged CLOSED history. Called on the server
     * thread even when no pattern is currently published. Incomplete visible coverage must be explicit; an empty
     * result never proves the absence of custody in unloaded or disconnected providers. No claim is adopted here.
     */
    ReusableCraftingCustodyCensus reusableCustody(String cpuOwner);

    /** @return the accepted sequence's durable result, including completed/cancelled work, without modifying it */
    Optional<AppendReceipt> reusableReceipt(UUID sessionId, long sequence);

    /** Stops further appends and requests safe-point closure. Repeated requests must be harmless. */
    void closeReusableSession(UUID sessionId);

    /**
     * Signals one explicit, runnable contender for an already occupied concrete native target. Implementations
     * validate the current recipe/mode/rule binding and reject the owner itself or unrelated targets. A true result
     * latches the owner's first request time; repeated signals cannot extend its twenty-tick safe-point deadline.
     * This transfers no inputs and consumes no crafting allowance. Ordinary preparation remains read-only.
     */
    boolean requestReusableYield(ReusableCraftingRequest contender);

    /**
     * Transfers the authoritative directed outbox through the receiver. A false result leaves the outbox intact;
     * a true result records its acknowledgement before the outbox is cleared. Never route tools via global AEKey
     * waiting counters. An unreachable owner is not permission to invent a refund.
     *
     * @return true after an acknowledged settlement, false while unreachable or not ready
     */
    boolean settleReusableSession(UUID sessionId, ReturnReceiver receiver);

    /** Receiver persists deduplication and actual accepted assets together; replay only acknowledges. */
    @FunctionalInterface
    interface ReturnReceiver {

        /**
         * @return true after accepting or recognizing the exact receipt; false without mutation on unknown ownership
         */
        boolean receive(Settlement settlement);
    }
}
