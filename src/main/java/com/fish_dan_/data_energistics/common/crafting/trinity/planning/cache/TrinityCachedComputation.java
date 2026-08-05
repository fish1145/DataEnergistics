package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.Objects;

/**
 * Publishes one immutable pure result together with the decision to retain it after completion.
 *
 * @param value     immutable calculation result
 * @param cacheable whether later callers may reuse the completed value
 * @param <V>       result type owned by one computation namespace
 */
public record TrinityCachedComputation<V>(V value, boolean cacheable) {

    public TrinityCachedComputation {
        Objects.requireNonNull(value, "A Trinity cached computation requires a value");
    }

    /**
     * Creates a deterministic result that remains in the Grid LRU.
     *
     * @param value immutable successful or deterministic rejection value
     * @param <V>   value type
     * @return retained computation result
     */
    public static <V> TrinityCachedComputation<V> cacheable(V value) {
        return new TrinityCachedComputation<>(value, true);
    }

    /**
     * Creates a transient result shared only with callers already waiting on the same computation.
     *
     * @param value immutable transient value
     * @param <V>   value type
     * @return non-retained computation result
     */
    public static <V> TrinityCachedComputation<V> transientValue(V value) {
        return new TrinityCachedComputation<>(value, false);
    }
}
