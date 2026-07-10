package com.fish_dan_.data_energistics.common.trinity;

/**
 * Immutable capability metadata shared by trinity core blocks and tests that do not boot Minecraft registries.
 */
public final class TrinityCoreMetadata implements TrinityCoreComponent {

    /** Capability category that future trinity structure scans will aggregate. */
    private final TrinityCoreKind kind;
    /** Storage type or parallel CPU count contributed by capacity-based cores. */
    private final int capacityValue;
    /** Pattern count contributed by pattern processing cores. */
    private final int patternCapacity;

    public TrinityCoreMetadata(TrinityCoreKind kind, int capacityValue, int patternCapacity) {
        this.kind = kind;
        validateCoreData(this.kind, capacityValue, patternCapacity);
        this.capacityValue = capacityValue;
        this.patternCapacity = patternCapacity;
    }

    /**
     * Creates storage type metadata from the shared M/G tier table.
     */
    public static TrinityCoreMetadata storageCore(TrinityCoreTier tier) {
        return new TrinityCoreMetadata(TrinityCoreKind.STORAGE_TYPES, tier.capacityValue(), 0);
    }

    /**
     * Creates parallel CPU metadata from the shared M/G tier table.
     */
    public static TrinityCoreMetadata parallelCpuCore(TrinityCoreTier tier) {
        return new TrinityCoreMetadata(TrinityCoreKind.PARALLEL_CPU, tier.capacityValue(), 0);
    }

    /**
     * Creates pattern processing metadata with a fixed recognizable pattern capacity.
     */
    public static TrinityCoreMetadata patternProcessingCore(int patternCapacity) {
        return new TrinityCoreMetadata(TrinityCoreKind.PATTERN_PROCESSING, 0, patternCapacity);
    }

    @Override
    public TrinityCoreKind kind() {
        return this.kind;
    }

    @Override
    public int capacityValue() {
        return this.capacityValue;
    }

    @Override
    public int patternCapacity() {
        return this.patternCapacity;
    }

    private static void validateCoreData(TrinityCoreKind kind, int capacityValue, int patternCapacity) {
        switch (kind) {
            case STORAGE_TYPES, PARALLEL_CPU -> {
                if (capacityValue <= 0 || patternCapacity != 0) {
                    throw new IllegalArgumentException(kind + " cores require positive capacity and zero pattern capacity");
                }
            }
            case PATTERN_PROCESSING -> {
                if (capacityValue != 0 || patternCapacity <= 0) {
                    throw new IllegalArgumentException("Pattern processing cores require zero capacity and positive pattern capacity");
                }
            }
        }
    }
}
