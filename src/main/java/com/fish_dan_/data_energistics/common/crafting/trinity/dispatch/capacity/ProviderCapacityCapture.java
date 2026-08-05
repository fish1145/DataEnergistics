package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.List;

/**
 * Immutable capacity epoch containing its complete semantic cache identity and provider observations.
 *
 * @param key       exact server-thread capture context
 * @param snapshots immutable capacity observations in provider publication order
 */
public record ProviderCapacityCapture(
                                      ProviderCapacityCaptureKey key,
                                      List<ProviderCapacitySnapshot> snapshots) {

    public ProviderCapacityCapture {
        if (key == null) {
            throw new IllegalArgumentException("Provider capacity capture requires an immutable key");
        }
        snapshots = List.copyOf(snapshots);
        for (ProviderCapacitySnapshot snapshot : snapshots) {
            if (snapshot.providerId().publicationScope() != key.gridScope() ||
                    snapshot.publicationRevision() != key.publicationRevision() ||
                    snapshot.capacityRevision() != key.capacityRevision() ||
                    snapshot.captureTick() != key.capacityEpoch() ||
                    !snapshot.patternIdentity().equals(key.patternIdentity())) {
                throw new IllegalArgumentException("Provider capacity snapshot disagrees with its capture key");
            }
        }
    }
}
