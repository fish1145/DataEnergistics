package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Immutable capability metadata shared by trinity core blocks and tests that do not boot Minecraft registries.
 */
public final class TrinityCoreMetadata implements TrinityCoreComponent {

    /** Primary category; structural acceptance and contributed capacity are queried separately. */
    private final TrinityCoreKind kind;
    /** Storage type count contributed by storage cores. */
    private final int capacityValue;
    /** Exact item or crafting storage capacity contributed by capacity-based cores, in bytes. */
    private final long byteCapacity;
    /** Pattern count contributed by pattern processing cores. */
    private final int patternCapacity;
    /** Whether this metadata is the zero-capacity placeholder accepted by every core predicate. */
    private final boolean universal;

    public TrinityCoreMetadata(TrinityCoreKind kind, int capacityValue, long byteCapacity, int patternCapacity) {
        this(kind, capacityValue, byteCapacity, patternCapacity, false);
    }

    private TrinityCoreMetadata(TrinityCoreKind kind,
                                int capacityValue,
                                long byteCapacity,
                                int patternCapacity,
                                boolean universal) {
        this.kind = kind;
        validateCoreData(this.kind, capacityValue, byteCapacity, patternCapacity, universal);
        this.capacityValue = capacityValue;
        this.byteCapacity = byteCapacity;
        this.patternCapacity = patternCapacity;
        this.universal = universal;
    }

    /**
     * Creates storage type metadata from the shared K/M tier table.
     */
    public static TrinityCoreMetadata storageCore(TrinityCoreTier tier) {
        return new TrinityCoreMetadata(
                TrinityCoreKind.STORAGE_TYPES,
                tier.capacityValue(),
                tier.byteCapacity(),
                0);
    }

    /**
     * Creates merged CPU storage metadata from the shared K/M tier table.
     */
    public static TrinityCoreMetadata parallelCpuCore(TrinityCoreTier tier) {
        return new TrinityCoreMetadata(
                TrinityCoreKind.PARALLEL_CPU,
                0,
                tier.byteCapacity(),
                0);
    }

    /**
     * Creates pattern processing metadata with a fixed recognizable pattern capacity.
     */
    public static TrinityCoreMetadata patternProcessingCore(int patternCapacity) {
        return new TrinityCoreMetadata(TrinityCoreKind.PATTERN_PROCESSING, 0, 0L, patternCapacity);
    }

    /**
     * Creates the zero-capacity universal unit that can occupy a storage, merged CPU, or pattern-processing core slot.
     *
     * <p>
     * The primary kind is storage, but it does not imply a storage capacity contribution.
     * Domain-aware callers must distinguish structural compatibility through
     * {@link TrinityCoreComponent#supportsKind(TrinityCoreKind)} from actual capacity through
     * {@link TrinityCoreComponent#contributesToKind(TrinityCoreKind)}.
     * </p>
     */
    public static TrinityCoreMetadata emptyTrinityUnit() {
        return new TrinityCoreMetadata(
                TrinityCoreKind.STORAGE_TYPES,
                0,
                0L,
                0,
                true);
    }

    @Override
    public TrinityCoreKind kind() {
        return this.kind;
    }

    @Override
    public boolean supportsKind(TrinityCoreKind requestedKind) {
        return this.universal || this.kind == requestedKind;
    }

    @Override
    public boolean contributesToKind(TrinityCoreKind requestedKind) {
        return !this.universal && this.kind == requestedKind;
    }

    @Override
    public int capacityValue() {
        return this.capacityValue;
    }

    @Override
    public long byteCapacity() {
        return this.byteCapacity;
    }

    @Override
    public int patternCapacity() {
        return this.patternCapacity;
    }

    private static void validateCoreData(
                                         TrinityCoreKind kind,
                                         int capacityValue,
                                         long byteCapacity,
                                         int patternCapacity,
                                         boolean universal) {
        if (universal) {
            if (kind != TrinityCoreKind.STORAGE_TYPES || capacityValue != 0 || byteCapacity != 0 || patternCapacity != 0) {
                throw new IllegalArgumentException(
                        "Universal Trinity units require storage primary metadata and zero capacities for all domains");
            }
            return;
        }
        switch (kind) {
            case STORAGE_TYPES -> {
                if (capacityValue <= 0 || byteCapacity <= 0 || patternCapacity != 0) {
                    throw new IllegalArgumentException(
                            "Storage cores require positive type and byte capacities, and zero pattern capacity");
                }
            }
            case PARALLEL_CPU -> {
                if (capacityValue != 0 || byteCapacity <= 0 || patternCapacity != 0) {
                    throw new IllegalArgumentException(
                            "Merged CPU cores require zero type capacity, positive byte capacity, and zero pattern capacity");
                }
            }
            case PATTERN_PROCESSING -> {
                if (capacityValue != 0 || byteCapacity != 0 || patternCapacity <= 0) {
                    throw new IllegalArgumentException(
                            "Pattern processing cores require zero capability and byte capacities, and positive pattern capacity");
                }
            }
        }
    }
}
