package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Identity-preserving publication index owned by one AE2 {@code NetworkCraftingProviders} instance.
 *
 * <p>
 * The implementation is deliberately unsynchronized: AE2 publication lifecycle and every live-provider resolution
 * occur on the owning server thread. Immutable IDs and copied ID lists may be transferred to pure planning code.
 * </p>
 */
public final class CraftingProviderPublicationIndexImpl implements CraftingProviderPublicationIndex {

    /**
     * Allocates a process-local namespace for each grid publication index.
     */
    private static final AtomicLong PUBLICATION_SCOPE_SEQUENCE = new AtomicLong();

    /**
     * Namespace preventing IDs from different grid indices from comparing equal.
     */
    private final long publicationScope = allocatePublicationScope();

    /**
     * Live publications retained only inside the server-thread index.
     */
    private final Map<CraftingProviderId, LivePublication> publications = new LinkedHashMap<>();

    /**
     * Identity-keyed immutable lookup rebuilt after each lifecycle mutation.
     */
    private Map<IPatternDetails, List<CraftingProviderId>> providerIdsByPattern = Map.of();

    /**
     * Last registration sequence allocated in this publication scope.
     */
    private long registrationSequence;

    /**
     * Monotonic index revision used to reject stale capacity observations.
     */
    private long revision;

    /**
     * Publishes the exact pattern snapshot captured by AE2 for one provider registration.
     *
     * @param provider live provider retained for later server-thread resolution
     * @param patterns exact immutable-by-convention AE2 publication contents
     * @return new ID valid until {@link #unpublish(CraftingProviderId)}
     */
    public CraftingProviderId publish(ICraftingProvider provider, List<IPatternDetails> patterns) {
        if (provider == null) {
            throw new IllegalArgumentException("Published crafting provider must not be null");
        }
        if (patterns == null) {
            throw new IllegalArgumentException("Published crafting patterns must not be null");
        }
        // Copying also rejects a malformed null pattern before any index state changes.
        List<IPatternDetails> patternSnapshot = List.copyOf(patterns);
        long nextRegistrationSequence = Math.incrementExact(this.registrationSequence);
        long nextRevision = Math.incrementExact(this.revision);
        CraftingProviderId providerId = new CraftingProviderId(
                this.publicationScope,
                nextRegistrationSequence);

        this.publications.put(providerId, new LivePublication(provider, patternSnapshot));
        this.providerIdsByPattern = rebuildPatternIndex(this.publications);
        this.registrationSequence = nextRegistrationSequence;
        this.revision = nextRevision;
        return providerId;
    }

    /**
     * Removes one exact publication and makes its ID stale.
     *
     * @param providerId current ID returned by {@link #publish(ICraftingProvider, List)}
     */
    public void unpublish(CraftingProviderId providerId) {
        if (!this.publications.containsKey(providerId)) {
            throw new IllegalStateException("Crafting provider publication is not current: " + providerId);
        }
        long nextRevision = Math.incrementExact(this.revision);
        this.publications.remove(providerId);
        this.providerIdsByPattern = rebuildPatternIndex(this.publications);
        this.revision = nextRevision;
    }

    @Override
    public long publicationRevision() {
        return this.revision;
    }

    @Override
    public List<CraftingProviderId> providerIdsFor(IPatternDetails patternIdentity) {
        List<CraftingProviderId> providerIds = this.providerIdsByPattern.get(patternIdentity);
        return providerIds == null ? List.of() : providerIds;
    }

    @Override
    @Nullable
    public ICraftingProvider resolveLiveProvider(CraftingProviderId providerId) {
        LivePublication publication = this.publications.get(providerId);
        return publication == null ? null : publication.provider();
    }

    /**
     * Allocates the non-persistent namespace used by one index instance.
     */
    private static long allocatePublicationScope() {
        return PUBLICATION_SCOPE_SEQUENCE.updateAndGet(Math::incrementExact);
    }

    /**
     * Rebuilds an identity-keyed immutable view while retaining publication and pattern-list multiplicity.
     */
    private static Map<IPatternDetails, List<CraftingProviderId>> rebuildPatternIndex(
                                                                                      Map<CraftingProviderId, LivePublication> publications) {
        IdentityHashMap<IPatternDetails, List<CraftingProviderId>> mutableIndex = new IdentityHashMap<>();
        for (Map.Entry<CraftingProviderId, LivePublication> entry : publications.entrySet()) {
            for (IPatternDetails pattern : entry.getValue().patterns()) {
                mutableIndex.computeIfAbsent(pattern, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }

        IdentityHashMap<IPatternDetails, List<CraftingProviderId>> immutableIndex = new IdentityHashMap<>();
        mutableIndex.forEach((pattern, providerIds) -> immutableIndex.put(pattern, List.copyOf(providerIds)));
        return Collections.unmodifiableMap(immutableIndex);
    }

    /**
     * Live publication facts that never leave the owning server-thread index.
     */
    private record LivePublication(ICraftingProvider provider, List<IPatternDetails> patterns) {}
}
