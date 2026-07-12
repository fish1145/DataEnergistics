package com.fish_dan_.data_energistics.integration.energy;

import com.fish_dan_.data_energistics.mixin.core.NeoForgeEnergyStorageAccessor;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.neoforged.neoforge.energy.IEnergyStorage;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlimitedEnergyAccessImplTest {

    private final UnlimitedEnergyAccess access = new UnlimitedEnergyAccessImpl();

    @Test
    void bypassesPrivateIntFieldLimitsForInsertionAndExtraction() {
        PrivateIntStorage storage = new PrivateIntStorage(10, 200, true, true);

        assertEquals(10L, this.access.stored(storage));
        assertEquals(200L, this.access.capacity(storage));
        assertEquals(70L, this.access.insert(storage, 70L, true));
        assertEquals(10, storage.getEnergyStored());
        assertEquals(70L, this.access.insert(storage, 70L, false));
        assertEquals(80, storage.getEnergyStored());
        assertEquals(35L, this.access.extract(storage, 35L, true));
        assertEquals(80, storage.getEnergyStored());
        assertEquals(35L, this.access.extract(storage, 35L, false));
        assertEquals(45, storage.getEnergyStored());
    }

    @Test
    void preservesLongFieldAmountsBeyondTheCapabilityWidth() {
        PrivateLongStorage storage = new PrivateLongStorage(4_000_000_000L, 9_000_000_000L, true, true);

        assertEquals(4_000_000_000L, this.access.stored(storage));
        assertEquals(9_000_000_000L, this.access.capacity(storage));
        assertEquals(2_000_000_000L, this.access.extract(storage, 2_000_000_000L, false));
        assertEquals(2_000_000_000L, this.access.stored(storage));
        assertEquals(5_000_000_000L, this.access.insert(storage, 5_000_000_000L, false));
        assertEquals(7_000_000_000L, this.access.stored(storage));
    }

    @Test
    void usesTypedUnlimitedStorageBeforeAccessorAndReflectionFallbacks() {
        TypedUnlimitedStorage storage = new TypedUnlimitedStorage(4_000_000_000L, 9_000_000_000L);

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
    void unwrapsKnownWrapperFields() {
        PrivateIntStorage delegate = new PrivateIntStorage(20, 300, true, true);
        WrapperStorage wrapper = new WrapperStorage(delegate);

        assertEquals(150L, this.access.insert(wrapper, 150L, false));
        assertEquals(170, delegate.getEnergyStored());
        assertEquals(70L, this.access.extract(wrapper, 70L, false));
        assertEquals(100, delegate.getEnergyStored());
    }

    @Test
    void usesIgnoreLimitMethodsForRealAndSimulatedTransfers() {
        IgnoreLimitMethodStorage storage = new IgnoreLimitMethodStorage(40L, 500L);

        assertEquals(200L, this.access.insert(storage, 200L, true));
        assertEquals(40L, this.access.stored(storage));
        assertEquals(0, storage.insertCalls());
        assertEquals(200L, this.access.insert(storage, 200L, false));
        assertEquals(240L, this.access.stored(storage));
        assertEquals(1, storage.insertCalls());
        assertEquals(100L, this.access.extract(storage, 100L, true));
        assertEquals(240L, this.access.stored(storage));
        assertEquals(0, storage.extractCalls());
        assertEquals(100L, this.access.extract(storage, 100L, false));
        assertEquals(140L, this.access.stored(storage));
        assertEquals(1, storage.extractCalls());
    }

    @Test
    void supportsIntWidthAmountAndIgnoreLimitMethods() {
        IntMethodStorage storage = new IntMethodStorage(15, 300);

        assertEquals(100L, this.access.insert(storage, 100L, false));
        assertEquals(115L, this.access.stored(storage));
        assertEquals(60L, this.access.extract(storage, 60L, false));
        assertEquals(55L, this.access.stored(storage));
    }

    @Test
    void usesNeoForgeAccessorBeforeReflectivePlans() {
        AccessorStorage storage = new AccessorStorage(5, 250);

        assertEquals(120L, this.access.insert(storage, 120L, false));
        assertEquals(125, storage.getEnergyStored());
        assertEquals(80L, this.access.extract(storage, 80L, false));
        assertEquals(45, storage.getEnergyStored());
    }

    @Test
    void restoresUndeliveredExtractionOnASourceOnlyStorage() {
        PrivateIntStorage storage = new PrivateIntStorage(100, 200, false, true);

        assertEquals(70L, this.access.extract(storage, 70L, false));
        assertEquals(30, storage.getEnergyStored());
        assertEquals(70L, this.access.rollbackExtraction(storage, 70L));
        assertEquals(100, storage.getEnergyStored());
        assertFalse(storage.canReceive());
    }

    @Test
    void rollsBackInvalidOperationReturnsInBothDirections() {
        InvalidOperationStorage storage = new InvalidOperationStorage(200L, 1_000L);

        assertThrows(UnlimitedEnergyAccessException.class, () -> this.access.insert(storage, 100L, false));
        assertEquals(200L, this.access.stored(storage));
        assertThrows(UnlimitedEnergyAccessException.class, () -> this.access.extract(storage, 50L, false));
        assertEquals(200L, this.access.stored(storage));
    }

    @Test
    void doesNotInvokeThirdPartyOperationsDuringSimulation() {
        SimulationMutatingStorage storage = new SimulationMutatingStorage(30L, 300L);

        assertEquals(90L, this.access.insert(storage, 90L, true));
        assertEquals(30L, this.access.stored(storage));
        assertEquals(0, storage.insertCalls());
    }

    @Test
    void rollsBackASetterWhoseReadBackDoesNotMatch() {
        MismatchingSetterStorage storage = new MismatchingSetterStorage(25L, 250L);

        assertThrows(UnlimitedEnergyAccessException.class, () -> this.access.insert(storage, 50L, false));
        assertEquals(25L, this.access.stored(storage));
    }

    @Test
    void preservesRollbackFailureOnTheDirectOperationException() {
        RollbackFailingOperationStorage storage = new RollbackFailingOperationStorage(0L, 100L);

        UnlimitedEnergyAccessException exception = assertThrows(
                UnlimitedEnergyAccessException.class,
                () -> this.access.insert(storage, 40L, false));

        assertTrue(exception.isMutationAmountKnown());
        assertEquals(40L, exception.mutationAmount());
        assertEquals(1, exception.getSuppressed().length);
        assertEquals(40L, storage.actualStored());
        assertEquals(1, storage.rollbackAttempts());
        assertEquals(0, storage.capabilityReceiveCalls());
    }

    @Test
    void neverBypassesReceiveOrExtractPermissions() {
        PrivateIntStorage storage = new PrivateIntStorage(100, 200, false, false);

        assertFalse(this.access.canReceive(storage));
        assertFalse(this.access.canExtract(storage));
        assertEquals(0L, this.access.insert(storage, 50L, false));
        assertEquals(0L, this.access.extract(storage, 50L, false));
        assertEquals(100, storage.getEnergyStored());
    }

    @Test
    void invokesKnownChangeNotifications() {
        NotifyingStorage storage = new NotifyingStorage(10, 100);

        this.access.notifyStorageChanged(storage);

        assertEquals(1, storage.notifications());
    }

    @Test
    void rejectsNegativeRequestsImmediately() {
        PrivateIntStorage storage = new PrivateIntStorage(10, 100, true, true);

        assertThrows(IllegalArgumentException.class, () -> this.access.insert(storage, -1L, false));
        assertThrows(IllegalArgumentException.class, () -> this.access.extract(storage, -1L, false));
        assertThrows(IllegalArgumentException.class, () -> this.access.rollbackExtraction(storage, -1L));
    }

    @Test
    void isolatesCapabilityStateAndPermissionFailures() {
        ThrowingCapabilityStorage storage = new ThrowingCapabilityStorage();

        assertEquals(0L, this.access.stored(storage));
        assertEquals(0L, this.access.capacity(storage));
        assertFalse(this.access.canReceive(storage));
        assertFalse(this.access.canExtract(storage));
        assertEquals(0L, this.access.insert(storage, 10L, false));
        assertEquals(0L, this.access.extract(storage, 10L, false));
        assertEquals(UnlimitedEnergyAccess.UNAVAILABLE, this.access.rollbackExtraction(storage, 10L));
    }

    @Test
    void isolatesNonFatalErrorsAndSneakyCheckedCapabilityFailures() {
        NonFatalThrowingCapabilityStorage storage = new NonFatalThrowingCapabilityStorage();

        assertEquals(0L, this.access.stored(storage));
        assertEquals(0L, this.access.capacity(storage));
        assertFalse(this.access.canReceive(storage));
        assertFalse(this.access.canExtract(storage));
    }

    @Test
    void rollsBackTypedWritesThatThrowNonFatalFailuresAfterMutation() {
        assertTypedWriteFailureRollsBack(new AssertionError("Deliberate typed write assertion"));
        assertTypedWriteFailureRollsBack(new IOException("Deliberate typed write checked failure"));
    }

    @Test
    void rethrowsFatalMethodHandleReaderFailuresWithoutChangingIdentity() {
        VirtualMachineError virtualMachineError = new VirtualMachineError("Deliberate fatal VM failure") {};
        FatalReaderStorage virtualMachineStorage = new FatalReaderStorage(virtualMachineError);

        VirtualMachineError propagatedVirtualMachineError = assertThrows(
                VirtualMachineError.class, () -> this.access.stored(virtualMachineStorage));
        assertSame(virtualMachineError, propagatedVirtualMachineError);

        ThreadDeath threadDeath = new ThreadDeath();
        FatalReaderStorage threadDeathStorage = new FatalReaderStorage(threadDeath);

        ThreadDeath propagatedThreadDeath = assertThrows(
                ThreadDeath.class, () -> this.access.stored(threadDeathStorage));
        assertSame(threadDeath, propagatedThreadDeath);
    }

    @Test
    void restoresInterruptStatusForRecoverableInterruptedFailures() {
        Thread.interrupted();
        InterruptedException interruptedException = new InterruptedException("Deliberate integration interruption");
        try {
            assertSame(interruptedException, ThrowableIsolation.rethrowIfFatal(interruptedException));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void assertTypedWriteFailureRollsBack(Throwable failure) {
        TypedUnlimitedStorage storage = new TypedUnlimitedStorage(20L, 200L, failure);

        UnlimitedEnergyAccessException exception = assertThrows(
                UnlimitedEnergyAccessException.class, () -> this.access.insert(storage, 80L, false));

        assertEquals(20L, storage.actualStored());
        assertEquals(2, storage.writeAttempts());
        assertTrue(exception.isMutationAmountKnown());
        assertEquals(0L, exception.mutationAmount());
        assertSame(failure, exception.getCause().getCause());
    }

    private abstract static class TestStorage implements IEnergyStorage {

        private final boolean receiveAllowed;
        private final boolean extractAllowed;

        private TestStorage(boolean receiveAllowed, boolean extractAllowed) {
            this.receiveAllowed = receiveAllowed;
            this.extractAllowed = extractAllowed;
        }

        protected abstract long actualStored();

        protected abstract long actualCapacity();

        protected abstract void setActualStored(long amount);

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
            return clampToInt(actualStored());
        }

        @Override
        public int getMaxEnergyStored() {
            return clampToInt(actualCapacity());
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

    private static final class TypedUnlimitedStorage extends TestStorage implements UnlimitedEnergyStorage {

        private long value;
        private final long limit;
        private int notifications;
        private int writeAttempts;
        private Throwable writeFailure;

        private TypedUnlimitedStorage(long value, long limit) {
            this(value, limit, null);
        }

        private TypedUnlimitedStorage(long value, long limit, Throwable writeFailure) {
            super(true, true);
            this.value = value;
            this.limit = limit;
            this.writeFailure = writeFailure;
        }

        @Override
        protected long actualStored() {
            return this.value;
        }

        @Override
        protected long actualCapacity() {
            return this.limit;
        }

        @Override
        protected void setActualStored(long amount) {
            this.value = amount;
        }

        @Override
        public long getStoredEnergyLong() {
            return this.value;
        }

        @Override
        public long getEnergyCapacityLong() {
            return this.limit;
        }

        @Override
        public void setStoredEnergyLong(long amount) {
            this.value = amount;
            this.writeAttempts++;
            Throwable failure = this.writeFailure;
            this.writeFailure = null;
            if (failure != null) {
                throwUnchecked(failure);
            }
        }

        @Override
        public void onUnlimitedEnergyChanged() {
            this.notifications++;
        }

        private int notifications() {
            return this.notifications;
        }

        private int writeAttempts() {
            return this.writeAttempts;
        }
    }

    private static final class ThrowingCapabilityStorage implements IEnergyStorage {

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
            throw new IllegalStateException("Deliberate stored-energy failure");
        }

        @Override
        public int getMaxEnergyStored() {
            throw new IllegalStateException("Deliberate capacity failure");
        }

        @Override
        public boolean canExtract() {
            throw new IllegalStateException("Deliberate extract-permission failure");
        }

        @Override
        public boolean canReceive() {
            throw new IllegalStateException("Deliberate receive-permission failure");
        }
    }

    private static final class NonFatalThrowingCapabilityStorage implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            throw new AssertionError("Deliberate stored-energy assertion");
        }

        @Override
        public int getMaxEnergyStored() {
            throwUnchecked(new IOException("Deliberate capacity checked failure"));
            return 0;
        }

        @Override
        public boolean canExtract() {
            throw new AssertionError("Deliberate extract-permission assertion");
        }

        @Override
        public boolean canReceive() {
            throwUnchecked(new IOException("Deliberate receive-permission checked failure"));
            return false;
        }
    }

    private static final class FatalReaderStorage extends TestStorage {

        private long amount = 10L;
        private final long capacity = 100L;
        private final Throwable readFailure;

        private FatalReaderStorage(Throwable readFailure) {
            super(true, true);
            this.readFailure = readFailure;
        }

        private long getAmount() {
            throwUnchecked(this.readFailure);
            return this.amount;
        }

        private long getCapacity() {
            return this.capacity;
        }

        private void setAmount(long amount) {
            this.amount = amount;
        }

        @Override
        protected long actualStored() {
            return this.amount;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.amount = amount;
        }
    }

    private static final class PrivateIntStorage extends TestStorage {

        private int energy;
        private final int capacity;

        private PrivateIntStorage(int energy, int capacity, boolean receiveAllowed, boolean extractAllowed) {
            super(receiveAllowed, extractAllowed);
            this.energy = energy;
            this.capacity = capacity;
        }

        @Override
        protected long actualStored() {
            return this.energy;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.energy = (int) amount;
        }
    }

    private static final class PrivateLongStorage extends TestStorage {

        private long amount;
        private final long maxStorage;

        private PrivateLongStorage(long amount, long maxStorage, boolean receiveAllowed, boolean extractAllowed) {
            super(receiveAllowed, extractAllowed);
            this.amount = amount;
            this.maxStorage = maxStorage;
        }

        @Override
        protected long actualStored() {
            return this.amount;
        }

        @Override
        protected long actualCapacity() {
            return this.maxStorage;
        }

        @Override
        protected void setActualStored(long amount) {
            this.amount = amount;
        }
    }

    private static final class WrapperStorage extends TestStorage {

        private final TestStorage delegate;

        private WrapperStorage(TestStorage delegate) {
            super(delegate.canReceive(), delegate.canExtract());
            this.delegate = delegate;
        }

        @Override
        protected long actualStored() {
            return this.delegate.actualStored();
        }

        @Override
        protected long actualCapacity() {
            return this.delegate.actualCapacity();
        }

        @Override
        protected void setActualStored(long amount) {
            this.delegate.setActualStored(amount);
        }
    }

    private static class MethodStorage extends TestStorage {

        private long amount;
        private final long capacity;

        private MethodStorage(long amount, long capacity) {
            super(true, true);
            this.amount = amount;
            this.capacity = capacity;
        }

        private long getAmount() {
            return this.amount;
        }

        private long getCapacity() {
            return this.capacity;
        }

        private void setAmount(long amount) {
            this.amount = amount;
        }

        @Override
        protected long actualStored() {
            return this.amount;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.amount = amount;
        }
    }

    private static final class IgnoreLimitMethodStorage extends MethodStorage {

        private int insertCalls;
        private int extractCalls;

        private IgnoreLimitMethodStorage(long amount, long capacity) {
            super(amount, capacity);
        }

        private long insertIgnoringLimit(long amount, boolean simulate) {
            this.insertCalls++;
            long inserted = Math.min(amount, actualCapacity() - actualStored());
            if (!simulate) {
                setActualStored(actualStored() + inserted);
            }
            return inserted;
        }

        private long extractIgnoringLimit(long amount, boolean simulate) {
            this.extractCalls++;
            long extracted = Math.min(amount, actualStored());
            if (!simulate) {
                setActualStored(actualStored() - extracted);
            }
            return extracted;
        }

        private int insertCalls() {
            return this.insertCalls;
        }

        private int extractCalls() {
            return this.extractCalls;
        }
    }

    private static final class IntMethodStorage extends TestStorage {

        private int amount;
        private final int capacity;

        private IntMethodStorage(int amount, int capacity) {
            super(true, true);
            this.amount = amount;
            this.capacity = capacity;
        }

        private int getAmount() {
            return this.amount;
        }

        private int getCapacity() {
            return this.capacity;
        }

        private void setAmount(int amount) {
            this.amount = amount;
        }

        private int insertIgnoringLimit(int amount, boolean simulate) {
            int inserted = Math.min(amount, this.capacity - this.amount);
            if (!simulate) {
                this.amount += inserted;
            }
            return inserted;
        }

        private int extractIgnoringLimit(int amount, boolean simulate) {
            int extracted = Math.min(amount, this.amount);
            if (!simulate) {
                this.amount -= extracted;
            }
            return extracted;
        }

        @Override
        protected long actualStored() {
            return this.amount;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.amount = (int) amount;
        }
    }

    private static final class InvalidOperationStorage extends MethodStorage {

        private InvalidOperationStorage(long amount, long capacity) {
            super(amount, capacity);
        }

        private long insertIgnoringLimit(long amount, boolean simulate) {
            setActualStored(actualStored() + amount);
            return amount + 1L;
        }

        private long extractIgnoringLimit(long amount, boolean simulate) {
            setActualStored(actualStored() - amount);
            return -1L;
        }
    }

    private static final class SimulationMutatingStorage extends MethodStorage {

        private int insertCalls;

        private SimulationMutatingStorage(long amount, long capacity) {
            super(amount, capacity);
        }

        private long insertIgnoringLimit(long amount, boolean simulate) {
            this.insertCalls++;
            setActualStored(actualStored() + amount);
            return amount;
        }

        private int insertCalls() {
            return this.insertCalls;
        }
    }

    private static final class RollbackFailingOperationStorage extends TestStorage {

        private long amount;
        private final long capacity;
        private int rollbackAttempts;
        private int capabilityReceiveCalls;

        private RollbackFailingOperationStorage(long amount, long capacity) {
            super(true, false);
            this.amount = amount;
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
            if (simulate) {
                return amount;
            }
            this.amount += amount;
            return amount + 1L;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            this.capabilityReceiveCalls++;
            return super.receiveEnergy(maxReceive, simulate);
        }

        @Override
        protected long actualStored() {
            return this.amount;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.amount = amount;
        }

        private int rollbackAttempts() {
            return this.rollbackAttempts;
        }

        private int capabilityReceiveCalls() {
            return this.capabilityReceiveCalls;
        }
    }

    private static final class MismatchingSetterStorage extends TestStorage {

        private final long originalAmount;
        private long amount;
        private final long capacity;

        private MismatchingSetterStorage(long amount, long capacity) {
            super(true, true);
            this.originalAmount = amount;
            this.amount = amount;
            this.capacity = capacity;
        }

        private long getAmount() {
            return this.amount;
        }

        private long getCapacity() {
            return this.capacity;
        }

        private void setAmount(long amount) {
            this.amount = amount == this.originalAmount ? amount : amount + 1L;
        }

        @Override
        protected long actualStored() {
            return this.amount;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.amount = amount;
        }
    }

    private static final class AccessorStorage extends TestStorage implements NeoForgeEnergyStorageAccessor {

        private int energy;
        private final int capacity;

        private AccessorStorage(int energy, int capacity) {
            super(true, true);
            this.energy = energy;
            this.capacity = capacity;
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

        @Override
        protected long actualStored() {
            return this.energy;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.energy = (int) amount;
        }
    }

    private static final class NotifyingStorage extends TestStorage {

        private int energy;
        private final int capacity;
        private int notifications;

        private NotifyingStorage(int energy, int capacity) {
            super(true, true);
            this.energy = energy;
            this.capacity = capacity;
        }

        private void onContentsChanged() {
            this.notifications++;
        }

        private int notifications() {
            return this.notifications;
        }

        @Override
        protected long actualStored() {
            return this.energy;
        }

        @Override
        protected long actualCapacity() {
            return this.capacity;
        }

        @Override
        protected void setActualStored(long amount) {
            this.energy = (int) amount;
        }
    }

    private static int clampToInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
