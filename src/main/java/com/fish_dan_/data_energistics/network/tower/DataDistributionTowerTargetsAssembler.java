package com.fish_dan_.data_energistics.network.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reassembles independently batched tower target revisions without exposing partial lists.
 *
 * <p>
 * State is isolated by container id. Within a container, a newer revision supersedes incomplete older work, old
 * revisions are ignored, and identical duplicate batches are idempotent.
 * </p>
 */
public final class DataDistributionTowerTargetsAssembler {

    /**
     * Latest assembly state for each container observed by this assembler.
     */
    private final Map<Integer, Assembly> assemblies = new HashMap<>();

    /**
     * Accepts one batch and publishes only when every batch in its revision is present.
     *
     * @param payload validated target batch
     * @return a complete snapshot exactly once for each revision, otherwise empty
     * @throws IllegalArgumentException when batches in the same revision disagree on metadata or content
     */
    public synchronized Optional<DataDistributionTowerTargetsSnapshot> accept(
                                                                              DataDistributionTowerTargetsPayload payload) {
        Assembly assembly = this.assemblies.get(payload.containerId());
        if (assembly != null && payload.revision() < assembly.revision) {
            return Optional.empty();
        }
        if (assembly == null || payload.revision() > assembly.revision) {
            assembly = new Assembly(payload.revision(), payload.batchCount(), payload.totalCount());
            this.assemblies.put(payload.containerId(), assembly);
        }

        return assembly.accept(payload, payload.containerId());
    }

    /**
     * Drops all pending and completed revision state for one closed menu container.
     *
     * @param containerId container whose state must be discarded
     */
    public synchronized void clearContainer(int containerId) {
        this.assemblies.remove(containerId);
    }

    /**
     * Drops all pending and completed revision state, normally when the active menu changes.
     */
    public synchronized void clear() {
        this.assemblies.clear();
    }

    /**
     * Accumulates one revision for one container.
     */
    private static final class Assembly {

        /**
         * Revision shared by every accepted batch.
         */
        private final long revision;
        /**
         * Expected number of batches in the revision.
         */
        private final int batchCount;
        /**
         * Expected number of rows after assembly.
         */
        private final int totalCount;
        /**
         * Immutable entries indexed by their server batch index.
         */
        private final Map<Integer, List<DataDistributionTowerTargetEntry>> batches = new HashMap<>();
        /**
         * Number of entries received so far across unique batches.
         */
        private int receivedEntryCount;
        /**
         * Prevents duplicate batches from publishing the same revision more than once.
         */
        private boolean published;

        /**
         * Creates empty state for one revision.
         *
         * @param revision   server revision
         * @param batchCount expected batch count
         * @param totalCount expected final row count
         */
        private Assembly(long revision, int batchCount, int totalCount) {
            this.revision = revision;
            this.batchCount = batchCount;
            this.totalCount = totalCount;
        }

        /**
         * Adds one validated batch to this revision.
         *
         * @param payload     incoming batch
         * @param containerId owning menu container
         * @return complete snapshot when this batch finishes the revision
         */
        private Optional<DataDistributionTowerTargetsSnapshot> accept(
                                                                      DataDistributionTowerTargetsPayload payload,
                                                                      int containerId) {
            if (payload.batchCount() != this.batchCount || payload.totalCount() != this.totalCount) {
                throw new IllegalArgumentException("Tower target batch metadata changed within revision " + this.revision);
            }

            List<DataDistributionTowerTargetEntry> previous = this.batches.get(payload.batchIndex());
            if (previous != null) {
                if (!previous.equals(payload.entries())) {
                    throw new IllegalArgumentException("Conflicting tower target batch " + payload.batchIndex() + " for revision " + this.revision);
                }
                return Optional.empty();
            }
            if (this.published) {
                return Optional.empty();
            }

            this.batches.put(payload.batchIndex(), payload.entries());
            this.receivedEntryCount += payload.entries().size();
            if (this.receivedEntryCount > this.totalCount) {
                throw new IllegalArgumentException("Tower target batches exceed declared total for revision " + this.revision + ": declared=" + this.totalCount + ", received=" + this.receivedEntryCount);
            }
            if (this.batches.size() != this.batchCount) {
                return Optional.empty();
            }
            if (this.receivedEntryCount != this.totalCount) {
                throw new IllegalArgumentException("Tower target batches do not match declared total for revision " + this.revision + ": declared=" + this.totalCount + ", received=" + this.receivedEntryCount);
            }

            ArrayList<DataDistributionTowerTargetEntry> entries = new ArrayList<>(this.totalCount);
            for (int batchIndex = 0; batchIndex < this.batchCount; batchIndex++) {
                List<DataDistributionTowerTargetEntry> batch = this.batches.get(batchIndex);
                if (batch == null) {
                    throw new IllegalStateException("Tower target revision is missing batch " + batchIndex);
                }
                entries.addAll(batch);
            }
            this.published = true;
            return Optional.of(new DataDistributionTowerTargetsSnapshot(
                    containerId,
                    this.revision,
                    this.totalCount,
                    entries));
        }
    }
}
