package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

/**
 * Internal access to AE2 network storage that transfers from concrete finite mounts without routing extraction
 * through the aggregate inventory.
 */
public interface FiniteNetworkStorageAccess {

    /**
     * Returns a monotonically increasing revision for mount and unmount requests on this network storage.
     */
    long storageStructureRevision();

    /**
     * Transfers at most {@code amount} from concrete mounts in AE2's normal extraction order.
     *
     * <p>
     * Mounts reporting exactly {@link Integer#MAX_VALUE} or {@link Long#MAX_VALUE} for the requested key are
     * treated as infinite sources and skipped. A finite mount for the same key remains eligible.
     * </p>
     */
    FiniteTransferResult transferFinite(AEKey what,
                                        long amount,
                                        IActionSource source,
                                        FiniteTransferTarget target);

    /** Destination participating in the simulate-then-commit transfer transaction. */
    interface FiniteTransferTarget {

        /** Returns how much of the offered amount can currently be accepted. */
        long simulateInsert(AEKey what, long amount);

        /** Commits insertion and returns the amount actually accepted. */
        long insert(AEKey what, long amount);
    }

    /**
     * Exact operation totals. A result is consistent only when every planned extraction reached the target or was
     * restored to its original concrete source.
     */
    record FiniteTransferResult(long transferred,
                                long plannedSourceExtraction,
                                long sourceExtracted,
                                long targetAccepted,
                                long sourceRollback,
                                long sourceRollbackAccepted,
                                int skippedInfiniteSources,
                                boolean retrySuggested) {

        public boolean consistent() {
            return this.plannedSourceExtraction == this.sourceExtracted &&
                    this.sourceExtracted == this.targetAccepted + this.sourceRollback &&
                    this.sourceRollback == this.sourceRollbackAccepted &&
                    this.transferred == this.targetAccepted;
        }
    }
}
