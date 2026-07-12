package com.fish_dan_.data_energistics.integration.oritech;

import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessException;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessImpl;

import net.neoforged.neoforge.energy.IEnergyStorage;

import org.junit.jupiter.api.Test;
import rearth.oritech.api.energy.EnergyApi.EnergyStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OritechEnergyIntegrationTest {

    private final UnlimitedEnergyAccess access = new UnlimitedEnergyAccessImpl();

    @Test
    void bypassesOritechTransferMethodsThroughItsTypedLongApi() {
        TestOritechStorage oritechStorage = new TestOritechStorage(4_000_000_000L, 9_000_000_000L);
        IEnergyStorage storage = OritechEnergyIntegration.wrapEnergyStorage(oritechStorage);

        assertEquals(4_000_000_000L, this.access.stored(storage));
        assertEquals(9_000_000_000L, this.access.capacity(storage));
        assertEquals(3_000_000_000L, this.access.insert(storage, 3_000_000_000L, false));
        assertEquals(2_000_000_000L, this.access.extract(storage, 2_000_000_000L, false));
        this.access.notifyStorageChanged(storage);

        assertEquals(5_000_000_000L, oritechStorage.getAmount());
        assertEquals(0, oritechStorage.rateLimitedCalls());
        assertEquals(1, oritechStorage.updates());
    }

    @Test
    void isolatesRecoverableStandardReadsAndPermissionChecks() {
        TestOritechStorage oritechStorage = new TestOritechStorage(40L, 100L);
        IEnergyStorage storage = OritechEnergyIntegration.wrapEnergyStorage(oritechStorage);

        oritechStorage.failAmountWith(new AssertionError("recoverable amount failure"));
        assertEquals(0, storage.getEnergyStored());
        oritechStorage.failAmountWith(null);

        Exception checkedFailure = new Exception("sneaky capacity failure");
        oritechStorage.failCapacityWith(checkedFailure);
        assertEquals(0, storage.getMaxEnergyStored());
        oritechStorage.failCapacityWith(null);

        oritechStorage.failInsertionPermissionWith(new AssertionError("recoverable insertion permission failure"));
        assertFalse(storage.canReceive());
        oritechStorage.failExtractionPermissionWith(checkedFailure);
        assertFalse(storage.canExtract());
    }

    @Test
    void rollsBackAWriteThatMutatesBeforeARecoverableFailure() {
        TestOritechStorage oritechStorage = new TestOritechStorage(40L, 100L);
        IEnergyStorage storage = OritechEnergyIntegration.wrapEnergyStorage(oritechStorage);
        oritechStorage.failNextSetAfterMutation(new AssertionError("recoverable write failure"));

        assertThrows(UnlimitedEnergyAccessException.class, () -> this.access.insert(storage, 30L, false));

        assertEquals(40L, oritechStorage.getAmount());
        assertEquals(0, oritechStorage.rateLimitedCalls());
    }

    @Test
    void isolatesRecoverableUpdateFailuresAsTypedNotificationFailures() {
        TestOritechStorage oritechStorage = new TestOritechStorage(40L, 100L);
        IEnergyStorage storage = OritechEnergyIntegration.wrapEnergyStorage(oritechStorage);
        oritechStorage.failUpdateWith(new AssertionError("recoverable update failure"));

        assertThrows(UnlimitedEnergyAccessException.class, () -> this.access.notifyStorageChanged(storage));
    }

    @Test
    void rethrowsFatalFailuresWithoutChangingTheirIdentity() {
        TestOritechStorage oritechStorage = new TestOritechStorage(40L, 100L);
        IEnergyStorage storage = OritechEnergyIntegration.wrapEnergyStorage(oritechStorage);

        TestVirtualMachineError virtualMachineError = new TestVirtualMachineError();
        oritechStorage.failAmountWith(virtualMachineError);
        TestVirtualMachineError thrownVirtualMachineError = assertThrows(
                TestVirtualMachineError.class, storage::getEnergyStored);
        assertSame(virtualMachineError, thrownVirtualMachineError);

        oritechStorage.failAmountWith(null);
        ThreadDeath threadDeath = new ThreadDeath();
        oritechStorage.failInsertionPermissionWith(threadDeath);
        ThreadDeath thrownThreadDeath = assertThrows(ThreadDeath.class, storage::canReceive);
        assertSame(threadDeath, thrownThreadDeath);
    }

    private static final class TestOritechStorage extends EnergyStorage {

        private long amount;
        private final long capacity;
        private int rateLimitedCalls;
        private int updates;
        private Throwable amountFailure;
        private Throwable capacityFailure;
        private Throwable insertionPermissionFailure;
        private Throwable extractionPermissionFailure;
        private Throwable updateFailure;
        private Throwable nextSetFailure;

        private TestOritechStorage(long amount, long capacity) {
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            this.rateLimitedCalls++;
            long inserted = Math.min(1L, Math.min(maxAmount, this.capacity - this.amount));
            if (!simulate) {
                this.amount += inserted;
            }
            return inserted;
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            this.rateLimitedCalls++;
            long extracted = Math.min(1L, Math.min(maxAmount, this.amount));
            if (!simulate) {
                this.amount -= extracted;
            }
            return extracted;
        }

        @Override
        public void setAmount(long amount) {
            this.amount = amount;
            if (this.nextSetFailure != null) {
                Throwable failure = this.nextSetFailure;
                this.nextSetFailure = null;
                throwUnchecked(failure);
            }
        }

        @Override
        public long getAmount() {
            throwIfPresent(this.amountFailure);
            return this.amount;
        }

        @Override
        public long getCapacity() {
            throwIfPresent(this.capacityFailure);
            return this.capacity;
        }

        @Override
        public boolean supportsInsertion() {
            throwIfPresent(this.insertionPermissionFailure);
            return true;
        }

        @Override
        public boolean supportsExtraction() {
            throwIfPresent(this.extractionPermissionFailure);
            return true;
        }

        @Override
        public void update() {
            this.updates++;
            throwIfPresent(this.updateFailure);
        }

        private void failAmountWith(Throwable failure) {
            this.amountFailure = failure;
        }

        private void failCapacityWith(Throwable failure) {
            this.capacityFailure = failure;
        }

        private void failInsertionPermissionWith(Throwable failure) {
            this.insertionPermissionFailure = failure;
        }

        private void failExtractionPermissionWith(Throwable failure) {
            this.extractionPermissionFailure = failure;
        }

        private void failUpdateWith(Throwable failure) {
            this.updateFailure = failure;
        }

        private void failNextSetAfterMutation(Throwable failure) {
            this.nextSetFailure = failure;
        }

        private int rateLimitedCalls() {
            return this.rateLimitedCalls;
        }

        private int updates() {
            return this.updates;
        }

        private static void throwIfPresent(Throwable throwable) {
            if (throwable != null) {
                throwUnchecked(throwable);
            }
        }

        private static void throwUnchecked(Throwable throwable) {
            TestOritechStorage.<RuntimeException>throwAny(throwable);
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> void throwAny(Throwable throwable) throws T {
            throw (T) throwable;
        }
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {}
}
