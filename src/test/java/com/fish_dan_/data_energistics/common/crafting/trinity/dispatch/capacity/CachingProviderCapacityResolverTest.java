package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class CachingProviderCapacityResolverTest {

    @Test
    void cachesCompleteCaptureContextButAlwaysRevalidatesLiveProviders() {
        TrinityComputationCache cache = TrinityComputationCache.create(Runnable::run, 16);
        RecordingResolver delegate = new RecordingResolver();
        ProviderCapacityResolver resolver = new CachingProviderCapacityResolver(delegate, () -> cache);
        TestPublications publications = new TestPublications();
        IPatternDetails pattern = new TestPattern();
        KeyCounter[] prototype = { new KeyCounter() };
        try {
            ProviderCapacityCapture first = resolver.capture(
                    publications,
                    pattern,
                    prototype,
                    8L,
                    "pattern",
                    40L);
            ProviderCapacityCapture repeated = resolver.capture(
                    publications,
                    pattern,
                    prototype,
                    8L,
                    "pattern",
                    40L);
            resolver.capture(publications, pattern, prototype, 9L, "pattern", 40L);
            resolver.capture(publications, pattern, prototype, 8L, "pattern", 41L);

            assertSame(first, repeated);
            assertEquals(3, delegate.captureCalls.get());
            assertSame(delegate.provider, resolver.resolveCurrent(
                    publications,
                    pattern,
                    prototype,
                    1L,
                    "pattern",
                    first.snapshots().getFirst(),
                    40L));
            assertSame(delegate.provider, resolver.resolveCurrent(
                    publications,
                    pattern,
                    prototype,
                    1L,
                    "pattern",
                    first.snapshots().getFirst(),
                    40L));
            assertEquals(2, delegate.resolveCalls.get());
        } finally {
            cache.close();
        }
    }

    private static final class RecordingResolver implements ProviderCapacityResolver {

        private final ICraftingProvider provider = new TestProvider();
        private final AtomicInteger captureCalls = new AtomicInteger();
        private final AtomicInteger resolveCalls = new AtomicInteger();

        @Override
        public ProviderCapacityCapture capture(CraftingProviderPublicationIndex publications,
                                               IPatternDetails pattern,
                                               KeyCounter[] prototype,
                                               long requestedCrafts,
                                               String patternIdentity,
                                               long captureTick) {
            this.captureCalls.incrementAndGet();
            ProviderCapacityCaptureKey key = ProviderCapacityCaptureKey.capture(
                    publications,
                    pattern,
                    prototype,
                    requestedCrafts,
                    patternIdentity,
                    captureTick);
            return new ProviderCapacityCapture(key, List.of(new ProviderCapacitySnapshot(
                    publications.providerIdsFor(pattern).getFirst(),
                    CraftingDispatchTarget.provider(),
                    Optional.empty(),
                    patternIdentity,
                    publications.publicationRevision(),
                    CountedCraftingProviderAdapters.mutationRevision(),
                    captureTick,
                    ProviderRoutingMode.TARGETED,
                    new DispatchCapacity.Known(requestedCrafts),
                    new DispatchCapacity.Known(requestedCrafts))));
        }

        @Override
        public ICraftingProvider resolveCurrent(CraftingProviderPublicationIndex publications,
                                                IPatternDetails pattern,
                                                KeyCounter[] prototype,
                                                long requestedCrafts,
                                                String patternIdentity,
                                                ProviderCapacitySnapshot snapshot,
                                                long validationTick) {
            this.resolveCalls.incrementAndGet();
            return this.provider;
        }
    }

    private static final class TestPublications implements CraftingProviderPublicationIndex {

        private final CraftingProviderId providerId = new CraftingProviderId(1L, 1L);

        @Override
        public long publicationScope() {
            return 1L;
        }

        @Override
        public long publicationRevision() {
            return 1L;
        }

        @Override
        public List<CraftingProviderId> providerIdsFor(IPatternDetails patternIdentity) {
            return List.of(this.providerId);
        }

        @Override
        public ICraftingProvider resolveLiveProvider(CraftingProviderId providerId) {
            throw new UnsupportedOperationException("The cache behavior test uses its recording resolver");
        }
    }

    private static final class TestProvider implements ICraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("The cache behavior test never submits provider work");
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class TestPattern implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }
}
