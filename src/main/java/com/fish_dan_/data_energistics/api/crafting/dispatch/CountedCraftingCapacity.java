package com.fish_dan_.data_energistics.api.crafting.dispatch;

import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;

/**
 * Immutable read-only capacity exposed by a registered counted crafting adapter.
 *
 * <p>
 * Empty numeric values mean that the adapter cannot prove a safe bound. Zero is a known exhausted capacity and is
 * never treated as unknown.
 * </p>
 *
 * @param target             provider-local route and optional physical machine identity
 * @param routingMode        target-selection contract used when preparing the route
 * @param logicalCrafts      known currently available logical crafts, or empty when unknown
 * @param maximumSingleBatch known upper bound for one physical submission, or empty when unknown
 */
public record CountedCraftingCapacity(@NotNull CountedCraftingTarget target,
                                      @NotNull CountedCraftingRoutingMode routingMode,
                                      @NotNull OptionalLong logicalCrafts,
                                      @NotNull OptionalLong maximumSingleBatch) {

    /**
     * Validates every known numeric bound before the capacity reaches proposal planning.
     */
    public CountedCraftingCapacity {
        requireNonNegative(logicalCrafts, "logical crafting capacity");
        requireNonNegative(maximumSingleBatch, "maximum counted crafting batch");
    }

    /**
     * Returns the source-compatible aggregate capability of an adapter that only implements {@code prepareBatch}.
     *
     * @return aggregate provider target with unknown numeric bounds
     */
    public static @NotNull CountedCraftingCapacity aggregateUnknown() {
        return new CountedCraftingCapacity(
                CountedCraftingTarget.provider(),
                CountedCraftingRoutingMode.AGGREGATE,
                OptionalLong.empty(),
                OptionalLong.empty());
    }

    /**
     * Rejects negative known values without assigning a sentinel meaning to them.
     */
    private static void requireNonNegative(@NotNull OptionalLong value, @NotNull String role) {
        if (value.isPresent() && value.getAsLong() < 0L) {
            throw new IllegalArgumentException("Known " + role + " must not be negative");
        }
    }
}
