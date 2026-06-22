package com.fish_dan_.data_energistics.integration.energy;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed direct energy access implementation for optional integration storages.
 *
 * <p>
 * This adapter is deliberately isolated from block entity logic. It discovers a small set of known storage field and
 * method shapes once per class, logs access failures, and returns {@link DirectEnergyAccess#INSERT_UNAVAILABLE} when a
 * target cannot be proven safe to mutate.
 */
public final class DirectEnergyAccessImpl implements DirectEnergyAccess {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final List<String> ENERGY_FIELD_NAMES = List.of("energy", "storedEnergy", "energyStored", "stored", "amount");
    private static final List<String> CAPACITY_FIELD_NAMES = List.of("capacity", "maxEnergy", "maxEnergyStored", "maxStored", "maxStorage");
    private static final List<String> WRAPPER_FIELD_NAMES = List.of("container", "storage", "delegate", "wrapped", "backingStorage");
    private static final Map<Class<?>, Optional<DirectEnergyStorageAccess>> STORAGE_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<VarHandle>> WRAPPER_FIELD_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean canReceive(IEnergyStorage storage) {
        return getDirectEnergyStorageTarget(storage).isPresent();
    }

    @Override
    public long insert(IEnergyStorage storage, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        Optional<DirectEnergyStorageTarget> target = getDirectEnergyStorageTarget(storage);
        return target.map(directEnergyStorageTarget -> directEnergyStorageTarget.insert(storage, amount, simulate))
                .orElse(INSERT_UNAVAILABLE);
    }

    @Override
    public void notifyStorageChanged(IEnergyStorage storage) {
        invokeNoArgBestEffort(storage, "onEnergyChanged");
        invokeNoArgBestEffort(storage, "onContentsChanged");
    }

    private static Optional<DirectEnergyStorageTarget> getDirectEnergyStorageTarget(IEnergyStorage storage) {
        Optional<DirectEnergyStorageTarget> unwrapped = getUnwrappedDirectEnergyStorageTarget(storage);
        if (unwrapped.isPresent()) {
            return unwrapped;
        }

        return getDirectEnergyStorageAccess(storage.getClass())
                .map(access -> new DirectEnergyStorageTarget(storage, access));
    }

    private static Optional<DirectEnergyStorageTarget> getUnwrappedDirectEnergyStorageTarget(IEnergyStorage storage) {
        Optional<VarHandle> wrapperField = WRAPPER_FIELD_CACHE.computeIfAbsent(storage.getClass(), DirectEnergyAccessImpl::resolveDirectEnergyWrapperField);
        if (wrapperField.isEmpty()) {
            return Optional.empty();
        }

        Object target = readDirectEnergyWrapperTarget(wrapperField.get(), storage);
        if (target == null || target == storage) {
            return Optional.empty();
        }

        Optional<DirectEnergyStorageAccess> access = getDirectEnergyStorageAccess(target.getClass());
        if (access.isPresent()) {
            return Optional.of(new DirectEnergyStorageTarget(target, access.get()));
        }

        if (target instanceof IEnergyStorage nestedStorage) {
            return getDirectEnergyStorageTarget(nestedStorage);
        }
        return Optional.empty();
    }

    private static Optional<DirectEnergyStorageAccess> getDirectEnergyStorageAccess(Class<?> storageClass) {
        return STORAGE_ACCESS_CACHE.computeIfAbsent(storageClass, DirectEnergyAccessImpl::resolveDirectEnergyStorageAccess);
    }

    private static Optional<DirectEnergyStorageAccess> resolveDirectEnergyStorageAccess(Class<?> storageClass) {
        Optional<Method> insertIgnoringLimit = findDirectInsertIgnoringLimit(storageClass);
        Optional<DirectEnergyAmountMethods> amountMethods = findDirectEnergyAmountMethods(storageClass);
        Optional<VarHandle> storedEnergy = findDirectNumericField(storageClass, ENERGY_FIELD_NAMES, true);
        if (insertIgnoringLimit.isEmpty() && amountMethods.isEmpty() && storedEnergy.isEmpty()) {
            return Optional.empty();
        }

        Optional<VarHandle> capacity = findDirectNumericField(storageClass, CAPACITY_FIELD_NAMES, false);
        return Optional.of(new DirectEnergyStorageAccess(
                storedEnergy.orElse(null),
                capacity.orElse(null),
                insertIgnoringLimit.orElse(null),
                amountMethods.orElse(null)));
    }

    private static Optional<VarHandle> resolveDirectEnergyWrapperField(Class<?> storageClass) {
        return findDirectObjectField(storageClass, WRAPPER_FIELD_NAMES);
    }

    @Nullable
    private static Object readDirectEnergyWrapperTarget(VarHandle handle, Object storage) {
        try {
            return handle.get(storage);
        } catch (RuntimeException | LinkageError e) {
            Data_Energistics.LOGGER.debug("Could not read direct energy wrapper field from {}", storage.getClass().getName(), e);
            return null;
        }
    }

    private static Optional<Method> findDirectInsertIgnoringLimit(Class<?> owner) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("insertIgnoringLimit", long.class, boolean.class);
                method.setAccessible(true);
                if (method.getReturnType() == long.class) {
                    return Optional.of(method);
                }
                Data_Energistics.LOGGER.debug("Direct energy method {}#insertIgnoringLimit has unsupported return type {}", type.getName(), method.getReturnType().getName());
                return Optional.empty();
            } catch (NoSuchMethodException e) {
                Data_Energistics.LOGGER.trace("Direct energy method {}#insertIgnoringLimit not found", type.getName(), e);
                type = type.getSuperclass();
            } catch (RuntimeException e) {
                Data_Energistics.LOGGER.debug("Could not inspect direct energy insert method on {}", type.getName(), e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<VarHandle> findDirectObjectField(Class<?> owner, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || field.getType().isPrimitive()) {
                        break;
                    }

                    field.setAccessible(true);
                    return Optional.of(MethodHandles.privateLookupIn(type, LOOKUP).unreflectVarHandle(field));
                } catch (NoSuchFieldException e) {
                    Data_Energistics.LOGGER.trace("Direct energy wrapper field {}#{} not found", type.getName(), fieldName, e);
                    type = type.getSuperclass();
                } catch (IllegalAccessException | RuntimeException | LinkageError e) {
                    Data_Energistics.LOGGER.debug("Could not inspect direct energy wrapper field {}#{}", type.getName(), fieldName, e);
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<DirectEnergyAmountMethods> findDirectEnergyAmountMethods(Class<?> owner) {
        Optional<Method> getAmount = findDirectNoArgLongMethod(owner, "getAmount");
        Optional<Method> getCapacity = findDirectNoArgLongMethod(owner, "getCapacity");
        Optional<Method> setAmount = findDirectSingleLongMethod(owner, "setAmount", void.class);
        if (getAmount.isEmpty() || getCapacity.isEmpty() || setAmount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DirectEnergyAmountMethods(getAmount.get(), getCapacity.get(), setAmount.get()));
    }

    private static Optional<Method> findDirectNoArgLongMethod(Class<?> owner, String methodName) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                if (method.getReturnType() == long.class) {
                    return Optional.of(method);
                }
                Data_Energistics.LOGGER.debug("Direct energy method {}#{} has unsupported return type {}", type.getName(), methodName, method.getReturnType().getName());
                return Optional.empty();
            } catch (NoSuchMethodException e) {
                Data_Energistics.LOGGER.trace("Direct energy method {}#{} not found", type.getName(), methodName, e);
                type = type.getSuperclass();
            } catch (RuntimeException e) {
                Data_Energistics.LOGGER.debug("Could not inspect direct energy method {}#{}", type.getName(), methodName, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> findDirectSingleLongMethod(Class<?> owner, String methodName, Class<?> returnType) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName, long.class);
                method.setAccessible(true);
                if (method.getReturnType() == returnType) {
                    return Optional.of(method);
                }
                Data_Energistics.LOGGER.debug("Direct energy method {}#{} has unsupported return type {}", type.getName(), methodName, method.getReturnType().getName());
                return Optional.empty();
            } catch (NoSuchMethodException e) {
                Data_Energistics.LOGGER.trace("Direct energy method {}#{} not found", type.getName(), methodName, e);
                type = type.getSuperclass();
            } catch (RuntimeException e) {
                Data_Energistics.LOGGER.debug("Could not inspect direct energy method {}#{}", type.getName(), methodName, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<VarHandle> findDirectNumericField(Class<?> owner, List<String> fieldNames, boolean writable) {
        for (String fieldName : fieldNames) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || writable && Modifier.isFinal(modifiers) || !isDirectEnergyFieldType(field.getType())) {
                        break;
                    }

                    field.setAccessible(true);
                    return Optional.of(MethodHandles.privateLookupIn(type, LOOKUP).unreflectVarHandle(field));
                } catch (NoSuchFieldException e) {
                    Data_Energistics.LOGGER.trace("Direct energy numeric field {}#{} not found", type.getName(), fieldName, e);
                    type = type.getSuperclass();
                } catch (IllegalAccessException | RuntimeException | LinkageError e) {
                    Data_Energistics.LOGGER.debug("Could not inspect direct energy numeric field {}#{}", type.getName(), fieldName, e);
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isDirectEnergyFieldType(Class<?> fieldType) {
        return fieldType == int.class || fieldType == long.class;
    }

    @Nullable
    private static Long invokeLong(Method method, Object target) {
        Object result = invoke(method, target);
        return result instanceof Number number ? number.longValue() : null;
    }

    @Nullable
    private static Object invoke(Method method, @Nullable Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Data_Energistics.LOGGER.debug("Could not invoke direct energy method {}#{}", method.getDeclaringClass().getName(), method.getName(), e);
            return null;
        }
    }

    private static void invokeNoArgBestEffort(Object target, String methodName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                invoke(method, target);
                return;
            } catch (NoSuchMethodException e) {
                Data_Energistics.LOGGER.trace("Direct energy notification method {}#{} not found", type.getName(), methodName, e);
                type = type.getSuperclass();
            } catch (RuntimeException e) {
                Data_Energistics.LOGGER.debug("Could not inspect direct energy notification method {}#{}", type.getName(), methodName, e);
                return;
            }
        }
    }

    private static int clampStoredAmount(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    private record DirectEnergyStorageTarget(Object target, DirectEnergyStorageAccess access) {

        private long insert(IEnergyStorage storage, long amount, boolean simulate) {
            return this.access.insert(storage, this.target, amount, simulate);
        }
    }

    private record DirectEnergyAmountMethods(Method getAmount, Method getCapacity, Method setAmount) {}

    private record DirectEnergyStorageAccess(@Nullable VarHandle storedEnergy, @Nullable VarHandle capacity,
                                             @Nullable Method insertIgnoringLimit,
                                             @Nullable DirectEnergyAmountMethods amountMethods) {

        private long insert(IEnergyStorage storage, Object target, long amount, boolean simulate) {
            long methodInserted = insertIgnoringLimit(target, amount, simulate);
            if (methodInserted != INSERT_UNAVAILABLE) {
                if (!simulate && methodInserted > 0) {
                    notifyDirectTargetChanged(target);
                }
                return methodInserted;
            }

            methodInserted = insertByAmountMethods(storage, target, amount, simulate);
            if (methodInserted != INSERT_UNAVAILABLE) {
                return methodInserted;
            }

            if (this.storedEnergy == null) {
                return INSERT_UNAVAILABLE;
            }

            Long current = readEnergyAmount(this.storedEnergy, target);
            if (current == null || current < 0 || !matchesReportedAmount(current, storage.getEnergyStored())) {
                return INSERT_UNAVAILABLE;
            }

            Long directCapacity = this.capacity == null ? null : readEnergyAmount(this.capacity, target);
            if (directCapacity != null && directCapacity < 0) {
                return INSERT_UNAVAILABLE;
            }

            long maxStored = directCapacity == null ? Math.max(0, storage.getMaxEnergyStored()) : directCapacity;
            if (directCapacity != null && !matchesReportedCapacity(directCapacity, storage.getMaxEnergyStored())) {
                return INSERT_UNAVAILABLE;
            }

            maxStored = Math.min(maxStored, getMaxStoredValue());
            if (maxStored <= current) {
                return 0;
            }

            long inserted = Math.min(amount, maxStored - current);
            if (inserted <= 0) {
                return 0;
            }
            if (simulate) {
                return inserted;
            }

            long targetAmount = current + inserted;
            if (!writeEnergyAmount(this.storedEnergy, target, targetAmount)) {
                return INSERT_UNAVAILABLE;
            }

            Long updated = readEnergyAmount(this.storedEnergy, target);
            if (updated == null || updated != targetAmount || !matchesReportedAmount(targetAmount, storage.getEnergyStored())) {
                writeEnergyAmount(this.storedEnergy, target, current);
                return INSERT_UNAVAILABLE;
            }
            notifyDirectTargetChanged(target);
            return inserted;
        }

        private long getMaxStoredValue() {
            return this.storedEnergy.varType() == int.class ? Integer.MAX_VALUE : Long.MAX_VALUE;
        }

        private long insertByAmountMethods(IEnergyStorage storage, Object target, long amount, boolean simulate) {
            if (this.amountMethods == null) {
                return INSERT_UNAVAILABLE;
            }

            Long current = invokeLong(this.amountMethods.getAmount(), target);
            if (current == null || current < 0 || !matchesReportedAmount(current, storage.getEnergyStored())) {
                return INSERT_UNAVAILABLE;
            }

            Long capacity = invokeLong(this.amountMethods.getCapacity(), target);
            if (capacity == null || capacity < 0 || !matchesReportedCapacity(capacity, storage.getMaxEnergyStored())) {
                return INSERT_UNAVAILABLE;
            }
            if (capacity <= current) {
                return 0;
            }

            long inserted = Math.min(amount, capacity - current);
            if (inserted <= 0) {
                return 0;
            }
            if (simulate) {
                return inserted;
            }

            long targetAmount = current + inserted;
            invoke(this.amountMethods.setAmount(), target, targetAmount);
            Long updated = invokeLong(this.amountMethods.getAmount(), target);
            if (updated == null || updated != targetAmount || !matchesReportedAmount(targetAmount, storage.getEnergyStored())) {
                invoke(this.amountMethods.setAmount(), target, current);
                return INSERT_UNAVAILABLE;
            }
            notifyDirectTargetChanged(target);
            return inserted;
        }

        private long insertIgnoringLimit(Object target, long amount, boolean simulate) {
            if (this.insertIgnoringLimit == null) {
                return INSERT_UNAVAILABLE;
            }

            Object result = invoke(this.insertIgnoringLimit, target, amount, simulate);
            return result instanceof Number number ? number.longValue() : INSERT_UNAVAILABLE;
        }

        @Nullable
        private static Long readEnergyAmount(VarHandle handle, Object target) {
            try {
                Object value = handle.get(target);
                return value instanceof Number number ? number.longValue() : null;
            } catch (RuntimeException | LinkageError e) {
                Data_Energistics.LOGGER.debug("Could not read direct energy amount from {}", target.getClass().getName(), e);
                return null;
            }
        }

        private static boolean writeEnergyAmount(VarHandle handle, Object target, long amount) {
            try {
                if (handle.varType() == int.class) {
                    if (amount > Integer.MAX_VALUE || amount < Integer.MIN_VALUE) {
                        return false;
                    }
                    handle.set(target, (int) amount);
                } else if (handle.varType() == long.class) {
                    handle.set(target, amount);
                } else {
                    return false;
                }
                return true;
            } catch (RuntimeException | LinkageError e) {
                Data_Energistics.LOGGER.debug("Could not write direct energy amount to {}", target.getClass().getName(), e);
                return false;
            }
        }

        private static boolean matchesReportedAmount(long directAmount, int reportedAmount) {
            return reportedAmount == clampStoredAmount(directAmount);
        }

        private static boolean matchesReportedCapacity(long directCapacity, int reportedCapacity) {
            return reportedCapacity <= 0 || matchesReportedAmount(directCapacity, reportedCapacity);
        }

        private static void notifyDirectTargetChanged(Object target) {
            invokeNoArgBestEffort(target, "update");
        }
    }
}
