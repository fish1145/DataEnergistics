package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessImpl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerEnergyDistributorImplTest {

    private static final BlockPos FIRST_POS = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND_POS = new BlockPos(2, 0, 0);
    private static final BlockPos RECEIVER_POS = new BlockPos(3, 0, 0);

    @Test
    void drainsTheFrozenQuotaEvenWhenEachOperationCanMoveOnlyOneFe() {
        TestEnergyStorage source = TestEnergyStorage.source(12, 1);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(12, 1);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)));

        distributor.performActiveRangeTransfer();

        assertEquals(0L, source.stored());
        assertEquals(12L, receiver.stored());
        assertEquals(12, source.realExtractCalls());
        assertEquals(12, receiver.realInsertCalls());
        assertTrue(receiver.realInsertCalls() > 5);
    }

    @Test
    void repeatedlyCallsStandardCapabilitiesWhenNoUnlimitedPlanIsAvailable() {
        TestEnergyStorage source = TestEnergyStorage.source(12, 1);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(12, 1);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)),
                new FallbackUnlimitedEnergyAccess());

        distributor.performActiveRangeTransfer();

        assertEquals(0L, source.stored());
        assertEquals(12L, receiver.stored());
        assertEquals(12, source.realExtractCalls());
        assertEquals(12, receiver.realInsertCalls());
    }

    @Test
    void doesNotFallbackAfterADirectReceiverMutationCannotBeRolledBack() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        RollbackFailingReceiver receiver = new RollbackFailingReceiver(10L);
        TestContext context = new TestContext();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)),
                new UnlimitedEnergyAccessImpl(),
                context);

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(10L, source.stored());
        assertEquals(10L, receiver.stored());
        assertEquals(0L, context.bufferedTransferEnergy());
        assertEquals(2, receiver.directInsertCalls());
        assertEquals(1, receiver.rollbackAttempts());
        assertEquals(0, receiver.capabilityReceiveCalls());
    }

    @Test
    void freezesBidirectionalSourceQuotasBeforeEnergyStartsCirculating() {
        TestEnergyStorage first = TestEnergyStorage.bidirectional(10, 100);
        TestEnergyStorage second = TestEnergyStorage.bidirectional(5, 100);
        TowerEnergyEndpoint firstEndpoint = endpoint(FIRST_POS, first);
        TowerEnergyEndpoint secondEndpoint = endpoint(SECOND_POS, second);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(firstEndpoint, secondEndpoint),
                List.of(firstEndpoint, secondEndpoint));

        distributor.performActiveRangeTransfer();

        assertEquals(5L, first.stored());
        assertEquals(10L, second.stored());
        assertEquals(10L, first.realExtracted());
        assertEquals(5L, second.realExtracted());
        assertEquals(15L, first.realExtracted() + second.realExtracted());
    }

    @Test
    void transfersAppFluxAmountsBeyondIntegerWidthWithoutClamping() {
        long amount = 4_000_000_000L;
        TestGridEnergyAccess gridEnergy = new TestGridEnergyAccess(amount);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(amount, Long.MAX_VALUE);
        TowerEnergyDistributorImpl distributor = new TowerEnergyDistributorImpl(
                new TestContext(),
                new TestEndpointResolver(List.of(), List.of(endpoint(RECEIVER_POS, receiver))),
                new TestUnlimitedEnergyAccess(),
                true,
                gridEnergy);

        distributor.performActiveRangeTransfer();

        assertEquals(0L, gridEnergy.stored());
        assertEquals(amount, receiver.stored());
        assertEquals(amount, gridEnergy.realExtracted());
        assertEquals(1, gridEnergy.realExtractCalls());
        assertEquals(2, gridEnergy.simulatedExtractCalls());
    }

    @Test
    void restoresUndeliveredAppFluxEnergyWithoutIntegerClamping() {
        long amount = 4_000_000_000L;
        long accepted = 1_000_000_000L;
        TestGridEnergyAccess gridEnergy = new TestGridEnergyAccess(amount);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(amount, accepted);
        receiver.reportFullInsertionDuringSimulation();
        TowerEnergyDistributorImpl distributor = new TowerEnergyDistributorImpl(
                new TestContext(),
                new TestEndpointResolver(List.of(), List.of(endpoint(RECEIVER_POS, receiver))),
                new TestUnlimitedEnergyAccess(),
                true,
                gridEnergy);

        distributor.performActiveRangeTransfer();

        assertEquals(amount - accepted, gridEnergy.stored());
        assertEquals(accepted, receiver.stored());
        assertEquals(amount - accepted, gridEnergy.restored());
        assertEquals(1, gridEnergy.restoreCalls());
    }

    @Test
    void stopsWhenReceiversCannotMakeProgress() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, 0);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)));

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(10L, source.stored());
        assertEquals(0L, receiver.stored());
        assertEquals(1, receiver.simulatedInsertCalls());
        assertEquals(0, source.realExtractCalls());
    }

    @Test
    void rollsBackUndeliveredEnergyWhenRealReceiverShortWrites() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, 3);
        receiver.reportFullInsertionDuringSimulation();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)));

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(7L, source.stored());
        assertEquals(3L, receiver.stored());
        assertEquals(10L, source.stored() + receiver.stored());
        assertEquals(1, source.realExtractCalls());
        assertEquals(1, receiver.realInsertCalls());
        assertEquals(1, source.rollbackCalls());
        assertEquals(7L, source.rolledBack());
    }

    @Test
    void rollsBackEntireExtractionWhenRealReceiverMakesNoProgress() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, 0);
        receiver.reportFullInsertionDuringSimulation();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)));

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(10L, source.stored());
        assertEquals(0L, receiver.stored());
        assertEquals(10L, source.stored() + receiver.stored());
        assertEquals(1, source.realExtractCalls());
        assertEquals(1, receiver.realInsertCalls());
        assertEquals(1, source.rollbackCalls());
        assertEquals(10L, source.rolledBack());
    }

    @Test
    void escrowsFallbackSourceShortWriteAndFlushesItOnTheNextTick() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, 3);
        receiver.reportFullInsertionDuringSimulation();
        receiver.stopRealInsertionAfterFirstAttempt();
        TestContext context = new TestContext();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)),
                new FallbackUnlimitedEnergyAccess(),
                context);

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(0L, source.stored());
        assertEquals(3L, receiver.stored());
        assertEquals(7L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());

        receiver.acceptAllRealInsertions();
        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(0L, source.stored());
        assertEquals(10L, receiver.stored());
        assertEquals(0L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());
        assertEquals(1, source.realExtractCalls());
    }

    @Test
    void escrowsFallbackSourceZeroWriteAndFlushesItOnTheNextTick() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, 0);
        receiver.reportFullInsertionDuringSimulation();
        TestContext context = new TestContext();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)),
                new FallbackUnlimitedEnergyAccess(),
                context);

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(0L, source.stored());
        assertEquals(0L, receiver.stored());
        assertEquals(10L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());

        receiver.acceptAllRealInsertions();
        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(0L, source.stored());
        assertEquals(10L, receiver.stored());
        assertEquals(0L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());
        assertEquals(1, source.realExtractCalls());
    }

    @Test
    void preservesCompletedFallbackExtractionWhenTheSecondRealSegmentThrows() {
        TestEnergyStorage source = TestEnergyStorage.source(10, 5);
        source.throwOnRealExtractionAttempt(2);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, Long.MAX_VALUE);
        TestContext context = new TestContext();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)),
                new FallbackUnlimitedEnergyAccess(),
                context);

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(5L, source.stored());
        assertEquals(5L, receiver.stored());
        assertEquals(0L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());
        assertEquals(2, source.realExtractAttempts());
        assertEquals(1, source.realExtractCalls());
        assertEquals(List.of(5L, 0L), context.bufferedTransferHistory());
    }

    @Test
    void escrowsCompletedFallbackInsertionWhenTheSecondRealSegmentThrows() {
        TestEnergyStorage source = TestEnergyStorage.source(10, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(10, 5);
        receiver.throwOnRealInsertionAttempt(2);
        TestContext context = new TestContext();
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, source)),
                List.of(endpoint(RECEIVER_POS, receiver)),
                new FallbackUnlimitedEnergyAccess(),
                context);

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(0L, source.stored());
        assertEquals(5L, receiver.stored());
        assertEquals(5L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());
        assertEquals(1, source.realExtractCalls());
        assertEquals(2, receiver.realInsertAttempts());
        assertEquals(List.of(10L, 5L), context.bufferedTransferHistory());

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(0L, source.stored());
        assertEquals(10L, receiver.stored());
        assertEquals(0L, context.bufferedTransferEnergy());
        assertEquals(10L, source.stored() + receiver.stored() + context.bufferedTransferEnergy());
        assertEquals(1, source.realExtractCalls());
    }

    @Test
    void exposesAndExtractsEscrowWithoutAnyEnergyEndpoints() {
        TestContext context = new TestContext();
        context.setBufferedTransferEnergy(10L);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(),
                List.of(),
                new FallbackUnlimitedEnergyAccess(),
                context);

        assertEquals(10L, distributor.getTotalExtractableEnergy(null));
        assertTrue(distributor.hasAnySource(null));
        assertEquals(4, distributor.extractEnergyFromRange(4, true, null));
        assertEquals(10L, context.bufferedTransferEnergy());

        assertEquals(4, distributor.extractEnergyFromRange(4, false, null));
        assertEquals(6L, distributor.getTotalExtractableEnergy(null));
        assertTrue(distributor.hasAnySource(null));
        assertEquals(6, distributor.extractEnergyFromRange(10, false, null));
        assertEquals(0L, distributor.getTotalExtractableEnergy(null));
        assertFalse(distributor.hasAnySource(null));
        assertEquals(0L, context.bufferedTransferEnergy());
    }

    @Test
    void isolatesAFailingSourceAndContinuesWithOtherEndpoints() {
        TestEnergyStorage failingSource = TestEnergyStorage.source(4, Long.MAX_VALUE);
        failingSource.failExtraction();
        TestEnergyStorage healthySource = TestEnergyStorage.source(7, Long.MAX_VALUE);
        TestEnergyStorage receiver = TestEnergyStorage.receiver(11, Long.MAX_VALUE);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, failingSource), endpoint(SECOND_POS, healthySource)),
                List.of(endpoint(RECEIVER_POS, receiver)));

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(4L, failingSource.stored());
        assertEquals(0L, healthySource.stored());
        assertEquals(7L, receiver.stored());
        assertEquals(1, failingSource.extractAttempts());
    }

    @Test
    void stallsBrokenReceiversAndContinuesWithHealthyEndpoints() {
        TestEnergyStorage firstSource = TestEnergyStorage.source(4, Long.MAX_VALUE);
        TestEnergyStorage secondSource = TestEnergyStorage.source(7, Long.MAX_VALUE);
        TestEnergyStorage throwingReceiver = TestEnergyStorage.receiver(11, Long.MAX_VALUE);
        throwingReceiver.failInsertion();
        TestEnergyStorage invalidReceiver = TestEnergyStorage.receiver(11, Long.MAX_VALUE);
        invalidReceiver.returnInvalidInsertion();
        TestEnergyStorage zeroProgressReceiver = TestEnergyStorage.receiver(11, 0);
        TestEnergyStorage healthyReceiver = TestEnergyStorage.receiver(11, Long.MAX_VALUE);
        TowerEnergyDistributorImpl distributor = createDistributor(
                List.of(endpoint(FIRST_POS, firstSource), endpoint(SECOND_POS, secondSource)),
                List.of(
                        endpoint(new BlockPos(3, 0, 0), throwingReceiver),
                        endpoint(new BlockPos(4, 0, 0), invalidReceiver),
                        endpoint(new BlockPos(5, 0, 0), zeroProgressReceiver),
                        endpoint(new BlockPos(6, 0, 0), healthyReceiver)));

        assertDoesNotThrow(distributor::performActiveRangeTransfer);

        assertEquals(11L, healthyReceiver.stored());
        assertEquals(1, throwingReceiver.insertAttempts());
        assertEquals(1, invalidReceiver.insertAttempts());
        assertEquals(1, zeroProgressReceiver.insertAttempts());
    }

    private static TowerEnergyDistributorImpl createDistributor(List<TowerEnergyEndpoint> extractEndpoints,
                                                                List<TowerEnergyEndpoint> receiveEndpoints) {
        return createDistributor(extractEndpoints, receiveEndpoints, new TestUnlimitedEnergyAccess());
    }

    private static TowerEnergyDistributorImpl createDistributor(List<TowerEnergyEndpoint> extractEndpoints,
                                                                List<TowerEnergyEndpoint> receiveEndpoints,
                                                                UnlimitedEnergyAccess unlimitedEnergyAccess) {
        return createDistributor(extractEndpoints, receiveEndpoints, unlimitedEnergyAccess, new TestContext());
    }

    private static TowerEnergyDistributorImpl createDistributor(List<TowerEnergyEndpoint> extractEndpoints,
                                                                List<TowerEnergyEndpoint> receiveEndpoints,
                                                                UnlimitedEnergyAccess unlimitedEnergyAccess,
                                                                TestContext context) {
        return new TowerEnergyDistributorImpl(
                context,
                new TestEndpointResolver(extractEndpoints, receiveEndpoints),
                unlimitedEnergyAccess,
                false);
    }

    private static TowerEnergyEndpoint endpoint(BlockPos pos, IEnergyStorage storage) {
        return new TowerEnergyEndpoint(pos, Direction.NORTH, storage);
    }

    private static final class TestContext implements TowerEnergyDistributorContext {

        private long bufferedTransferEnergy;
        private final List<Long> bufferedTransferHistory = new ArrayList<>();

        @Override
        public @Nullable Level level() {
            return null;
        }

        @Override
        public boolean isTowerActive() {
            return true;
        }

        @Override
        public AENetworkedBlockEntity aeNetworkHost() {
            return null;
        }

        @Override
        public void markEndpointChanged(BlockPos pos) {}

        @Override
        public long bufferedTransferEnergy() {
            return this.bufferedTransferEnergy;
        }

        @Override
        public void setBufferedTransferEnergy(long amount) {
            this.bufferedTransferEnergy = amount;
            this.bufferedTransferHistory.add(amount);
        }

        private List<Long> bufferedTransferHistory() {
            return List.copyOf(this.bufferedTransferHistory);
        }

        @Override
        public void recordMaxExtractEndpoints(int endpointCount) {}

        @Override
        public void recordMaxReceiveEndpoints(int endpointCount) {}

        @Override
        public void recordSimulatedCacheHit() {}

        @Override
        public void recordSimulatedCacheMiss() {}
    }

    private static final class TestEndpointResolver implements TowerEnergyEndpointResolver {

        private final List<TowerEnergyEndpoint> extractEndpoints;
        private final List<TowerEnergyEndpoint> receiveEndpoints;

        private TestEndpointResolver(List<TowerEnergyEndpoint> extractEndpoints,
                                     List<TowerEnergyEndpoint> receiveEndpoints) {
            this.extractEndpoints = List.copyOf(extractEndpoints);
            this.receiveEndpoints = List.copyOf(receiveEndpoints);
        }

        @Override
        public @Nullable IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable Direction side) {
            return null;
        }

        @Override
        public @Nullable IEnergyStorage findAccessibleEnergyStorage(BlockPos pos, boolean forReceive) {
            return null;
        }

        @Override
        public List<TowerEnergyEndpoint> findAccessibleEnergyEndpoints(BlockPos pos, boolean forReceive) {
            return List.of();
        }

        @Override
        public List<TowerEnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos) {
            List<TowerEnergyEndpoint> candidates = endpoints(forReceive);
            if (excludedPos == null) {
                return candidates;
            }

            ArrayList<TowerEnergyEndpoint> filtered = new ArrayList<>(candidates.size());
            for (TowerEnergyEndpoint endpoint : candidates) {
                if (!excludedPos.equals(endpoint.pos())) {
                    filtered.add(endpoint);
                }
            }
            return List.copyOf(filtered);
        }

        @Override
        public List<TowerEnergyEndpoint> collectEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers,
                                                                boolean forReceive) {
            return endpoints(forReceive);
        }

        @Override
        public List<TowerEnergyEndpoint> collectClusterEnergyEndpoints(boolean forReceive) {
            return endpoints(forReceive);
        }

        @Override
        public List<TowerEnergyEndpoint> getCachedResolvedEnergyEndpoints(boolean forReceive) {
            return endpoints(forReceive);
        }

        @Override
        public @Nullable BlockPos normalizeExtractExcludedPos(@Nullable BlockPos excludedPos) {
            return excludedPos;
        }

        @Override
        public @Nullable BlockPos normalizeReceiveExcludedPos(@Nullable BlockPos excludedPos) {
            return excludedPos;
        }

        @Override
        public boolean canReceiveEnergy(@Nullable IEnergyStorage storage) {
            return storage != null && storage.canReceive();
        }

        @Override
        public void invalidateResolvedCache() {}

        @Override
        public void clearReusableCache() {}

        private List<TowerEnergyEndpoint> endpoints(boolean forReceive) {
            return forReceive ? this.receiveEndpoints : this.extractEndpoints;
        }
    }

    private static final class TestGridEnergyAccess implements TowerGridEnergyAccess {

        private long stored;
        private long realExtracted;
        private long restored;
        private int simulatedExtractCalls;
        private int realExtractCalls;
        private int restoreCalls;

        private TestGridEnergyAccess(long stored) {
            this.stored = stored;
        }

        @Override
        public long extract(AENetworkedBlockEntity tower, long amount, boolean simulate) {
            long extracted = Math.min(amount, this.stored);
            if (simulate) {
                this.simulatedExtractCalls++;
            } else {
                this.realExtractCalls++;
                this.realExtracted += extracted;
                this.stored -= extracted;
            }
            return extracted;
        }

        @Override
        public long restore(AENetworkedBlockEntity tower, long amount) {
            this.restoreCalls++;
            this.restored += amount;
            this.stored += amount;
            return amount;
        }

        private long stored() {
            return this.stored;
        }

        private long realExtracted() {
            return this.realExtracted;
        }

        private long restored() {
            return this.restored;
        }

        private int simulatedExtractCalls() {
            return this.simulatedExtractCalls;
        }

        private int realExtractCalls() {
            return this.realExtractCalls;
        }

        private int restoreCalls() {
            return this.restoreCalls;
        }
    }

    private static final class TestUnlimitedEnergyAccess implements UnlimitedEnergyAccess {

        @Override
        public long stored(IEnergyStorage storage) {
            return testStorage(storage).stored();
        }

        @Override
        public long capacity(IEnergyStorage storage) {
            return testStorage(storage).capacity();
        }

        @Override
        public boolean canReceive(IEnergyStorage storage) {
            return testStorage(storage).canReceive();
        }

        @Override
        public boolean canExtract(IEnergyStorage storage) {
            return testStorage(storage).canExtract();
        }

        @Override
        public long insert(IEnergyStorage storage, long amount, boolean simulate) {
            return testStorage(storage).insert(amount, simulate);
        }

        @Override
        public long extract(IEnergyStorage storage, long amount, boolean simulate) {
            return testStorage(storage).extract(amount, simulate);
        }

        @Override
        public long rollbackExtraction(IEnergyStorage storage, long amount) {
            return testStorage(storage).rollbackExtraction(amount);
        }

        @Override
        public void notifyStorageChanged(IEnergyStorage storage) {
            testStorage(storage).recordNotification();
        }

        private static TestEnergyStorage testStorage(IEnergyStorage storage) {
            if (storage instanceof TestEnergyStorage testStorage) {
                return testStorage;
            }
            throw new IllegalArgumentException("Unexpected test storage: " + storage.getClass().getName());
        }
    }

    private static final class FallbackUnlimitedEnergyAccess implements UnlimitedEnergyAccess {

        @Override
        public long stored(IEnergyStorage storage) {
            return storage.getEnergyStored();
        }

        @Override
        public long capacity(IEnergyStorage storage) {
            return storage.getMaxEnergyStored();
        }

        @Override
        public boolean canReceive(IEnergyStorage storage) {
            return storage.canReceive();
        }

        @Override
        public boolean canExtract(IEnergyStorage storage) {
            return storage.canExtract();
        }

        @Override
        public long insert(IEnergyStorage storage, long amount, boolean simulate) {
            return UNAVAILABLE;
        }

        @Override
        public long extract(IEnergyStorage storage, long amount, boolean simulate) {
            return UNAVAILABLE;
        }

        @Override
        public long rollbackExtraction(IEnergyStorage storage, long amount) {
            return UNAVAILABLE;
        }

        @Override
        public void notifyStorageChanged(IEnergyStorage storage) {
            throw new IllegalStateException("Fallback capability mutations must notify themselves");
        }
    }

    private static final class RollbackFailingReceiver implements IEnergyStorage {

        private long amount;
        private final long capacity;
        private int directInsertCalls;
        private int rollbackAttempts;
        private int capabilityReceiveCalls;

        private RollbackFailingReceiver(long capacity) {
            this.capacity = capacity;
        }

        private long getAmount() {
            return this.amount;
        }

        private long getCapacity() {
            return this.capacity;
        }

        private void setAmount(long amount) {
            this.rollbackAttempts++;
            throw new IllegalStateException("Deliberate rollback failure for " + amount + " FE");
        }

        private long insertIgnoringLimit(long amount, boolean simulate) {
            this.directInsertCalls++;
            if (simulate) {
                return amount;
            }
            this.amount += amount;
            return amount + 1L;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            this.capabilityReceiveCalls++;
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) this.amount;
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) this.capacity;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        private long stored() {
            return this.amount;
        }

        private int directInsertCalls() {
            return this.directInsertCalls;
        }

        private int rollbackAttempts() {
            return this.rollbackAttempts;
        }

        private int capabilityReceiveCalls() {
            return this.capabilityReceiveCalls;
        }
    }

    private static final class TestEnergyStorage implements IEnergyStorage {

        private long stored;
        private final long capacity;
        private final boolean receiveAllowed;
        private final boolean extractAllowed;
        private long maxInsert;
        private final long maxExtract;
        private boolean failInsertion;
        private boolean invalidInsertion;
        private boolean failExtraction;
        private boolean fullInsertionDuringSimulation;
        private boolean stopRealInsertionAfterFirstAttempt;
        private int throwingRealInsertionAttempt;
        private int throwingRealExtractionAttempt;
        private int insertAttempts;
        private int simulatedInsertCalls;
        private int realInsertCalls;
        private int realInsertAttempts;
        private int realExtractCalls;
        private int realExtractAttempts;
        private int extractAttempts;
        private long realExtracted;
        private int rollbackCalls;
        private long rolledBack;
        private int notifications;

        private TestEnergyStorage(long stored, long capacity, boolean receiveAllowed, boolean extractAllowed,
                                  long maxInsert, long maxExtract) {
            this.stored = stored;
            this.capacity = capacity;
            this.receiveAllowed = receiveAllowed;
            this.extractAllowed = extractAllowed;
            this.maxInsert = maxInsert;
            this.maxExtract = maxExtract;
        }

        private static TestEnergyStorage source(long stored, long maxExtract) {
            return new TestEnergyStorage(stored, stored, false, true, 0, maxExtract);
        }

        private static TestEnergyStorage receiver(long capacity, long maxInsert) {
            return new TestEnergyStorage(0, capacity, true, false, maxInsert, 0);
        }

        private static TestEnergyStorage bidirectional(long stored, long capacity) {
            return new TestEnergyStorage(stored, capacity, true, true, Long.MAX_VALUE, Long.MAX_VALUE);
        }

        private long stored() {
            return this.stored;
        }

        private long capacity() {
            return this.capacity;
        }

        private int simulatedInsertCalls() {
            return this.simulatedInsertCalls;
        }

        private int realInsertCalls() {
            return this.realInsertCalls;
        }

        private int realExtractCalls() {
            return this.realExtractCalls;
        }

        private int realExtractAttempts() {
            return this.realExtractAttempts;
        }

        private int realInsertAttempts() {
            return this.realInsertAttempts;
        }

        private int extractAttempts() {
            return this.extractAttempts;
        }

        private int insertAttempts() {
            return this.insertAttempts;
        }

        private long realExtracted() {
            return this.realExtracted;
        }

        private int rollbackCalls() {
            return this.rollbackCalls;
        }

        private long rolledBack() {
            return this.rolledBack;
        }

        private void failExtraction() {
            this.failExtraction = true;
        }

        private void failInsertion() {
            this.failInsertion = true;
        }

        private void returnInvalidInsertion() {
            this.invalidInsertion = true;
        }

        private void reportFullInsertionDuringSimulation() {
            this.fullInsertionDuringSimulation = true;
        }

        private void stopRealInsertionAfterFirstAttempt() {
            this.stopRealInsertionAfterFirstAttempt = true;
        }

        private void acceptAllRealInsertions() {
            this.stopRealInsertionAfterFirstAttempt = false;
            this.maxInsert = Long.MAX_VALUE;
        }

        private void throwOnRealInsertionAttempt(int attempt) {
            this.throwingRealInsertionAttempt = attempt;
        }

        private void throwOnRealExtractionAttempt(int attempt) {
            this.throwingRealExtractionAttempt = attempt;
        }

        private long insert(long amount, boolean simulate) {
            if (!this.receiveAllowed || amount <= 0) {
                return 0;
            }
            this.insertAttempts++;
            if (!simulate) {
                this.realInsertAttempts++;
                if (this.realInsertAttempts == this.throwingRealInsertionAttempt) {
                    throw new IllegalStateException("Deliberate segmented receiver failure");
                }
            }
            if (this.failInsertion) {
                throw new IllegalStateException("Deliberate receiver failure");
            }
            if (this.invalidInsertion) {
                return amount + 1;
            }
            long insertionLimit = simulate && this.fullInsertionDuringSimulation ? Long.MAX_VALUE : this.maxInsert;
            if (!simulate && this.stopRealInsertionAfterFirstAttempt && this.realInsertCalls > 0) {
                insertionLimit = 0;
            }
            long inserted = Math.min(amount, Math.min(insertionLimit, this.capacity - this.stored));
            if (simulate) {
                this.simulatedInsertCalls++;
            } else {
                this.realInsertCalls++;
                if (inserted > 0) {
                    this.stored += inserted;
                }
            }
            return inserted;
        }

        private long extract(long amount, boolean simulate) {
            this.extractAttempts++;
            if (this.failExtraction) {
                throw new IllegalStateException("Deliberate endpoint failure");
            }
            if (!this.extractAllowed || amount <= 0) {
                return 0;
            }
            if (!simulate) {
                this.realExtractAttempts++;
                if (this.realExtractAttempts == this.throwingRealExtractionAttempt) {
                    throw new IllegalStateException("Deliberate segmented source failure");
                }
            }
            long extracted = Math.min(amount, Math.min(this.maxExtract, this.stored));
            if (!simulate && extracted > 0) {
                this.realExtractCalls++;
                this.realExtracted += extracted;
                this.stored -= extracted;
            }
            return extracted;
        }

        private long rollbackExtraction(long amount) {
            if (amount < 0 || amount > this.capacity - this.stored) {
                throw new IllegalArgumentException("Invalid rollback amount: " + amount);
            }
            this.rollbackCalls++;
            this.rolledBack += amount;
            this.stored += amount;
            return amount;
        }

        private void recordNotification() {
            this.notifications++;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return (int) insert(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return (int) extract(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(this.stored, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(this.capacity, Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return this.extractAllowed;
        }

        @Override
        public boolean canReceive() {
            return this.receiveAllowed;
        }
    }
}
