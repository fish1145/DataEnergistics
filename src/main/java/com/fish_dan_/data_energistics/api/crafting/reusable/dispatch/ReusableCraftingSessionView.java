package com.fish_dan_.data_energistics.api.crafting.reusable.dispatch;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;

import appeng.api.stacks.GenericStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable server observation. Held resources belong only to this owner/target, never to global AE availability. */
public record ReusableCraftingSessionView(UUID sessionId, UUID jobId, String cpuOwner, String targetIdentity,
                                          State state, long revision, long accepted, long completed, long cancelled,
                                          List<SlotStack> heldTools, Optional<String> failure) {

    public ReusableCraftingSessionView {
        heldTools = List.copyOf(heldTools);
        if (revision < 0L || accepted < 0L || completed < 0L || cancelled < 0L ||
                completed > accepted || cancelled > accepted - completed) {
            throw new IllegalArgumentException("Reusable session counters are inconsistent");
        }
    }

    /** Offline/unreachable is absence of an observation, not a transition to CLOSED. */
    public enum State {
        OPEN,
        CLOSING,
        FAULTED,
        RETURN_PENDING,
        CLOSED
    }

    /** Exact sequence progress used to reconcile a repeated append without repeating work accounting. */
    public record AppendReceipt(long sequence, long accepted, long completed, long cancelled) {

        public AppendReceipt {
            if (sequence < 0L || accepted <= 0L || completed < 0L || cancelled < 0L ||
                    completed > accepted || cancelled > accepted - completed) {
                throw new IllegalArgumentException("Reusable append receipt is inconsistent");
            }
        }
    }

    /**
     * Directed actual-asset return. These values describe the sender's authoritative outbox, not permission to
     * synthesize items. Machine-owned units are released separately; exhausted units are explicitly accounted for.
     */
    public record Settlement(UUID sessionId, UUID jobId, String cpuOwner, String targetIdentity, long sequence,
                             List<GenericStack> returnedAssets, List<SlotStack> releasedMachineTools,
                             long exhaustedTools, List<AppendReceipt> receipts,
                             Optional<String> failure) {

        public Settlement {
            if (sequence < 0L || exhaustedTools < 0L) {
                throw new IllegalArgumentException("Reusable settlement counters must not be negative");
            }
            returnedAssets = List.copyOf(returnedAssets);
            releasedMachineTools = List.copyOf(releasedMachineTools);
            receipts = List.copyOf(receipts);
            for (GenericStack stack : returnedAssets) {
                if (stack.amount() <= 0L) {
                    throw new IllegalArgumentException("Returned physical assets must have positive quantities");
                }
            }
        }
    }
}
