package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

import java.util.List;

/**
 * Server-thread lifecycle bridge used by AE2 publication Mixins to update the provider index.
 *
 * <p>
 * This mutation boundary is separate from {@link CraftingProviderPublicationIndex}, so dispatch and planning code
 * receive only read access. Implementations publish AE2's already-captured pattern list and never query its
 * round-robin provider iterator.
 * </p>
 */
public interface CraftingProviderPublicationSink {

    /**
     * Publishes one provider state after AE2 mounted all captured patterns successfully.
     *
     * @param provider live provider retained only by the server-thread index
     * @param patterns exact pattern list captured by AE2 for this registration
     * @return identity valid until the matching unpublish call
     */
    CraftingProviderId dataEnergistics$publishProvider(
                                                       ICraftingProvider provider,
                                                       List<IPatternDetails> patterns);

    /**
     * Invalidates one exact provider registration after AE2 unmounted it successfully.
     *
     * @param providerId current publication identity
     */
    void dataEnergistics$unpublishProvider(CraftingProviderId providerId);
}
