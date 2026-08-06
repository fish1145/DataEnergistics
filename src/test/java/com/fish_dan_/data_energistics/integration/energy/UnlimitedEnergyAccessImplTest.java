package com.fish_dan_.data_energistics.integration.energy;

import com.fish_dan_.data_energistics.mixin.core.NeoForgeEnergyStorageAccessor;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlimitedEnergyAccessImplTest {

    private final UnlimitedEnergyAccess access = new UnlimitedEnergyAccessImpl();

    @Test
    void leavesUnknownThirdPartyStorageOnItsPublicCapability() {
        UnknownStorage storage = new UnknownStorage(40, 500, true, true);

        assertEquals(40L, this.access.stored(storage));
        assertEquals(500L, this.access.capacity(storage));
        assertTrue(this.access.canReceive(storage));
        assertTrue(this.access.canExtract(storage));
        assertEquals(UnlimitedEnergyAccess.UNAVAILABLE, this.access.insert(storage, 200L, true));
        assertEquals(UnlimitedEnergyAccess.UNAVAILABLE, this.access.insert(storage, 200L, false));
        assertEquals(UnlimitedEnergyAccess.UNAVAILABLE, this.access.extract(storage, 30L, false));
        assertEquals(UnlimitedEnergyAccess.UNAVAILABLE, this.access.rollbackExtraction(storage, 30L));

        assertEquals(40, storage.getEnergyStored());
    }

    @Test
    void transfersTypedLongAmountsAndPublishesChanges() {
        TypedStorage storage = new TypedStorage(4_000_000_000L, 9_000_000_000L, true, true);

        assertEquals(4_000_000_000L, this.access.stored(storage));
        assertEquals(9_000_000_000L, this.access.capacity(storage));
        assertEquals(3_000_000_000L, this.access.insert(storage, 3_000_000_000L, true));
        assertEquals(4_000_000_000L, storage.actualStored());
        assertEquals(3_000_000_000L, this.access.insert(storage, 3_000_000_000L, false));
        assertEquals(2_000_000_000L, this.access.extract(storage, 2_000_000_000L, false));

        this.access.notifyStorageChanged(storage);

        assertEquals(5_000_000_000L, storage.actualStored());
        assertEquals(1, storage.notifications());
    }

    @Test
    void snapshotsTypedEnergyStateWithOneVerifiedReadPass() {
        TypedStorage storage = new TypedStorage(4_000_000_000L, 9_000_000_000L, true, true);

        UnlimitedEnergyAccess.EnergySnapshot snapshot = this.access.snapshot(storage);

        assertEquals(4_000_000_000L, snapshot.stored());
        assertEquals(9_000_000_000L, snapshot.capacity());
        assertEquals(1, storage.directStoredReads());
        assertEquals(1, storage.directCapacityReads());
        assertEquals(1, storage.capabilityStoredReads());
        assertEquals(1, storage.capabilityCapacityReads());
    }

    @Test
    void routesNeoForgeEnergyStorageSubclassesThroughTheirPublicCapability() {
        TrackingEnergyStorage storage = new TrackingEnergyStorage();

        assertEquals(1L, insertWithFallback(storage, 250));
        assertEquals(101, storage.getEnergyStored());
        assertEquals(1, storage.receiveCalls());
        assertEquals(1L, extractWithFallback(storage, 80));
        assertEquals(100, storage.getEnergyStored());
        assertEquals(1, storage.extractCalls());
    }

    @Test
    void preservesCapabilityDirectionPermissionsForDirectStorage() {
        TypedStorage source = new TypedStorage(100L, 200L, false, true);
        TypedStorage sink = new TypedStorage(20L, 200L, true, false);

        assertEquals(0L, this.access.insert(source, 50L, false));
        assertEquals(50L, this.access.extract(source, 50L, false));
        assertEquals(50L, this.access.insert(sink, 50L, false));
        assertEquals(0L, this.access.extract(sink, 50L, false));
        assertEquals(50L, this.access.rollbackExtraction(source, 50L));
        assertEquals(100L, source.actualStored());
        assertEquals(70L, sink.actualStored());
    }

    @Test
    void rollsBackTypedWritesThatFailAfterMutation() {
        TypedStorage storage = new TypedStorage(20L, 200L, true, true);
        storage.failNextWrite();

        UnlimitedEnergyAccessException exception = assertThrows(
                UnlimitedEnergyAccessException.class, () -> this.access.insert(storage, 80L, false));

        assertEquals(20L, storage.actualStored());
        assertEquals(2, storage.writeAttempts());
        assertTrue(exception.isMutationAmountKnown());
        assertEquals(0L, exception.mutationAmount());
    }

    @Test
    void rejectsNegativeRequestsBeforeAccessingStorage() {
        ThrowingStorage storage = new ThrowingStorage();

        assertThrows(IllegalArgumentException.class, () -> this.access.insert(storage, -1L, false));
        assertThrows(IllegalArgumentException.class, () -> this.access.extract(storage, -1L, false));
        assertThrows(IllegalArgumentException.class, () -> this.access.rollbackExtraction(storage, -1L));
    }

    @Test
    void isolatesRecoverableCapabilityFailures() {
        ThrowingStorage storage = new ThrowingStorage();

        assertEquals(0L, this.access.stored(storage));
        assertEquals(0L, this.access.capacity(storage));
        assertFalse(this.access.canReceive(storage));
        assertFalse(this.access.canExtract(storage));
        assertEquals(0L, this.access.insert(storage, 10L, false));
        assertEquals(0L, this.access.extract(storage, 10L, false));
        assertEquals(UnlimitedEnergyAccess.UNAVAILABLE, this.access.rollbackExtraction(storage, 10L));
    }

    private static class StandardStorage implements IEnergyStorage {

        private long stored;
        private final long capacity;
        private final boolean receiveAllowed;
        private final boolean extractAllowed;
        private int capabilityStoredReads;
        private int capabilityCapacityReads;

        private StandardStorage(long stored, long capacity, boolean receiveAllowed, boolean extractAllowed) {
            this.stored = stored;
            this.capacity = capacity;
            this.receiveAllowed = receiveAllowed;
            this.extractAllowed = extractAllowed;
        }

        protected long actualStored() {
            return this.stored;
        }

        protected long actualCapacity() {
            return this.capacity;
        }

        protected void setActualStored(long stored) {
            this.stored = stored;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!canReceive() || maxReceive <= 0) {
                return 0;
            }
            int received = (int) Math.min(1L, Math.min(maxReceive, actualCapacity() - actualStored()));
            if (!simulate) {
                setActualStored(actualStored() + received);
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!canExtract() || maxExtract <= 0) {
                return 0;
            }
            int extracted = (int) Math.min(1L, Math.min(maxExtract, actualStored()));
            if (!simulate) {
                setActualStored(actualStored() - extracted);
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            this.capabilityStoredReads++;
            return clampToInt(actualStored());
        }

        @Override
        public int getMaxEnergyStored() {
            this.capabilityCapacityReads++;
            return clampToInt(actualCapacity());
        }

        protected int capabilityStoredReads() {
            return this.capabilityStoredReads;
        }

        protected int capabilityCapacityReads() {
            return this.capabilityCapacityReads;
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

    private static final class UnknownStorage extends StandardStorage {

        private UnknownStorage(long stored, long capacity, boolean receiveAllowed, boolean extractAllowed) {
            super(stored, capacity, receiveAllowed, extractAllowed);
        }
    }

    private static final class TypedStorage extends StandardStorage implements UnlimitedEnergyStorage {

        private int notifications;
        private int writeAttempts;
        private boolean failNextWrite;
        private int directStoredReads;
        private int directCapacityReads;

        private TypedStorage(long stored, long capacity, boolean receiveAllowed, boolean extractAllowed) {
            super(stored, capacity, receiveAllowed, extractAllowed);
        }

        @Override
        public long getStoredEnergyLong() {
            this.directStoredReads++;
            return actualStored();
        }

        @Override
        public long getEnergyCapacityLong() {
            this.directCapacityReads++;
            return actualCapacity();
        }

        private int directStoredReads() {
            return this.directStoredReads;
        }

        private int directCapacityReads() {
            return this.directCapacityReads;
        }

        @Override
        public void setStoredEnergyLong(long amount) {
            this.writeAttempts++;
            setActualStored(amount);
            if (this.failNextWrite) {
                this.failNextWrite = false;
                throw new IllegalStateException("Deliberate typed write failure");
            }
        }

        @Override
        public void onUnlimitedEnergyChanged() {
            this.notifications++;
        }

        private void failNextWrite() {
            this.failNextWrite = true;
        }

        private int notifications() {
            return this.notifications;
        }

        private int writeAttempts() {
            return this.writeAttempts;
        }
    }

    private long insertWithFallback(IEnergyStorage storage, int amount) {
        long inserted = this.access.insert(storage, amount, false);
        return inserted == UnlimitedEnergyAccess.UNAVAILABLE ? storage.receiveEnergy(amount, false) : inserted;
    }

    private long extractWithFallback(IEnergyStorage storage, int amount) {
        long extracted = this.access.extract(storage, amount, false);
        return extracted == UnlimitedEnergyAccess.UNAVAILABLE ? storage.extractEnergy(amount, false) : extracted;
    }

    private static final class TrackingEnergyStorage extends EnergyStorage implements NeoForgeEnergyStorageAccessor {

        private int receiveCalls;
        private int extractCalls;

        private TrackingEnergyStorage() {
            super(500, 1, 1, 100);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            this.receiveCalls++;
            return super.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            this.extractCalls++;
            return super.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int dataEnergistics$getEnergy() {
            return this.energy;
        }

        @Override
        public void dataEnergistics$setEnergy(int energy) {
            this.energy = energy;
        }

        @Override
        public int dataEnergistics$getCapacity() {
            return this.capacity;
        }

        private int receiveCalls() {
            return this.receiveCalls;
        }

        private int extractCalls() {
            return this.extractCalls;
        }
    }

    private static final class ThrowingStorage implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            throw new IllegalStateException("Deliberate receive failure");
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            throw new IllegalStateException("Deliberate extract failure");
        }

        @Override
        public int getEnergyStored() {
            throw new IllegalStateException("Deliberate stored energy failure");
        }

        @Override
        public int getMaxEnergyStored() {
            throw new IllegalStateException("Deliberate capacity failure");
        }

        @Override
        public boolean canExtract() {
            throw new IllegalStateException("Deliberate extract permission failure");
        }

        @Override
        public boolean canReceive() {
            throw new IllegalStateException("Deliberate receive permission failure");
        }
    }

    private static int clampToInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
