package com.fish_dan_.data_energistics.common.trinity;

import java.util.Objects;

/**
 * Immutable capability metadata shared by trinity core blocks and tests that do not boot Minecraft registries.
 */
public final class TrinityCoreMetadata implements TrinityCoreComponent {

    /** Capability category that future trinity structure scans will aggregate. */
    private final TrinityCoreKind kind;
    /** Storage type or parallel CPU count contributed by capacity-based cores. */
    private final int capacityValue;
    /** Pattern row count contributed by pattern processing cores. */
    private final int patternRows;

    public TrinityCoreMetadata(TrinityCoreKind kind, int capacityValue, int patternRows) {
        this.kind = Objects.requireNonNull(kind, "kind");
        validateCoreData(this.kind, capacityValue, patternRows);
        this.capacityValue = capacityValue;
        this.patternRows = patternRows;
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
     * Creates pattern processing metadata with a fixed row count.
     */
    public static TrinityCoreMetadata patternProcessingCore(int patternRows) {
        return new TrinityCoreMetadata(TrinityCoreKind.PATTERN_PROCESSING, 0, patternRows);
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
    public int patternRows() {
        return this.patternRows;
    }

    private static void validateCoreData(TrinityCoreKind kind, int capacityValue, int patternRows) {
        switch (kind) {
            case STORAGE_TYPES, PARALLEL_CPU -> {
                if (capacityValue <= 0 || patternRows != 0) {
                    throw new IllegalArgumentException(kind + " cores require positive capacity and zero pattern rows");
                }
            }
            case PATTERN_PROCESSING -> {
                if (capacityValue != 0 || patternRows <= 0) {
                    throw new IllegalArgumentException("Pattern processing cores require zero capacity and positive pattern rows");
                }
            }
        }
    }
}
