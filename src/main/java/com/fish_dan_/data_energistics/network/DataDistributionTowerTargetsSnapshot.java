package com.fish_dan_.data_energistics.network;

import java.util.List;

/**
 * One complete, atomically publishable Data Distribution Tower target list.
 *
 * @param containerId menu container that requested the target list
 * @param revision    monotonically increasing server snapshot revision
 * @param totalCount  complete number of structured target rows
 * @param entries     ordered immutable target rows
 */
public record DataDistributionTowerTargetsSnapshot(int containerId,
                                                   long revision,
                                                   int totalCount,
                                                   List<DataDistributionTowerTargetEntry> entries) {

    /**
     * Validates that the assembled snapshot is complete and immutable.
     */
    public DataDistributionTowerTargetsSnapshot {
        entries = List.copyOf(entries);
        if (containerId < 0) {
            throw new IllegalArgumentException("Container id must be non-negative: " + containerId);
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Target snapshot revision must be non-negative: " + revision);
        }
        if (totalCount != entries.size()) {
            throw new IllegalArgumentException(
                    "Target snapshot total does not match assembled entries: expected=" + totalCount + ", actual=" + entries.size());
        }
    }
}
