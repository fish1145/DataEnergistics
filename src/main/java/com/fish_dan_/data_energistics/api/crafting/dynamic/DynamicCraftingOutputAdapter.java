package com.fish_dan_.data_energistics.api.crafting.dynamic;

import net.minecraft.resources.ResourceLocation;

import appeng.api.crafting.IPatternDetails;

import java.util.Optional;

/**
 * Resolves dynamic physical-output semantics for arbitrary crafting-pattern implementations.
 *
 * <p>
 * Adapters receive the original outer {@link IPatternDetails} instance so wrappers and specialized processing
 * patterns can expose their own output semantics without casts to an AE2 implementation class. Adapters must be
 * stateless and must not inspect or retain a CPU, provider, grid, world, or crafting job.
 * </p>
 */
public interface DynamicCraftingOutputAdapter {

    /**
     * Returns the stable public identity used for duplicate checks, persistence, and diagnostics.
     *
     * @return globally unique adapter ID within the dynamic-output registry
     */
    ResourceLocation id();

    /**
     * Resolves dynamic output declarations for one logical push of the supplied pattern.
     *
     * <p>
     * Returning empty leaves every output under exact key semantics. Returned declarations must refer to physical
     * outputs of the supplied outer pattern; the crafting runtime validates that relationship before dispatch.
     * </p>
     *
     * @param details original outer pattern details selected for dispatch
     * @return immutable dynamic semantics, or empty when this adapter does not own the pattern
     */
    Optional<DynamicCraftingOutputSemantics> resolve(IPatternDetails details);
}
