package com.fish_dan_.data_energistics.common.compartment;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Dynamic structure-side view that hides a backing storage whenever its compartment is unavailable.
 */
public final class AvailabilityCheckedCompartmentStorage implements CompartmentStorage {

    private final BooleanSupplier available;
    private final Supplier<CompartmentStorage> backing;
    private final Runnable beforeAccess;

    public AvailabilityCheckedCompartmentStorage(BooleanSupplier available,
                                                 Supplier<CompartmentStorage> backing) {
        this(available, backing, () -> {});
    }

    public AvailabilityCheckedCompartmentStorage(BooleanSupplier available,
                                                 Supplier<CompartmentStorage> backing,
                                                 Runnable beforeAccess) {
        this.available = available;
        this.backing = backing;
        this.beforeAccess = beforeAccess;
    }

    @Override
    public long insert(AEKey key, long amount, boolean simulate) {
        CompartmentStorage storage = availableStorage();
        return storage != null ? storage.insert(key, amount, simulate) : 0L;
    }

    @Override
    public long extract(AEKey key, long amount, boolean simulate) {
        CompartmentStorage storage = availableStorage();
        return storage != null ? storage.extract(key, amount, simulate) : 0L;
    }

    @Override
    public long amount(AEKey key) {
        CompartmentStorage storage = availableStorage();
        return storage != null ? storage.amount(key) : 0L;
    }

    @Override
    public boolean isEmpty() {
        CompartmentStorage storage = availableStorage();
        return storage == null || storage.isEmpty();
    }

    @Override
    public Object2LongMap<AEKey> entries() {
        CompartmentStorage storage = availableStorage();
        return storage != null ? storage.entries() : UnavailableCompartmentStorage.INSTANCE.entries();
    }

    @Override
    public void clear() {
        CompartmentStorage storage = availableStorage();
        if (storage != null) {
            storage.clear();
        }
    }

    @Override
    public ListTag serializeNBT(HolderLookup.Provider registries) {
        CompartmentStorage storage = availableStorage();
        return storage != null ? storage.serializeNBT(registries) : UnavailableCompartmentStorage.INSTANCE.serializeNBT(registries);
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, ListTag tag) {
        CompartmentStorage storage = availableStorage();
        if (storage == null) {
            UnavailableCompartmentStorage.INSTANCE.deserializeNBT(registries, tag);
            return;
        }
        storage.deserializeNBT(registries, tag);
    }

    private @Nullable CompartmentStorage availableStorage() {
        if (!this.available.getAsBoolean()) {
            return null;
        }
        this.beforeAccess.run();
        return this.backing.get();
    }
}
