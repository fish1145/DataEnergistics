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
     * Returns the storage type count or parallel CPU count; pattern cores return zero.
     */
    int capacityValue();

    /**
     * Returns the exact item or crafting storage capacity contributed by this core, in bytes. Pattern cores return
     * zero. The default preserves the historical ratio for third-party component implementations.
     */
    default long byteCapacity() {
        return Math.multiplyExact(capacityValue(), 524_288L);
    }

    /**
     * Returns the number of patterns this core lets the crafting child structure recognize; storage and parallel CPU
     * cores return zero.
     */
    int patternCapacity();
}
