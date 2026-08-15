package com.fish_dan_.data_energistics.integration.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.mixin.core.accessor.NeoForgeEnergyStorageAccessor;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Provides verified {@link UnlimitedEnergyAccess} for explicit, type-safe energy storage contracts.
 *
 * <p>
 * Direct mutations are available only through {@link UnlimitedEnergyStorage} or the reference NeoForge storage
 * accessor. Unknown third-party implementations remain on their public {@link IEnergyStorage} API instead of being
 * inspected or mutated through implementation details.
 */
public final class VerifiedUnlimitedEnergyAccess implements UnlimitedEnergyAccess {

    private static final AmountWriter TYPED_AMOUNT_WRITER = new AmountWriter() {

        @Override
        public void write(Object target, long amount) {
            if (!supports(amount)) {
                throw new UnlimitedEnergyAccessException(
                        "Typed unlimited energy writer cannot represent " + amount + " FE");
            }
            try {
                ((UnlimitedEnergyStorage) target).setStoredEnergyLong(amount);
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
                throw directFailure("Could not write typed unlimited energy storage", target, exception);
            }
        }

        @Override
        public long maxValue() {
            return Long.MAX_VALUE;
        }
    };
    private static final StateAccess TYPED_STATE_ACCESS = new StateAccess(
            VerifiedUnlimitedEnergyAccess::readTypedStored,
            VerifiedUnlimitedEnergyAccess::readTypedCapacity,
            TYPED_AMOUNT_WRITER,
            Long.MAX_VALUE);
    private static final AmountWriter NEOFORGE_AMOUNT_WRITER = new AmountWriter() {

        @Override
        public void write(Object target, long amount) {
            if (!supports(amount)) {
                throw new UnlimitedEnergyAccessException(
                        "NeoForge energy writer cannot represent " + amount + " FE");
            }
            try {
                ((NeoForgeEnergyStorageAccessor) target).dataEnergistics$setEnergy((int) amount);
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
                throw directFailure("Could not write NeoForge unlimited energy storage", target, exception);
            }
        }

        @Override
        public long maxValue() {
            return Integer.MAX_VALUE;
        }
    };
    private static final StateAccess NEOFORGE_STATE_ACCESS = new StateAccess(
            VerifiedUnlimitedEnergyAccess::readNeoForgeStored,
            VerifiedUnlimitedEnergyAccess::readNeoForgeCapacity,
            NEOFORGE_AMOUNT_WRITER,
            Integer.MAX_VALUE);

    @Override
    public long stored(IEnergyStorage storage) {
        Optional<DirectTarget> target = findDirectTarget(storage);
        if (target.isPresent()) {
            Snapshot snapshot = readVerifiedSnapshot(storage, target.get());
            if (snapshot != null) {
                return snapshot.stored();
            }
        }
        Integer stored = readCapabilityValue(storage, true);
        return stored == null ? 0L : stored;
    }

    @Override
    public EnergySnapshot snapshot(IEnergyStorage storage) {
        Optional<DirectTarget> target = findDirectTarget(storage);
        if (target.isPresent()) {
            Snapshot snapshot = readVerifiedSnapshot(storage, target.get());
            if (snapshot != null) {
                return new EnergySnapshot(snapshot.stored(), snapshot.capacity());
            }
        }
        Integer stored = readCapabilityValue(storage, true);
        Integer capacity = readCapabilityValue(storage, false);
        return new EnergySnapshot(stored == null ? 0L : stored, capacity == null ? 0L : capacity);
    }

    @Override
    public long capacity(IEnergyStorage storage) {
        Optional<DirectTarget> target = findDirectTarget(storage);
        if (target.isPresent()) {
            Snapshot snapshot = readVerifiedSnapshot(storage, target.get());
            if (snapshot != null) {
                return snapshot.capacity();
            }
        }
        Integer capacity = readCapabilityValue(storage, false);
        return capacity == null ? 0L : capacity;
    }

    @Override
    public boolean canReceive(IEnergyStorage storage) {
        return readCapabilityPermission(storage, true);
    }

    @Override
    public boolean canExtract(IEnergyStorage storage) {
        return readCapabilityPermission(storage, false);
    }

    @Override
    public long insert(IEnergyStorage storage, long amount, boolean simulate) {
        validateRequestedAmount(amount);
        if (amount == 0L || !canReceive(storage)) {
            return 0L;
        }
        return transfer(storage, amount, simulate, true);
    }

    @Override
    public long extract(IEnergyStorage storage, long amount, boolean simulate) {
        validateRequestedAmount(amount);
        if (amount == 0L || !canExtract(storage)) {
            return 0L;
        }
        return transfer(storage, amount, simulate, false);
    }

    @Override
    public long rollbackExtraction(IEnergyStorage storage, long amount) {
        validateRequestedAmount(amount);
        if (amount == 0L) {
            return 0L;
        }

        Optional<DirectTarget> resolvedTarget = findDirectTarget(storage);
        if (resolvedTarget.isEmpty()) {
            return UNAVAILABLE;
        }

        DirectTarget target = resolvedTarget.get();
        Snapshot before = readVerifiedSnapshot(storage, target);
        if (before == null || !target.access().writer().supports(before.stored())) {
            throw directFailure("Could not verify unlimited extraction compensation state", target.target());
        }

        long restoredAmount = safeAdd(before.stored(), amount);
        if (restoredAmount < 0L || restoredAmount > before.capacity() || !target.access().writer().supports(restoredAmount)) {
            throw directFailure(
                    "Could not compensate " + amount + " FE with state " + before.stored() + "/" + before.capacity(),
                    target.target());
        }
        try {
            target.access().writer().write(target.target(), restoredAmount);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw mutationFailure(storage, target, before, amount, true,
                    "Unlimited extraction compensation write failed", exception);
        }

        Snapshot after = readVerifiedSnapshot(storage, target);
        if (after == null || after.stored() != restoredAmount || after.capacity() != before.capacity()) {
            throw mutationFailure(storage, target, before, amount, true,
                    "Unlimited extraction compensation failed read-back verification", null);
        }
        return amount;
    }

    @Override
    public void notifyStorageChanged(IEnergyStorage storage) {
        if (storage instanceof UnlimitedEnergyStorage typedStorage) {
            notifyTypedStorage(typedStorage);
        }
    }

    private static long transfer(IEnergyStorage storage, long amount, boolean simulate, boolean inserting) {
        Optional<DirectTarget> resolvedTarget = findDirectTarget(storage);
        if (resolvedTarget.isEmpty()) {
            return UNAVAILABLE;
        }

        DirectTarget target = resolvedTarget.get();
        Snapshot before = readVerifiedSnapshot(storage, target);
        if (before == null || !target.access().writer().supports(before.stored())) {
            throw directFailure("Could not verify unlimited energy state", target.target());
        }

        long available = inserting ? insertionSpace(before, target.access()) : before.stored();
        if (available <= 0L) {
            return 0L;
        }

        long requested = Math.min(amount, available);
        if (simulate) {
            return requested;
        }
        return writeAmountDirectly(storage, target, before, requested, inserting);
    }

    private static long writeAmountDirectly(IEnergyStorage storage, DirectTarget target, Snapshot before,
                                            long requested, boolean inserting) {
        long targetAmount = inserting ? safeAdd(before.stored(), requested) : before.stored() - requested;
        if (targetAmount < 0L || targetAmount > before.capacity() || !target.access().writer().supports(targetAmount)) {
            throw directFailure("Unlimited direct write cannot represent target state " + targetAmount,
                    target.target());
        }

        try {
            target.access().writer().write(target.target(), targetAmount);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw mutationFailure(storage, target, before, requested, inserting,
                    "Unlimited direct write invocation failed", exception);
        }

        Snapshot after = readVerifiedSnapshot(storage, target);
        if (after == null || after.stored() != targetAmount || after.capacity() != before.capacity()) {
            throw mutationFailure(storage, target, before, requested, inserting,
                    "Unlimited direct write failed read-back verification", null);
        }
        return requested;
    }

    private static UnlimitedEnergyAccessException mutationFailure(IEnergyStorage storage, DirectTarget target,
                                                                  Snapshot before, long requested, boolean inserting,
                                                                  String message, @Nullable Throwable cause) {
        RollbackResult rollback = rollback(storage, target, before);
        long mutationAmount = 0L;
        boolean mutationAmountKnown = false;
        if (rollback.snapshot() != null) {
            long changed = inserting ? rollback.snapshot().stored() - before.stored() : before.stored() - rollback.snapshot().stored();
            if (changed >= 0L && changed <= requested) {
                mutationAmount = changed;
                mutationAmountKnown = true;
            }
        }
        UnlimitedEnergyAccessException failure = directFailure(
                message, target.target(), cause, mutationAmountKnown, mutationAmount);
        if (rollback.failure() != null) {
            failure.addSuppressed(rollback.failure());
        }
        return failure;
    }

    private static RollbackResult rollback(IEnergyStorage storage, DirectTarget target, Snapshot before) {
        UnlimitedEnergyAccessException failure = null;
        try {
            target.access().writer().write(target.target(), before.stored());
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            failure = directFailure("Could not invoke unlimited energy rollback", target.target(), exception);
        }
        Snapshot afterRollback = readVerifiedSnapshot(storage, target);
        if (afterRollback == null || afterRollback.stored() != before.stored() || afterRollback.capacity() != before.capacity()) {
            UnlimitedEnergyAccessException verificationFailure = directFailure(
                    "Could not verify unlimited energy rollback", target.target());
            if (failure == null) {
                failure = verificationFailure;
            } else {
                failure.addSuppressed(verificationFailure);
            }
        }
        return new RollbackResult(afterRollback, failure);
    }

    private static long insertionSpace(Snapshot snapshot, StateAccess state) {
        long writableCapacity = Math.min(snapshot.capacity(), state.maxWritable());
        return writableCapacity <= snapshot.stored() ? 0L : writableCapacity - snapshot.stored();
    }

    @Nullable
    private static Snapshot readVerifiedSnapshot(IEnergyStorage storage, DirectTarget target) {
        Long stored = target.access().stored().read(target.target());
        Long capacity = target.access().capacity().read(target.target());
        if (stored == null || capacity == null || stored < 0L || capacity < stored) {
            return null;
        }

        Integer reportedStored = readCapabilityValue(storage, true);
        Integer reportedCapacity = readCapabilityValue(storage, false);
        if (reportedStored == null || reportedCapacity == null) {
            return null;
        }

        if (!matchesCapabilityValue(stored, reportedStored) || !matchesCapabilityValue(capacity, reportedCapacity)) {
            Data_Energistics.LOGGER.debug(
                    "Rejected inconsistent unlimited energy state on {}: direct={}/{}, reported={}/{}",
                    target.target().getClass().getName(), stored, capacity, reportedStored, reportedCapacity);
            return null;
        }
        return new Snapshot(stored, capacity);
    }

    private static Optional<DirectTarget> findDirectTarget(IEnergyStorage storage) {
        if (storage instanceof UnlimitedEnergyStorage) {
            return Optional.of(new DirectTarget(storage, TYPED_STATE_ACCESS));
        }
        if (storage.getClass() == EnergyStorage.class && storage instanceof NeoForgeEnergyStorageAccessor) {
            return Optional.of(new DirectTarget(storage, NEOFORGE_STATE_ACCESS));
        }
        return Optional.empty();
    }

    @Nullable
    private static Long readTypedStored(Object target) {
        try {
            return ((UnlimitedEnergyStorage) target).getStoredEnergyLong();
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error("Could not read typed unlimited stored energy from {}",
                    target.getClass().getName(), exception);
            return null;
        }
    }

    @Nullable
    private static Long readTypedCapacity(Object target) {
        try {
            return ((UnlimitedEnergyStorage) target).getEnergyCapacityLong();
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error("Could not read typed unlimited energy capacity from {}",
                    target.getClass().getName(), exception);
            return null;
        }
    }

    @Nullable
    private static Long readNeoForgeStored(Object target) {
        try {
            return (long) ((NeoForgeEnergyStorageAccessor) target).dataEnergistics$getEnergy();
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error("Could not read NeoForge stored energy from {}",
                    target.getClass().getName(), exception);
            return null;
        }
    }

    @Nullable
    private static Long readNeoForgeCapacity(Object target) {
        try {
            return (long) ((NeoForgeEnergyStorageAccessor) target).dataEnergistics$getCapacity();
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error("Could not read NeoForge energy capacity from {}",
                    target.getClass().getName(), exception);
            return null;
        }
    }

    private static void notifyTypedStorage(UnlimitedEnergyStorage storage) {
        try {
            storage.onUnlimitedEnergyChanged();
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw directFailure("Could not notify typed unlimited energy storage", storage, exception);
        }
    }

    private static boolean matchesCapabilityValue(long directValue, int reportedValue) {
        return reportedValue == Math.min(directValue, Integer.MAX_VALUE);
    }

    @Nullable
    private static Integer readCapabilityValue(IEnergyStorage storage, boolean stored) {
        String description = stored ? "stored energy" : "energy capacity";
        try {
            int value = stored ? storage.getEnergyStored() : storage.getMaxEnergyStored();
            if (value >= 0) {
                return value;
            }
            Data_Energistics.LOGGER.error("Energy capability {} reported negative {}: {}",
                    storage.getClass().getName(), description, value);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error("Energy capability {} failed to report {}",
                    storage.getClass().getName(), description, exception);
        }
        return null;
    }

    private static boolean readCapabilityPermission(IEnergyStorage storage, boolean receive) {
        String description = receive ? "receive" : "extract";
        try {
            return receive ? storage.canReceive() : storage.canExtract();
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error("Energy capability {} failed to report {} permission",
                    storage.getClass().getName(), description, exception);
            return false;
        }
    }

    private static long safeAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MIN_VALUE : left + right;
    }

    private static UnlimitedEnergyAccessException directFailure(String message, Object target) {
        return directFailure(message, target, null);
    }

    private static UnlimitedEnergyAccessException directFailure(String message, Object target,
                                                                @Nullable Throwable cause) {
        return directFailure(message, target, cause, true, 0L);
    }

    private static UnlimitedEnergyAccessException directFailure(String message, Object target,
                                                                @Nullable Throwable cause,
                                                                boolean mutationAmountKnown, long mutationAmount) {
        String contextualMessage = message + " on " + target.getClass().getName();
        return new UnlimitedEnergyAccessException(contextualMessage, cause, mutationAmountKnown, mutationAmount);
    }

    private static void validateRequestedAmount(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Energy amount must not be negative: " + amount);
        }
    }

    private interface AmountReader {

        @Nullable
        Long read(Object target);
    }

    private interface AmountWriter {

        void write(Object target, long amount);

        long maxValue();

        default boolean supports(long amount) {
            return amount >= 0L && amount <= maxValue();
        }
    }

    private record StateAccess(AmountReader stored, AmountReader capacity, AmountWriter writer, long maxWritable) {}

    private record DirectTarget(Object target, StateAccess access) {}

    private record Snapshot(long stored, long capacity) {}

    private record RollbackResult(@Nullable Snapshot snapshot, @Nullable UnlimitedEnergyAccessException failure) {}
}
