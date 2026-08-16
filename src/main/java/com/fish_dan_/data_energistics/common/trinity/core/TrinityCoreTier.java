package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Defines the M/G capacity tiers used by trinity storage and parallel CPU cores.
 */
public enum TrinityCoreTier {

    /** The first M-tier core, equivalent to 1M and 2 capability units. */
    SIZE_1M("1m", "1M", 1),
    /** The 4M core, equivalent to 8 capability units. */
    SIZE_4M("4m", "4M", 4),
    /** The 16M core, equivalent to 32 capability units. */
    SIZE_16M("16m", "16M", 16),
    /** The 64M core, equivalent to 128 capability units. */
    SIZE_64M("64m", "64M", 64),
    /** The 256M core, equivalent to 512 capability units. */
    SIZE_256M("256m", "256M", 256),
    /** The first G-tier core, equivalent to 1024M and 2048 capability units. */
    SIZE_1G("1g", "1G", 1024),
    /** The 4G core, equivalent to 4096M and 8192 capability units. */
    SIZE_4G("4g", "4G", 4096),
    /** The 16G core, equivalent to 16384M and 32768 capability units. */
    SIZE_16G("16g", "16G", 16384),
    /** The 64G core, equivalent to 65536M and 131072 capability units. */
    SIZE_64G("64g", "64G", 65536),
    /** The 256G core, equivalent to 262144M and 524288 capability units. */
    SIZE_256G("256g", "256G", 262144);

    /** Resource suffix used by block ids and generated resource filenames. */
    private final String idSuffix;
    /** Display label used by lang entries and tests. */
    private final String displayName;
    /** Capacity normalized to M so G tiers can share the same calculation rule. */
    private final int mUnits;
    /** Exact item or crafting storage capacity contributed by the core, in bytes. */
    private final long byteCapacity;
    /** Type or parallel value contributed by the core. */
    private final int capacityValue;

    TrinityCoreTier(String idSuffix, String displayName, int mUnits) {
        this.idSuffix = idSuffix;
        this.displayName = displayName;
        this.mUnits = mUnits;
        this.byteCapacity = Math.multiplyExact(mUnits, 1_048_576L);
        this.capacityValue = mUnits * 2;
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
     * Returns this tier after conversion to M units.
     */
    public int mUnits() {
        return this.mUnits;
    }

    /**
     * Returns the exact item or crafting storage capacity contributed by this tier, in bytes.
     */
    public long byteCapacity() {
        return this.byteCapacity;
    }

    /**
     * Returns the number of storage types or parallel jobs contributed by this tier.
     */
    public int capacityValue() {
        return this.capacityValue;
    }
}
