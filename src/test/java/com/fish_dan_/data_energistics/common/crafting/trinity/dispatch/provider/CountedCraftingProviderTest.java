package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityResolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityView;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CountedCraftingProviderTest {

    @Test
    void directBridgePreservesFallbackTargetRejection() {
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

    @ParameterizedTest
    @EnumSource(ProviderRoutingMode.class)
    void capacityResolverPreservesEveryDeclaredRoutingContract(ProviderRoutingMode routingMode) {
        TestPattern pattern = new TestPattern();
        RoutingProvider provider = new RoutingProvider(pattern, routingMode);
        CraftingProviderPublicationIndexImpl publications = new CraftingProviderPublicationIndexImpl();
        publications.publish(provider, List.of(pattern));
        ProviderCapacityResolver resolver = ProviderCapacityResolver.create();

        List<ProviderCapacitySnapshot> snapshots = resolver.capture(
                publications,
                pattern,
                new KeyCounter[0],
                8L,
                "routing-contract",
                1L).snapshots();

        assertEquals(1, snapshots.size());
        ProviderCapacitySnapshot snapshot = snapshots.getFirst();
        assertEquals(routingMode, snapshot.routingMode());
        assertSame(provider, resolver.resolveCurrent(
                publications,
                pattern,
                new KeyCounter[0],
                8L,
                "routing-contract",
                snapshot,
                2L));
    }

    @Test
    void registeredAdapterPublishesCapacityAndPreparesItsExactMachineTarget() {
        TestPattern pattern = new TestPattern();
        RegisteredProvider provider = new RegisteredProvider(pattern);
        RegisteredTargetAdapter adapter = new RegisteredTargetAdapter();
        CountedCraftingProviderAdapters.register(provider, adapter);
        try {
            CraftingProviderPublicationIndexImpl publications = new CraftingProviderPublicationIndexImpl();
            publications.publish(provider, List.of(pattern));
            ProviderCapacityResolver resolver = ProviderCapacityResolver.create();
            ProviderCapacitySnapshot snapshot = resolver.capture(
                    publications,
                    pattern,
                    new KeyCounter[0],
                    8L,
                    "registered-target",
                    1L).snapshots().getFirst();

            assertEquals(ProviderRoutingMode.TARGETED, snapshot.routingMode());
            assertEquals("registered-route", snapshot.route().stableIdentity());
            assertEquals(
                    "shared-machine",
                    snapshot.machineTargetId().orElseThrow().stableIdentity());
            assertEquals(new DispatchCapacity.Known(6L), snapshot.capacity());
            assertEquals(new DispatchCapacity.Known(4L), snapshot.maximumSingleBatch());
            assertSame(provider, resolver.resolveCurrent(
                    publications,
                    pattern,
                    new KeyCounter[0],
                    3L,
                    "registered-target",
                    snapshot,
                    2L));

            CountedCraftingPreparation preparation = CountedCraftingProviderAdapters.prepare(
                    provider,
                    pattern,
                    new KeyCounter[0],
                    3L,
                    snapshot,
                    ignored -> true);

            assertTrue(preparation.accepted());
            assertSame(adapter.admission, preparation.admission());
            assertEquals(snapshot.route(), preparation.target());
            assertEquals(
                    CountedCraftingTarget.machine("registered-route", "shared-machine"),
                    adapter.preparedTarget);
        } finally {
            CountedCraftingProviderAdapters.unregister(provider, adapter);
        }
    }

    @Test
    void publicCapacityRejectsNegativeKnownBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CountedCraftingCapacity(
                        CountedCraftingTarget.provider(),
                        CountedCraftingRoutingMode.AGGREGATE,
                        OptionalLong.of(-1L),
                        OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CountedCraftingCapacity(
                        CountedCraftingTarget.provider(),
                        CountedCraftingRoutingMode.AGGREGATE,
                        OptionalLong.empty(),
                        OptionalLong.of(-1L)));
    }

    @Test
    void registeredCapacityFailureIsolatedToItsProvider() {
        TestPattern pattern = new TestPattern();
        RegisteredProvider provider = new RegisteredProvider(pattern);
        CountedCraftingProviderAdapter adapter = new CountedCraftingProviderAdapter() {

            @Override
            public @Nullable CountedCraftingAdmission prepareBatch(
                    @NotNull IPatternDetails patternDetails,
                    KeyCounter @NotNull [] prototype,
                    long requestedCount) {
                return null;
            }

            @Override
            public @NotNull List<@NotNull CountedCraftingCapacity> captureCapacity(
                    @NotNull IPatternDetails patternDetails,
                    KeyCounter @NotNull [] prototype,
                    long requestedCount) {
                throw new IllegalStateException("broken registered capacity adapter");
            }
        };
        CountedCraftingProviderAdapters.register(provider, adapter);
        try {
            CraftingProviderPublicationIndexImpl publications = new CraftingProviderPublicationIndexImpl();
            publications.publish(provider, List.of(pattern));

            assertTrue(ProviderCapacityResolver.create()
                    .capture(
                            publications,
                            pattern,
                            new KeyCounter[0],
                            1L,
                            "broken-registered-adapter",
                            1L)
                    .snapshots()
                    .isEmpty());
        } finally {
            CountedCraftingProviderAdapters.unregister(provider, adapter);
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
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype,
                long requestedCount) {
            this.prepareCalls++;
            return this.admission;
        }
    }

    private static final class RoutingProvider implements ICraftingProvider, ProviderCapacityView {

        private final IPatternDetails pattern;
        private final ProviderRoutingMode routingMode;

        private RoutingProvider(IPatternDetails pattern, ProviderRoutingMode routingMode) {
            this.pattern = pattern;
            this.routingMode = routingMode;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Capacity routing contract does not perform physical dispatch");
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public List<ProviderCapacitySnapshot> snapshotCapacity(
                CraftingProviderId providerId,
                IPatternDetails patternDetails,
                KeyCounter[] prototype,
                long requestedCrafts,
                String patternIdentity,
                long publicationRevision,
                long capacityRevision,
                long captureTick) {
            CraftingDispatchTarget route = this.routingMode == ProviderRoutingMode.TARGETED ?
                    new CraftingDispatchTarget("test-target") :
                    CraftingDispatchTarget.provider();
            DispatchCapacity capacity = this.routingMode == ProviderRoutingMode.TARGETED ?
                    new DispatchCapacity.Known(requestedCrafts) :
                    DispatchCapacity.Unknown.INSTANCE;
            return List.of(new ProviderCapacitySnapshot(
                    providerId,
                    route,
                    Optional.empty(),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    this.routingMode,
                    capacity,
                    new DispatchCapacity.Known(this.routingMode == ProviderRoutingMode.AGGREGATE ?
                            requestedCrafts : 1L)));
        }
    }

    private static final class RegisteredProvider implements ICraftingProvider {

        private final IPatternDetails pattern;

        private RegisteredProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Registered adapter test never performs physical dispatch");
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class RegisteredTargetAdapter implements CountedCraftingProviderAdapter {

        private final FixedAdmission admission = new FixedAdmission(3L, false);
        @Nullable
        private CountedCraftingTarget preparedTarget;

        @Override
        public @Nullable CountedCraftingAdmission prepareBatch(
                IPatternDetails patternDetails,
                KeyCounter[] prototype,
                long requestedCount) {
            throw new AssertionError("Targeted adapter must be prepared through its captured route");
        }

        @Override
        public @NotNull List<@NotNull CountedCraftingCapacity> captureCapacity(
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype,
                long requestedCount) {
            return List.of(new CountedCraftingCapacity(
                    CountedCraftingTarget.machine("registered-route", "shared-machine"),
                    CountedCraftingRoutingMode.TARGETED,
                    OptionalLong.of(6L),
                    OptionalLong.of(4L)));
        }

        @Override
        public @NotNull CountedCraftingAdmission prepareBatchForTarget(
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype,
                long requestedCount,
                @NotNull CountedCraftingTarget target) {
            this.preparedTarget = target;
            return this.admission;
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
