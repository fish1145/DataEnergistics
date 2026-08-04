package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

/**
 * Thread-safe provider shard and machine-target reservation boundary for immutable proposal planning.
 */
public interface ProviderShardDispatcher {

    /**
     * Creates a dispatcher with a fixed shard mapping for its complete runtime lifetime.
     *
     * @param shardCount positive immutable shard count
     * @return independent dispatcher
     */
    static ProviderShardDispatcher create(int shardCount) {
        return new ProviderShardDispatcherImpl(shardCount);
    }

    /**
     * Selects one provider target and atomically reserves its currently observed capacity.
     *
     * @param request         immutable worker request
     * @param planner         pure capacity slice planner
     * @param providerQuantum maximum simultaneous proposals reserving one provider
     * @return reserved selection or explicit no-capacity result
     */
    Result selectAndReserve(
                            CraftingDispatchProposalRequest request,
                            CapacitySlicePlanner planner,
                            int providerQuantum);

    /**
     * Reservation-bearing shard result.
     */
    sealed interface Result permits Reserved, NoCapacity {}

    /**
     * @param target        selected original immutable snapshot
     * @param logicalCrafts positive capacity reserved for this proposal
     * @param nextCursor    fairness cursor to adopt after consumption
     * @param reservation   idempotent reservation release handle
     */
    record Reserved(
                    ProviderCapacitySnapshot target,
                    long logicalCrafts,
                    int nextCursor,
                    Reservation reservation)
            implements Result {

        public Reserved {
            if (target == null) {
                throw new IllegalArgumentException("Reserved provider shard target must not be null");
            }
            if (logicalCrafts <= 0L) {
                throw new IllegalArgumentException("Reserved provider shard count must be positive");
            }
            if (nextCursor < 0) {
                throw new IllegalArgumentException("Reserved provider shard cursor must not be negative");
            }
            if (reservation == null) {
                throw new IllegalArgumentException("Provider shard reservation must not be null");
            }
        }
    }

    /**
     * No candidate retained unreserved safe capacity.
     */
    enum NoCapacity implements Result {
        INSTANCE
    }

    /**
     * Transient provider and optional machine-target reservation owned by one proposal ticket.
     */
    interface Reservation extends AutoCloseable {

        /**
         * Releases capacity exactly once.
         */
        @Override
        void close();
    }
}
