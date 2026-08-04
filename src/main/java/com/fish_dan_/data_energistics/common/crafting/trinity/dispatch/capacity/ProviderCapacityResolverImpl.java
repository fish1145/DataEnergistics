package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Identity-index implementation that treats provider-owned target views as authoritative and all other routes
 * conservatively.
 */
final class ProviderCapacityResolverImpl implements ProviderCapacityResolver {

    @Override
    public List<ProviderCapacitySnapshot> capture(
                                                  CraftingProviderPublicationIndex publications,
                                                  IPatternDetails pattern,
                                                  KeyCounter[] prototype,
                                                  long requestedCrafts,
                                                  String patternIdentity,
                                                  long captureTick) {
        validateCapture(requestedCrafts, patternIdentity, captureTick);
        long publicationRevision = publications.publicationRevision();
        long capacityRevision = CountedCraftingProviderAdapters.mutationRevision();
        ArrayList<ProviderCapacitySnapshot> snapshots = new ArrayList<>();
        for (var providerId : publications.providerIdsFor(pattern)) {
            ICraftingProvider provider = publications.resolveLiveProvider(providerId);
            if (provider == null) {
                throw new IllegalStateException("Current crafting-provider publication did not resolve its provider");
            }
            if (provider instanceof ProviderCapacityView capacityView) {
                List<ProviderCapacitySnapshot> providerSnapshots = List.copyOf(capacityView.snapshotCapacity(
                        providerId,
                        pattern,
                        prototype,
                        requestedCrafts,
                        patternIdentity,
                        publicationRevision,
                        capacityRevision,
                        captureTick));
                for (ProviderCapacitySnapshot snapshot : providerSnapshots) {
                    validateProviderSnapshot(
                            snapshot,
                            providerId,
                            patternIdentity,
                            publicationRevision,
                            capacityRevision);
                }
                snapshots.addAll(providerSnapshots);
                continue;
            }

            ProviderRoutingMode routingMode = CountedCraftingProviderAdapters.fallbackRoutingMode(provider);
            snapshots.add(new ProviderCapacitySnapshot(
                    providerId,
                    CraftingDispatchTarget.provider(),
                    Optional.empty(),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    routingMode,
                    DispatchCapacity.Unknown.INSTANCE,
                    routingMode == ProviderRoutingMode.UNKNOWN ?
                            new DispatchCapacity.Known(1L) :
                            DispatchCapacity.Unknown.INSTANCE));
        }
        if (publications.publicationRevision() != publicationRevision ||
                CountedCraftingProviderAdapters.mutationRevision() != capacityRevision) {
            throw new IllegalStateException("Crafting-provider revisions changed during capacity capture");
        }
        return List.copyOf(snapshots);
    }

    @Override
    @Nullable
    public ICraftingProvider resolveCurrent(
                                            CraftingProviderPublicationIndex publications,
                                            IPatternDetails pattern,
                                            KeyCounter[] prototype,
                                            long requestedCrafts,
                                            String patternIdentity,
                                            ProviderCapacitySnapshot snapshot,
                                            long validationTick) {
        validateCapture(requestedCrafts, patternIdentity, validationTick);
        if (publications.publicationRevision() != snapshot.publicationRevision() ||
                CountedCraftingProviderAdapters.mutationRevision() != snapshot.capacityRevision() ||
                !snapshot.patternIdentity().equals(patternIdentity) ||
                !publications.providerIdsFor(pattern).contains(snapshot.providerId())) {
            return null;
        }
        ICraftingProvider provider = publications.resolveLiveProvider(snapshot.providerId());
        if (provider == null) {
            return null;
        }
        if (!(provider instanceof ProviderCapacityView capacityView)) {
            return CountedCraftingProviderAdapters.fallbackRoutingMode(provider) == snapshot.routingMode() ?
                    provider : null;
        }

        List<ProviderCapacitySnapshot> currentSnapshots = List.copyOf(capacityView.snapshotCapacity(
                snapshot.providerId(),
                pattern,
                prototype,
                requestedCrafts,
                patternIdentity,
                snapshot.publicationRevision(),
                snapshot.capacityRevision(),
                validationTick));
        if (publications.publicationRevision() != snapshot.publicationRevision() ||
                CountedCraftingProviderAdapters.mutationRevision() != snapshot.capacityRevision()) {
            return null;
        }
        for (ProviderCapacitySnapshot current : currentSnapshots) {
            validateProviderSnapshot(
                    current,
                    snapshot.providerId(),
                    patternIdentity,
                    snapshot.publicationRevision(),
                    snapshot.capacityRevision());
            if (sameTarget(snapshot, current) && hasCapacity(current)) {
                return provider;
            }
        }
        return null;
    }

    private static void validateCapture(long requestedCrafts, String patternIdentity, long captureTick) {
        if (requestedCrafts <= 0L) {
            throw new IllegalArgumentException("Provider capacity request must be positive");
        }
        if (patternIdentity.isBlank()) {
            throw new IllegalArgumentException("Provider capacity pattern identity must not be blank");
        }
        if (captureTick < 0L) {
            throw new IllegalArgumentException("Provider capacity capture tick must not be negative");
        }
    }

    private static void validateProviderSnapshot(
                                                 ProviderCapacitySnapshot snapshot,
                                                 CraftingProviderId providerId,
                                                 String patternIdentity,
                                                 long publicationRevision,
                                                 long capacityRevision) {
        if (!snapshot.providerId().equals(providerId) ||
                !snapshot.patternIdentity().equals(patternIdentity) ||
                snapshot.publicationRevision() != publicationRevision ||
                snapshot.capacityRevision() != capacityRevision) {
            throw new IllegalStateException("Provider returned a capacity snapshot outside its capture context");
        }
    }

    private static boolean sameTarget(ProviderCapacitySnapshot expected, ProviderCapacitySnapshot current) {
        return expected.route().equals(current.route()) &&
                expected.machineTargetId().equals(current.machineTargetId()) &&
                expected.routingMode() == current.routingMode();
    }

    private static boolean hasCapacity(ProviderCapacitySnapshot snapshot) {
        return !knownZero(snapshot.capacity()) && !knownZero(snapshot.maximumSingleBatch());
    }

    private static boolean knownZero(DispatchCapacity capacity) {
        return capacity instanceof DispatchCapacity.Known(long logicalCrafts) && logicalCrafts == 0L;
    }
}
