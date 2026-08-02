package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderRegistration;
import com.fish_dan_.data_energistics.api.crafting.dispatch.TrinityCountedCraftingDispatch;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CountedCraftingProviderTest {

    @Test
    void directBridgePreservesFallbackTargetRejectionAndRegistrationBoundary() {
        FixedAdmission admission = new FixedAdmission();
        LegacyProvider acceptedProvider = new LegacyProvider(admission);

        CountedCraftingPreparation accepted = acceptedProvider.prepareBatch(
                new TestPattern(),
                new KeyCounter[0],
                1L,
                ignored -> true);

        assertTrue(accepted.accepted());
        assertSame(admission, accepted.admission());
        assertEquals(CraftingDispatchTarget.provider(), accepted.target());
        assertEquals(1, acceptedProvider.prepareCalls);
        assertThrows(
                IllegalArgumentException.class,
                () -> registerAndClose(acceptedProvider, (details, prototype, count) -> admission));

        LegacyProvider unavailableProvider = new LegacyProvider(null);

        CountedCraftingPreparation unavailable = unavailableProvider.prepareBatch(
                new TestPattern(),
                new KeyCounter[0],
                1L,
                ignored -> true);

        assertFalse(unavailable.accepted());
        assertEquals(1, unavailable.rejections().size());
        assertEquals(CraftingDispatchStatus.NO_CAPACITY, unavailable.rejections().getFirst().status());
        assertNull(unavailable.rejections().getFirst().target());

        LegacyProvider excludedProvider = new LegacyProvider(new FixedAdmission());

        CountedCraftingPreparation excluded = excludedProvider.prepareBatch(
                new TestPattern(),
                new KeyCounter[0],
                1L,
                ignored -> false);

        assertFalse(excluded.accepted());
        assertEquals(0, excludedProvider.prepareCalls);
        assertEquals(CraftingDispatchTarget.provider(), excluded.rejections().getFirst().target());
    }

    @Test
    void registeredAdapterUsesProviderIdentityAndUnregisterRestoresGenericSingle() {
        PlainProvider provider = new PlainProvider();
        PlainProvider equalButDistinctProvider = new PlainProvider();
        FixedAdmission admission = new FixedAdmission(3L, true);
        long initialRevision = CountedCraftingProviderAdapters.mutationRevision();
        long[] requested = new long[1];
        CountedCraftingProviderAdapter adapter = (details, prototype, requestedCount) -> {
            requested[0] = requestedCount;
            return admission;
        };
        CountedCraftingProviderRegistration registration =
                TrinityCountedCraftingDispatch.registerAdapter(provider, adapter);
        try (registration) {
            assertEquals(initialRevision + 1L, CountedCraftingProviderAdapters.mutationRevision());
            assertThrows(
                    IllegalStateException.class,
                    () -> registerAndClose(provider, adapter));
            assertEquals(initialRevision + 1L, CountedCraftingProviderAdapters.mutationRevision());

            CountedCraftingPreparation preparation = CountedCraftingProviderAdapters.prepare(
                    provider,
                    new TestPattern(),
                    new KeyCounter[0],
                    5L,
                    ignored -> true);

            assertSame(admission, preparation.admission());
            assertEquals(5L, requested[0]);
            assertEquals(3L, CountedCraftingProviderAdapters.validatedAdmissionCount(
                    provider,
                    admission,
                    5L));
            assertTrue(admission.hasTransferredInputOwnership());
            assertTrue(admission.commit(new KeyCounter[0]));
            assertEquals(1, admission.commitCalls);
            assertEquals(0, provider.pushCalls);

            CountedCraftingPreparation distinctPreparation = CountedCraftingProviderAdapters.prepare(
                    equalButDistinctProvider,
                    new TestPattern(),
                    new KeyCounter[0],
                    5L,
                    ignored -> true);
            CountedCraftingAdmission distinctAdmission = distinctPreparation.admission();
            assertNotNull(distinctAdmission);
            assertEquals(1L, distinctAdmission.count());
            assertTrue(distinctAdmission.commit(new KeyCounter[0]));
            assertEquals(1, equalButDistinctProvider.pushCalls);
        }

        assertEquals(initialRevision + 2L, CountedCraftingProviderAdapters.mutationRevision());
        CountedCraftingPreparation genericPreparation = CountedCraftingProviderAdapters.prepare(
                provider,
                new TestPattern(),
                new KeyCounter[0],
                5L,
                ignored -> true);
        CountedCraftingAdmission genericAdmission = genericPreparation.admission();
        assertNotNull(genericAdmission);
        assertEquals(1L, genericAdmission.count());
        assertTrue(genericAdmission.commit(new KeyCounter[0]));
        assertEquals(1, provider.pushCalls);
        assertThrows(IllegalStateException.class, registration::close);
        assertEquals(initialRevision + 2L, CountedCraftingProviderAdapters.mutationRevision());
    }

    private static void registerAndClose(
            ICraftingProvider provider,
            CountedCraftingProviderAdapter adapter) {
        try (CountedCraftingProviderRegistration ignored =
                TrinityCountedCraftingDispatch.registerAdapter(provider, adapter)) {
            assertNotNull(ignored);
        }
    }

    private static final class LegacyProvider implements CountedCraftingProvider {

        @Nullable
        private final CountedCraftingAdmission admission;
        private int prepareCalls;

        private LegacyProvider(@Nullable CountedCraftingAdmission admission) {
            this.admission = admission;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Provider bridge test never performs physical dispatch");
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public @Nullable CountedCraftingAdmission prepareBatch(
                                                               IPatternDetails patternDetails,
                                                               KeyCounter[] prototype,
                                                               long requestedCount) {
            this.prepareCalls++;
            return this.admission;
        }
    }

    private static final class PlainProvider implements ICraftingProvider {

        private int pushCalls;

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            this.pushCalls++;
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof PlainProvider;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class FixedAdmission implements CountedCraftingAdmission {

        private final long count;
        private final boolean transferredInputOwnership;
        private int commitCalls;

        private FixedAdmission() {
            this(1L, false);
        }

        private FixedAdmission(long count, boolean transferredInputOwnership) {
            this.count = count;
            this.transferredInputOwnership = transferredInputOwnership;
        }

        @Override
        public long count() {
            return this.count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return this.transferredInputOwnership;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            this.commitCalls++;
            return true;
        }
    }

    private static final class TestPattern implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            throw new UnsupportedOperationException("Provider bridge test never inspects pattern definitions");
        }

        @Override
        public IInput[] getInputs() {
            throw new UnsupportedOperationException("Provider bridge test never inspects pattern inputs");
        }

        @Override
        public List<GenericStack> getOutputs() {
            throw new UnsupportedOperationException("Provider bridge test never inspects pattern outputs");
        }
    }
}
