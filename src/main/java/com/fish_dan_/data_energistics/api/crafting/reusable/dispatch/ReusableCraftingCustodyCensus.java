package com.fish_dan_.data_energistics.api.crafting.reusable.dispatch;

import java.util.List;
import java.util.UUID;

/**
 * Immutable, owner-filtered custody evidence from one currently visible provider. Completeness describes only
 * that provider's current executor scope, never unloaded chunks, disconnected grids or the entire world.
 * The loaded epoch changes with the source instance; revision changes when its custody evidence or coverage
 * changes, not when a tool merely advances its damage. This metadata never authorizes inventory reconstruction.
 */
public record ReusableCraftingCustodyCensus(UUID loadedEpoch, long revision, boolean complete, List<Entry> sessions) {

    public ReusableCraftingCustodyCensus {
        if (revision < 0) {
            throw new IllegalArgumentException("Negative reusable custody census revision");
        }
        sessions = List.copyOf(sessions);
    }

    /** Closed acknowledgements remain evidence when a CPU restores an older snapshot without their history. */
    public record Entry(UUID sessionId, UUID jobId, String cpuOwner, String targetIdentity, long accepted,
                        boolean settlementAcknowledged) {

        public Entry {
            if (cpuOwner.isBlank() || targetIdentity.isBlank() || accepted <= 0) {
                throw new IllegalArgumentException("Reusable custody evidence requires exact ownership and accepted work");
            }
        }
    }
}
