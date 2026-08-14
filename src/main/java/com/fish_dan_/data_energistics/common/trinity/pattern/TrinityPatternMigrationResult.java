package com.fish_dan_.data_energistics.common.trinity.pattern;

/** Bounded outcome of one best-effort migration from an AE grid into a Trinity pattern catalog. */
public record TrinityPatternMigrationResult(int movedFromStorage,
                                            int movedFromContainers,
                                            int invalidRefunded,
                                            int duplicateRefunded,
                                            int unsupportedKept,
                                            int storageInvalidRecycled,
                                            int storageUnsupportedKept,
                                            int storageDuplicateRecycled,
                                            int storageSourceUncertain,
                                            int meBlocked,
                                            int capacitySkipped,
                                            int sourceFailures,
                                            int quarantinedSources,
                                            int fallbackIdentitySources,
                                            boolean targetAborted) {

    /** @return terminal result used when no active Trinity destination is available */
    public static TrinityPatternMigrationResult targetUnavailable() {
        return new TrinityPatternMigrationResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    /** @return whether the batch changed at least one source or target */
    public boolean changed() {
        return this.movedFromStorage > 0 || this.movedFromContainers > 0 ||
                this.invalidRefunded > 0 || this.duplicateRefunded > 0 ||
                this.storageInvalidRecycled > 0 || this.storageDuplicateRecycled > 0;
    }
}
