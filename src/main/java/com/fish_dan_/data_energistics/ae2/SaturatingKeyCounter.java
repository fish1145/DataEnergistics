package com.fish_dan_.data_energistics.ae2;

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
            long current = total.get(entry.getKey());
            long added = entry.getLongValue();
            if (current < 0L || added < 0L) {
                throw new IllegalArgumentException("AE storage amounts must be non-negative");
            }
            total.set(entry.getKey(), added > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + added);
        }
    }
}
