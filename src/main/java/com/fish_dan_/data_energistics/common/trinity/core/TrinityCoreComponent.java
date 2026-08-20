package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Exposes static trinity core capability data for future structure readers without requiring block entities.
 */
public interface TrinityCoreComponent {

    /**
     * Returns the capability category this core contributes to the trinity structure.
     */
    TrinityCoreKind kind();

    /**
     * Returns the storage type count; merged CPU and pattern cores return zero.
     */
    int capacityValue();

    /**
     * Returns whether this component can satisfy the requested Trinity capability domain.
     *
     * <p>Ordinary cores expose only their declared {@link #kind()}; a universal empty unit may expose all three
     * domains while retaining a primary kind for legacy callers.</p>
     *
     * @param requestedKind capability domain being queried
     * @return whether this component contributes to that domain
     */
    default boolean supportsKind(TrinityCoreKind requestedKind) {
        return this.kind() == requestedKind;
    }

    /**
     * Returns the storage type count for a requested capability domain.
     *
     * @param requestedKind capability domain being queried
     * @return storage type count, or zero for non-storage domains
     * @throws IllegalArgumentException when this component does not support the requested domain
     */
    default int capacityValue(TrinityCoreKind requestedKind) {
        if (!supportsKind(requestedKind)) {
            throw new IllegalArgumentException("Trinity core does not support capability domain: " + requestedKind);
        }
        return requestedKind == TrinityCoreKind.STORAGE_TYPES ? capacityValue() : 0;
    }

    /**
     * Returns the exact item or crafting storage capacity contributed by this core, in bytes. Pattern cores return
     * zero. The default preserves the historical ratio for third-party component implementations.
     */
    default long byteCapacity() {
        return Math.multiplyExact(capacityValue(), 524_288L);
    }

    /**
     * Returns the byte capacity for a requested storage or parallel-CPU capability domain.
     *
     * @param requestedKind capability domain being queried
     * @return byte capacity, or zero for pattern-processing domains
     * @throws IllegalArgumentException when this component does not support the requested domain
     */
    default long byteCapacity(TrinityCoreKind requestedKind) {
        if (!supportsKind(requestedKind)) {
            throw new IllegalArgumentException("Trinity core does not support capability domain: " + requestedKind);
        }
        return requestedKind == TrinityCoreKind.PATTERN_PROCESSING ? 0L : byteCapacity();
    }

    /**
     * Returns the number of patterns this core lets the crafting child structure recognize; storage and parallel CPU
     * cores return zero.
     */
    int patternCapacity();

    /**
     * Returns the pattern capacity for a requested capability domain.
     *
     * @param requestedKind capability domain being queried
     * @return pattern capacity, or zero for non-pattern domains
     * @throws IllegalArgumentException when this component does not support the requested domain
     */
    default int patternCapacity(TrinityCoreKind requestedKind) {
        if (!supportsKind(requestedKind)) {
            throw new IllegalArgumentException("Trinity core does not support capability domain: " + requestedKind);
        }
        return requestedKind == TrinityCoreKind.PATTERN_PROCESSING ? patternCapacity() : 0;
    }
}
