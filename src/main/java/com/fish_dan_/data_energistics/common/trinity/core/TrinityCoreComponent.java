package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Exposes static Trinity core slot compatibility and capability data without requiring block entities.
 */
public interface TrinityCoreComponent {

    /**
     * Returns this component's primary capability category. Query {@link #contributesToKind(TrinityCoreKind)} before
     * aggregating capacity: a universal placeholder contributes to none of its supported domains.
     */
    TrinityCoreKind kind();

    /**
     * Returns the storage type count; merged CPU cores, pattern cores, and empty units return zero.
     */
    int capacityValue();

    /**
     * Returns whether this component may occupy a core slot in the requested Trinity domain.
     *
     * <p>
     * Ordinary cores expose only their declared {@link #kind()}; a universal empty unit may expose all three
     * domains without contributing capacity to any of them.
     * </p>
     *
     * @param requestedKind capability domain being queried
     * @return whether this component is structurally valid for that domain
     */
    default boolean supportsKind(TrinityCoreKind requestedKind) {
        return this.kind() == requestedKind;
    }

    /**
     * Returns whether this component contributes capacity in the requested Trinity domain.
     *
     * <p>
     * Ordinary cores contribute to the domain they support. A universal empty unit is structurally valid in all
     * three domains but deliberately contributes to none of them.
     * </p>
     *
     * @param requestedKind capability domain being queried
     * @return whether this component contributes capacity to that domain
     */
    default boolean contributesToKind(TrinityCoreKind requestedKind) {
        return supportsKind(requestedKind);
    }

    /**
     * Returns the exact item or crafting storage capacity contributed by this core, in bytes. Pattern cores and empty
     * units return zero. This capacity is independent of the storage type count and must be supplied explicitly.
     */
    long byteCapacity();

    /**
     * Returns the number of patterns this core lets the crafting child structure recognize; storage cores, parallel
     * CPU cores, and empty units return zero.
     */
    int patternCapacity();
}
