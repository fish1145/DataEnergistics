package com.fish_dan_.data_energistics.integration.oritech;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OritechEnergyIntegration {

    private static final String ENERGY_API_CLASS = "rearth.oritech.api.energy.EnergyApi";
    private static final String BLOCK_PROVIDER_CLASS = "rearth.oritech.api.energy.EnergyApi$BlockProvider";
    private static final String ENERGY_STORAGE_CLASS = "rearth.oritech.api.energy.EnergyApi$EnergyStorage";
    private static final String BLOCK_ENERGY_API_CLASS = "rearth.oritech.api.energy.BlockEnergyApi";
    private static final long DIRECT_INSERT_UNAVAILABLE = Long.MIN_VALUE;

    private static boolean initialized;
    private static boolean available;
    private static Class<?> blockProviderClass;
    private static Class<?> energyStorageClass;
    private static MethodHandle blockEnergyFindMethod;
    private static MethodHandle providerGetEnergyStorageMethod;
    private static MethodHandle storageInsertMethod;
    private static MethodHandle storageExtractMethod;
    private static MethodHandle storageGetAmountMethod;
    private static MethodHandle storageGetCapacityMethod;
    private static MethodHandle storageUpdateMethod;
    private static MethodHandle storageSupportsInsertionMethod;
    private static MethodHandle storageSupportsExtractionMethod;
    private static Optional<VarHandle> blockEnergyApiField = Optional.empty();
    private static final Map<Class<?>, Optional<DirectEnergyFieldAccess>> DIRECT_FIELD_ACCESS_CACHE = new ConcurrentHashMap<>();

    private OritechEnergyIntegration() {}

    @Nullable
    public static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
        if (!isReady()) {
            return null;
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Object storage = findViaBlockEnergyApi(level, pos, state, blockEntity, side);
        if (storage == null && blockProviderClass.isInstance(blockEntity)) {
            storage = invoke(providerGetEnergyStorageMethod, blockEntity, side);
        }
        return energyStorageClass.isInstance(storage) ? new OritechEnergyStorage(storage) : null;
    }

    private static boolean isReady() {
        if (!initialized) {
            initialize();
        }
        return available;
    }

    private static void initialize() {
        initialized = true;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> energyApiClass = Class.forName(ENERGY_API_CLASS);
            Class<?> blockEnergyApiClass = Class.forName(BLOCK_ENERGY_API_CLASS);
            blockProviderClass = Class.forName(BLOCK_PROVIDER_CLASS);
            energyStorageClass = Class.forName(ENERGY_STORAGE_CLASS);
            blockEnergyApiField = ReflectionAccess.findStaticField(energyApiClass, "BLOCK");
            blockEnergyFindMethod = lookup.findVirtual(
                    blockEnergyApiClass,
                    "find",
                    MethodType.methodType(energyStorageClass, Level.class, BlockPos.class, BlockState.class,
                            BlockEntity.class, Direction.class));
            providerGetEnergyStorageMethod = lookup.findVirtual(
                    blockProviderClass,
                    "getEnergyStorage",
                    MethodType.methodType(energyStorageClass, Direction.class));
            storageInsertMethod = lookup.findVirtual(
                    energyStorageClass,
                    "insert",
                    MethodType.methodType(long.class, long.class, boolean.class));
            storageExtractMethod = lookup.findVirtual(
                    energyStorageClass,
                    "extract",
                    MethodType.methodType(long.class, long.class, boolean.class));
            storageGetAmountMethod = lookup.findVirtual(
                    energyStorageClass,
                    "getAmount",
                    MethodType.methodType(long.class));
            storageGetCapacityMethod = lookup.findVirtual(
                    energyStorageClass,
                    "getCapacity",
                    MethodType.methodType(long.class));
            storageUpdateMethod = lookup.findVirtual(
                    energyStorageClass,
                    "update",
                    MethodType.methodType(void.class));
            storageSupportsInsertionMethod = lookup.findVirtual(
                    energyStorageClass,
                    "supportsInsertion",
                    MethodType.methodType(boolean.class));
            storageSupportsExtractionMethod = lookup.findVirtual(
                    energyStorageClass,
                    "supportsExtraction",
                    MethodType.methodType(boolean.class));
            available = blockEnergyApiField.isPresent();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            available = false;
        }
    }

    @Nullable
    private static Object findViaBlockEnergyApi(Level level, BlockPos pos, BlockState state,
                                                @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        Object blockEnergyApi = ReflectionAccess.getField(blockEnergyApiField, null);
        return blockEnergyApi == null ? null : invoke(blockEnergyFindMethod, blockEnergyApi, level, pos, state, blockEntity, side);
    }

    @Nullable
    private static Object invoke(MethodHandle method, Object... args) {
        try {
            return method.invokeWithArguments(args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long invokeLong(MethodHandle method, Object... args) {
        Object value = invoke(method, args);
        return value instanceof Long longValue ? longValue : 0L;
    }

    private static boolean invokeBoolean(MethodHandle method, Object... args) {
        Object value = invoke(method, args);
        return value instanceof Boolean bool && bool;
    }

    private static int clampToInt(long amount) {
        if (amount <= 0L) {
            return 0;
        }
        return amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    private static Optional<DirectEnergyFieldAccess> getDirectFieldAccess(Class<?> storageClass) {
        return DIRECT_FIELD_ACCESS_CACHE.computeIfAbsent(storageClass, OritechEnergyIntegration::resolveDirectFieldAccess);
    }

    private static Optional<DirectEnergyFieldAccess> resolveDirectFieldAccess(Class<?> storageClass) {
        Optional<VarHandle> amount = findLongField(storageClass, "amount", true);
        Optional<VarHandle> capacity = findLongField(storageClass, "capacity", false);
        if (amount.isEmpty() || capacity.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DirectEnergyFieldAccess(amount.get(), capacity.get()));
    }

    private static Optional<VarHandle> findLongField(Class<?> owner, String fieldName, boolean writable) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.getType() != long.class || writable && Modifier.isFinal(modifiers)) {
                    return Optional.empty();
                }

                field.setAccessible(true);
                return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(field));
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private record DirectEnergyFieldAccess(VarHandle amount, VarHandle capacity) {

        private long insert(Object storage, long maxReceive, boolean simulate) {
            if (maxReceive <= 0L) {
                return 0L;
            }

            Long current = readLong(this.amount, storage);
            Long max = readLong(this.capacity, storage);
            if (current == null || max == null || current < 0L || max <= current) {
                return 0L;
            }

            long inserted = Math.min(maxReceive, max - current);
            if (inserted <= 0L || simulate) {
                return inserted;
            }

            try {
                this.amount.set(storage, current + inserted);
                return inserted;
            } catch (RuntimeException | LinkageError ignored) {
                return DIRECT_INSERT_UNAVAILABLE;
            }
        }

        private boolean canReceive(Object storage) {
            Long current = readLong(this.amount, storage);
            Long max = readLong(this.capacity, storage);
            return current != null && max != null && current >= 0L && max > current;
        }

        @Nullable
        private static Long readLong(VarHandle handle, Object storage) {
            try {
                Object value = handle.get(storage);
                return value instanceof Long longValue ? longValue : null;
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }
    }

    private static final class OritechEnergyStorage implements IEnergyStorage {

        private final Object storage;

        private OritechEnergyStorage(Object storage) {
            this.storage = storage;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || !canReceive()) {
                return 0;
            }

            long inserted = insertEnergyDirectly(maxReceive, simulate);
            if (inserted == DIRECT_INSERT_UNAVAILABLE) {
                inserted = invokeLong(storageInsertMethod, this.storage, (long) maxReceive, simulate);
            }
            if (!simulate && inserted > 0L) {
                invoke(storageUpdateMethod, this.storage);
            }
            return clampToInt(inserted);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0 || !canExtract()) {
                return 0;
            }

            long extracted = invokeLong(storageExtractMethod, this.storage, (long) maxExtract, simulate);
            if (!simulate && extracted > 0L) {
                invoke(storageUpdateMethod, this.storage);
            }
            return clampToInt(extracted);
        }

        @Override
        public int getEnergyStored() {
            return clampToInt(invokeLong(storageGetAmountMethod, this.storage));
        }

        @Override
        public int getMaxEnergyStored() {
            return clampToInt(invokeLong(storageGetCapacityMethod, this.storage));
        }

        @Override
        public boolean canExtract() {
            return invokeBoolean(storageSupportsExtractionMethod, this.storage) && invokeLong(storageExtractMethod, this.storage, 1L, true) > 0L;
        }

        @Override
        public boolean canReceive() {
            if (!invokeBoolean(storageSupportsInsertionMethod, this.storage)) {
                return false;
            }

            Optional<DirectEnergyFieldAccess> directAccess = getDirectFieldAccess(this.storage.getClass());
            return directAccess.map(access -> access.canReceive(this.storage))
                    .orElseGet(() -> invokeLong(storageInsertMethod, this.storage, 1L, true) > 0L);
        }

        private long insertEnergyDirectly(long maxReceive, boolean simulate) {
            Optional<DirectEnergyFieldAccess> directAccess = getDirectFieldAccess(this.storage.getClass());
            return directAccess.map(access -> access.insert(this.storage, maxReceive, simulate))
                    .orElse(DIRECT_INSERT_UNAVAILABLE);
        }
    }
}
