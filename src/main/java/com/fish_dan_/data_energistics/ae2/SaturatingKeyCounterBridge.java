package com.fish_dan_.data_energistics.ae2;

import appeng.api.stacks.KeyCounter;

/**
 * Internal runtime bridge that enables direct saturating additions while an AE network storage inventory reports its
 * contents into a {@link KeyCounter}.
 *
 * <p>
 * The bridge is enabled only for the duration of one {@code MEStorage#getAvailableStacks} call. It is implemented by
 * the KeyCounter Mixin and is intentionally not part of the external AE2 API.
 * </p>
 */
public interface SaturatingKeyCounterBridge {

    /**
     * Enables overflow-safe accumulation for the current counter.
     */
    void dataEnergistics$beginSaturatingMerge();

    /**
     * Restores ordinary KeyCounter behavior after one storage report.
     */
    void dataEnergistics$endSaturatingMerge();
}
