package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-thread boundary that captures immutable provider capacity and resolves it again immediately before commit.
 */
public interface ProviderCapacityResolver {

    /**
     * Creates the default identity- and revision-validating resolver.
     *
     * @return stateless resolver
     */
    static ProviderCapacityResolver create() {
        return new ProviderCapacityResolverImpl();
    }

    /**
     * Captures every publication for one exact ready pattern without consuming AE2's round-robin provider iterator.
     *
     * @param publications    grid-local publication index
     * @param pattern         exact live pattern identity
     * @param prototype       exact one-craft input binding
     * @param requestedCrafts positive logical work eligible for dispatch
     * @param patternIdentity immutable semantic signature used by the ready work
     * @param captureTick     current server tick
     * @return immutable capacity snapshots in publication and provider-target order
     */
    List<ProviderCapacitySnapshot> capture(
                                           CraftingProviderPublicationIndex publications,
                                           IPatternDetails pattern,
                                           KeyCounter[] prototype,
                                           long requestedCrafts,
                                           String patternIdentity,
                                           long captureTick);

    /**
     * Revalidates registration, capability revision, pattern identity and exact target immediately before preparation.
     *
     * @param publications    grid-local publication index
     * @param pattern         exact live pattern identity
     * @param prototype       current one-craft input binding
     * @param requestedCrafts positive count about to be offered to the target
     * @param patternIdentity current immutable semantic signature
     * @param snapshot        previously captured target
     * @param validationTick  current server tick
     * @return current live provider, or {@code null} when any proposal fact became stale
     */
    @Nullable
    ICraftingProvider resolveCurrent(
                                     CraftingProviderPublicationIndex publications,
                                     IPatternDetails pattern,
                                     KeyCounter[] prototype,
                                     long requestedCrafts,
                                     String patternIdentity,
                                     ProviderCapacitySnapshot snapshot,
                                     long validationTick);
}
