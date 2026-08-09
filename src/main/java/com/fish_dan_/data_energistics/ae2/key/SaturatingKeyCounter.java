package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.KeyCounter;

/** Combines non-negative AE storage amounts without allowing signed {@code long} overflow. */
public final class SaturatingKeyCounter {

    private SaturatingKeyCounter() {}

    /**
     * Merges one mounted storage contribution into an AE network total, capping each key at {@link Long#MAX_VALUE}.
     *
     * @param total        accumulated network amounts
     * @param contribution amounts reported by one mounted storage
     */
    public static void merge(KeyCounter total, KeyCounter contribution) {
        for (var entry : contribution) {
            total.set(
                    entry.getKey(),
                    mergeAmount(total.get(entry.getKey()), entry.getLongValue()));
        }
    }

    /**
     * Adds one mounted-storage amount without allowing signed overflow.
     *
     * @param current accumulated network amount
     * @param added   amount reported by one mounted storage
     * @return non-negative saturated total
     */
    public static long mergeAmount(long current, long added) {
        if (current < 0L || added < 0L) {
            Data_Energistics.LOGGER.warn("AE storage amounts must be non-negative");
            if (current < 0L) {
                current = 0L;
            }
            if (added < 0L) {
                added = 0L;
            }
        }
        return added > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + added;
    }
}
