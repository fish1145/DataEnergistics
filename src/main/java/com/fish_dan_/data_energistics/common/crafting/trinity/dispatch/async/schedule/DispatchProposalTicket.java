package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;

/**
 * Server-thread handle for one outstanding asynchronous proposal calculation.
 *
 * <p>
 * Closing is idempotent and releases the worker/grid admission immediately. Tickets are transient and must never be
 * persisted.
 * </p>
 */
public interface DispatchProposalTicket extends AutoCloseable {

    /** @return immutable lease bound to this ticket */
    CraftingDispatchLease lease();

    /** @return current non-blocking calculation state */
    State state();

    /** Records that server-thread revalidation rejected this proposal as stale. */
    void recordStale();

    /** Releases executor admission and cancels unfinished calculation. */
    @Override
    void close();

    /** Non-blocking terminal states observed by the owning server thread. */
    sealed interface State permits Pending, Ready, NoCapacity, Failed, Cancelled {}

    /** Calculation remains queued or running. */
    enum Pending implements State {
        INSTANCE
    }

    /** @param proposal completed immutable proposal */
    record Ready(CraftingDispatchProposal proposal) implements State {

        public Ready {
            if (proposal == null) {
                throw new IllegalArgumentException("Ready dispatch proposal must not be null");
            }
        }
    }

    /** No candidate had a positive safe physical-call offer. */
    enum NoCapacity implements State {
        INSTANCE
    }

    /** @param cause unexpected pure-planning failure already logged by the scheduler */
    record Failed(RuntimeException cause) implements State {

        public Failed {
            if (cause == null) {
                throw new IllegalArgumentException("Dispatch proposal failure cause must not be null");
            }
        }
    }

    /** Ticket was closed before the server thread consumed a result. */
    enum Cancelled implements State {
        INSTANCE
    }
}
