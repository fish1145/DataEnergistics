package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.Objects;
import java.util.concurrent.Future;

/**
 * Gives one caller an isolated wait handle and records how its underlying calculation was selected.
 *
 * @param future     caller-owned wait handle whose cancellation never cancels shared work
 * @param cacheHit   whether an existing completed or in-flight entry supplied the calculation
 * @param registered whether the new calculation occupies a Grid LRU entry
 * @param <V>        result type
 */
public record TrinityComputationLookup<V>(Future<V> future, boolean cacheHit, boolean registered) {

    public TrinityComputationLookup {
        Objects.requireNonNull(future, "A Trinity computation lookup requires a caller future");
        if (cacheHit && !registered) {
            throw new IllegalArgumentException("A Trinity cache hit must refer to a registered entry");
        }
    }
}
