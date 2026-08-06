package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.Objects;

/**
 * Returns an inline computation value together with its cache-selection path.
 *
 * @param value      immutable calculation value
 * @param cacheHit   whether an existing completed or in-flight entry supplied the value
 * @param registered whether the calculation occupies a Grid LRU entry instead of an in-flight bypass slot
 * @param <V>        result type
 */
public record TrinityComputationValue<V>(V value, boolean cacheHit, boolean registered) {

    public TrinityComputationValue {
        Objects.requireNonNull(value, "A Trinity inline computation requires a value");
    }
}
