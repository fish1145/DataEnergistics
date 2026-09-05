package com.fish_dan_.data_energistics.common.compartment;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Dynamic aggregate view over structure-facing compartment storages.
 *
 * <p>
 * Hosts expose this so main structure business can access compartment IO through interfaces without depending on
 * concrete block entities.
 */
public final class CompartmentStorageGroup implements CompartmentStorage {

    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";

    private final Supplier<Collection<CompartmentStorage>> storages;

    public CompartmentStorageGroup(Supplier<Collection<CompartmentStorage>> storages) {
        this.storages = storages;
    }

    @Override
    public long insert(AEKey key, long amount, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        for (CompartmentStorage storage : currentStorages()) {
            if (remaining <= 0L) {
                break;
            }
            long inserted = storage.insert(key, remaining, simulate);
            validateTransfer("insert", inserted, remaining);
            remaining -= inserted;
        }
        return amount - remaining;
    }

    @Override
    public long extract(AEKey key, long amount, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        for (CompartmentStorage storage : currentStorages()) {
            if (remaining <= 0L) {
                break;
            }
            long extracted = storage.extract(key, remaining, simulate);
            validateTransfer("extract", extracted, remaining);
            remaining -= extracted;
        }
        return amount - remaining;
    }

    @Override
    public long amount(AEKey key) {
        long total = 0L;
        for (CompartmentStorage storage : currentStorages()) {
            long amount = storage.amount(key);
            if (amount < 0L) {
                throw new IllegalStateException("Backing compartment storage returned negative amount: " + amount);
            }
            total = saturatingAdd(total, amount);
        }
        return total;
    }

    @Override
    public boolean isEmpty() {
        for (CompartmentStorage storage : currentStorages()) {
            if (!storage.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object2LongMap<AEKey> entries() {
        Object2LongOpenHashMap<AEKey> aggregate = new Object2LongOpenHashMap<>();
        for (CompartmentStorage storage : currentStorages()) {
            for (Object2LongMap.Entry<AEKey> entry : storage.entries().object2LongEntrySet()) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0L) {
                    throw new IllegalStateException("Backing compartment storage exposed invalid amount: " + amount);
                }
                aggregate.put(key, saturatingAdd(aggregate.getLong(key), amount));
            }
        }
        return Object2LongMaps.unmodifiable(aggregate);
    }

    @Override
    public void clear() {
        for (CompartmentStorage storage : currentStorages()) {
            storage.clear();
        }
    }

    @Override
    public ListTag serializeNBT(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Object2LongMap.Entry<AEKey> entry : entries().object2LongEntrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.put(KEY_TAG, entry.getKey().toTagGeneric(registries));
            tag.putLong(AMOUNT_TAG, entry.getLongValue());
            list.add(tag);
        }
        return list;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, ListTag tag) {
        throw new IllegalStateException("Compartment storage group cannot deserialize aggregate data without a distribution policy");
    }

    private Collection<CompartmentStorage> currentStorages() {
        return this.storages.get();
    }

    private static void validateTransfer(String operation, long transferred, long requested) {
        if (transferred < 0L || transferred > requested) {
            throw new IllegalStateException(
                    "Backing compartment storage returned invalid " + operation + " amount " + transferred +
                            " for request " + requested);
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
