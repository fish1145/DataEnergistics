package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityCachedComputation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationNamespace;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Cache decorator that stores only immutable capture results and always delegates current-provider validation.
 */
final class CachingProviderCapacityResolver implements ProviderCapacityResolver {

    private final ProviderCapacityResolver delegate;
    private final Supplier<TrinityComputationCache> cache;

    CachingProviderCapacityResolver(ProviderCapacityResolver delegate, Supplier<TrinityComputationCache> cache) {
        if (delegate == null || cache == null) {
            throw new IllegalArgumentException("Caching provider capacity resolution requires a delegate and cache");
        }
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<ProviderCapacitySnapshot> capture(CraftingProviderPublicationIndex publications,
                                                  IPatternDetails pattern,
                                                  KeyCounter[] prototype,
                                                  long requestedCrafts,
                                                  String patternIdentity,
                                                  long captureTick) {
        ProviderCapacityCaptureKey key = ProviderCapacityCaptureKey.capture(
                publications,
                pattern,
                prototype,
                requestedCrafts,
                patternIdentity,
                captureTick);
        try {
            return this.cache.get().computeInline(
                    key.gridScope(),
                    TrinityComputationNamespace.CAPACITY_CAPTURE,
                    key.publicationRevision(),
                    key,
                    () -> TrinityCachedComputation.cacheable(captureAndValidate(
                            key,
                            publications,
                            pattern,
                            prototype,
                            requestedCrafts,
                            patternIdentity,
                            captureTick))).value();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider capacity cache wait was interrupted", exception);
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    @Override
    @Nullable
    public ICraftingProvider resolveCurrent(CraftingProviderPublicationIndex publications,
                                             IPatternDetails pattern,
                                             KeyCounter[] prototype,
                                             long requestedCrafts,
                                             String patternIdentity,
                                             ProviderCapacitySnapshot snapshot,
                                             long validationTick) {
        return this.delegate.resolveCurrent(
                publications,
                pattern,
                prototype,
                requestedCrafts,
                patternIdentity,
                snapshot,
                validationTick);
    }

    private List<ProviderCapacitySnapshot> captureAndValidate(ProviderCapacityCaptureKey key,
                                                              CraftingProviderPublicationIndex publications,
                                                              IPatternDetails pattern,
                                                              KeyCounter[] prototype,
                                                              long requestedCrafts,
                                                              String patternIdentity,
                                                              long captureTick) {
        List<ProviderCapacitySnapshot> snapshots = this.delegate.capture(
                publications,
                pattern,
                prototype,
                requestedCrafts,
                patternIdentity,
                captureTick);
        if (publications.publicationScope() != key.gridScope() ||
                publications.publicationRevision() != key.publicationRevision() ||
                CountedCraftingProviderAdapters.mutationRevision() != key.capacityRevision() ||
                !publications.providerIdsFor(pattern).equals(key.providerFingerprint())) {
            throw new IllegalStateException("Provider capacity cache context changed while capturing capacity");
        }
        for (ProviderCapacitySnapshot snapshot : snapshots) {
            if (snapshot.providerId().publicationScope() != key.gridScope() ||
                    snapshot.publicationRevision() != key.publicationRevision() ||
                    snapshot.capacityRevision() != key.capacityRevision() ||
                    snapshot.captureTick() != key.capacityEpoch() ||
                    !snapshot.patternIdentity().equals(key.patternIdentity())) {
                throw new IllegalStateException("Provider capacity cache result escaped its immutable key context");
            }
        }
        return List.copyOf(snapshots);
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Provider capacity cache calculation failed", failure);
    }
}
