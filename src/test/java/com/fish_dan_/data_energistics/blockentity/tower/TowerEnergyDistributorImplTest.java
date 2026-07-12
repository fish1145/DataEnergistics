package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;

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
        return new TowerEnergyDistributorImpl(
                new TestContext(),
                new TestEndpointResolver(extractEndpoints, receiveEndpoints),
                unlimitedEnergyAccess,
                false);
    }

    private static TowerEnergyEndpoint endpoint(BlockPos pos, TestEnergyStorage storage) {
        return new TowerEnergyEndpoint(pos, Direction.NORTH, storage);
    }

    private static final class TestContext implements TowerEnergyDistributorContext {

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
        public void notifyStorageChanged(IEnergyStorage storage) {
            throw new IllegalStateException("Fallback capability mutations must notify themselves");
        }
    }

    private static final class TestEnergyStorage implements IEnergyStorage {

        private long stored;
        private final long capacity;
        private final boolean receiveAllowed;
        private final boolean extractAllowed;
        private final long maxInsert;
        private final long maxExtract;
        private boolean failInsertion;
        private boolean invalidInsertion;
        private boolean failExtraction;
        private int insertAttempts;
        private int simulatedInsertCalls;
        private int realInsertCalls;
        private int realExtractCalls;
        private int extractAttempts;
        private long realExtracted;
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

        private int extractAttempts() {
            return this.extractAttempts;
        }

        private int insertAttempts() {
            return this.insertAttempts;
        }

        private long realExtracted() {
            return this.realExtracted;
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

        private long insert(long amount, boolean simulate) {
            if (!this.receiveAllowed || amount <= 0) {
                return 0;
            }
            this.insertAttempts++;
            if (this.failInsertion) {
                throw new IllegalStateException("Deliberate receiver failure");
            }
            if (this.invalidInsertion) {
                return amount + 1;
            }
            long inserted = Math.min(amount, Math.min(this.maxInsert, this.capacity - this.stored));
            if (simulate) {
                this.simulatedInsertCalls++;
            } else if (inserted > 0) {
                this.realInsertCalls++;
                this.stored += inserted;
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
            long extracted = Math.min(amount, Math.min(this.maxExtract, this.stored));
            if (!simulate && extracted > 0) {
                this.realExtractCalls++;
                this.realExtracted += extracted;
                this.stored -= extracted;
            }
            return extracted;
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
