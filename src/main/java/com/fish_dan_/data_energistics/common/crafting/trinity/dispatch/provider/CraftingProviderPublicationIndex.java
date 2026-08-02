package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-thread index that separates immutable provider identities from AE2's live provider objects.
 *
 * <p>
 * The index follows AE2 publication identity rather than equality and preserves every provider publication for an
 * exact pattern object. Queries never call AE2's round-robin {@code getMediums} iterator.
 * </p>
 */
public interface CraftingProviderPublicationIndex {

    /**
     * Returns the generation changed by every successful publication or removal.
     *
     * @return non-negative monotonic revision
     */
    long publicationRevision();

    /**
     * Resolves all current publications that advertised this exact pattern object.
     *
     * @param patternIdentity live pattern identity; equality-equivalent objects remain isolated
     * @return immutable provider IDs in publication order, including multiplicity
     */
    List<CraftingProviderId> providerIdsFor(IPatternDetails patternIdentity);

    /**
     * Resolves a current ID immediately before server-thread capacity capture or commit.
     *
     * @param providerId provider publication to resolve
     * @return live provider, or {@code null} when the ID became stale
     */
    @Nullable
    ICraftingProvider resolveLiveProvider(CraftingProviderId providerId);
}
