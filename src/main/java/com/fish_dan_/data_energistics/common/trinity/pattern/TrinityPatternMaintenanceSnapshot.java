package com.fish_dan_.data_energistics.common.trinity.pattern;

/** Immutable server-authoritative progress of Data Core pattern maintenance. */
public record TrinityPatternMaintenanceSnapshot(Operation operation,
                                                Stage stage,
                                                long completedUnits,
                                                long totalUnits,
                                                int installedPatterns,
                                                int patternCapacity,
                                                int succeededUnits,
                                                int failedUnits) {

    public TrinityPatternMaintenanceSnapshot {
        if (completedUnits < 0L || totalUnits < 0L || completedUnits > totalUnits || installedPatterns < 0 ||
                patternCapacity < 0 || installedPatterns > patternCapacity || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("Invalid Trinity pattern maintenance progress");
        }
        if (operation == Operation.IDLE && stage != Stage.IDLE) {
            throw new IllegalArgumentException("Idle Trinity pattern maintenance requires the idle stage");
        }
    }

    /** Returns the capacity-only state used when no maintenance result is retained. */
    public static TrinityPatternMaintenanceSnapshot idle(int installedPatterns, int patternCapacity) {
        return new TrinityPatternMaintenanceSnapshot(
                Operation.IDLE,
                Stage.IDLE,
                0L,
                0L,
                installedPatterns,
                patternCapacity,
                0,
                0);
    }

    /** Returns whether one migration or installed-pattern refund currently owns the catalog. */
    public boolean active() {
        return this.operation != Operation.IDLE && !this.stage.terminal();
    }

    /** Pattern-maintenance operation shown by the aggregate pattern view. */
    public enum Operation {
        IDLE,
        MIGRATION,
        REFUND_PATTERNS
    }

    /** Bounded task phase synchronized to the aggregate pattern view. */
    public enum Stage {

        IDLE(false),
        SCANNING(false),
        STORAGE(false),
        PATTERN_CONTAINERS(false),
        PREPARING_REFUND(false),
        WAITING_FOR_PLAYER(false),
        COMMITTING(false),
        COMPLETED(true),
        FAILED(true),
        CANCELLED(true);

        private final boolean terminal;

        Stage(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean terminal() {
            return this.terminal;
        }
    }
}
