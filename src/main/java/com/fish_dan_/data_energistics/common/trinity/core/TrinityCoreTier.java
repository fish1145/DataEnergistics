package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Defines the K/M capacity tiers used by Trinity storage and merged CPU cores.
 */
public enum TrinityCoreTier {

    SIZE_1K("1k", "1K", 1),
    SIZE_4K("4k", "4K", 4),
    SIZE_16K("16k", "16K", 16),
    SIZE_64K("64k", "64K", 64),
    SIZE_256K("256k", "256K", 256),
    SIZE_1M("1m", "1M", 1_024),
    SIZE_4M("4m", "4M", 4_096),
    SIZE_16M("16m", "16M", 16_384),
    SIZE_64M("64m", "64M", 65_536),
    SIZE_256M("256m", "256M", 262_144);

    /** Resource suffix used by block ids and generated resource filenames. */
    private final String idSuffix;
    /** Display label used by lang entries and tests. */
    private final String displayName;
    /** Exact capacity normalized to KiB. */
    private final int kibibytes;
    /** Exact item or crafting storage capacity contributed by the core, in bytes. */
    private final long byteCapacity;
    /** Storage type capacity contributed by a storage core of this tier. */
    private final int capacityValue;

    TrinityCoreTier(String idSuffix, String displayName, int kibibytes) {
        this.idSuffix = idSuffix;
        this.displayName = displayName;
        this.kibibytes = kibibytes;
        this.byteCapacity = Math.multiplyExact(kibibytes, 1_024L);
        this.capacityValue = Math.max(1, Math.floorDiv(kibibytes, 512));
    }

    /**
     * Returns the lowercase resource suffix used by this tier.
     */
    public String idSuffix() {
        return this.idSuffix;
    }

    /**
     * Returns the user-facing tier label.
     */
    public String displayName() {
        return this.displayName;
    }

    /**
     * Returns this tier in KiB.
     */
    public int kibibytes() {
        return this.kibibytes;
    }

    /**
     * Returns the exact item or crafting storage capacity contributed by this tier, in bytes.
     */
    public long byteCapacity() {
        return this.byteCapacity;
    }

    /**
     * Returns the number of storage types contributed by this tier.
     */
    public int capacityValue() {
        return this.capacityValue;
    }
}
