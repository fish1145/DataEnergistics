package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

/**
 * Identifies one crafting-provider publication without retaining the live provider object.
 *
 * <p>
 * The identity is process-local and intentionally changes when AE2 removes and republishes the provider. It must
 * therefore be re-resolved before server-thread work and must not be persisted across server restarts.
 * </p>
 *
 * @param publicationScope     identity of the owning grid publication index
 * @param registrationSequence monotonic registration number inside that index
 */
public record CraftingProviderId(long publicationScope, long registrationSequence) {

    public CraftingProviderId {
        if (publicationScope <= 0L) {
            throw new IllegalArgumentException("Crafting provider publication scope must be positive");
        }
        if (registrationSequence <= 0L) {
            throw new IllegalArgumentException("Crafting provider registration sequence must be positive");
        }
    }
}
