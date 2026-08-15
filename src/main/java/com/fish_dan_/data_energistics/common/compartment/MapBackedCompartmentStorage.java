package com.fish_dan_.data_energistics.common.compartment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Map-backed compartment storage.
 */
public class MapBackedCompartmentStorage implements CompartmentStorage {

    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";

    private final Object2LongOpenHashMap<AEKey> contents = new Object2LongOpenHashMap<>();
    private final Runnable listener;

    public MapBackedCompartmentStorage(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public long insert(AEKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0L;
        }
        long current = this.contents.getLong(key);
        long inserted = saturatingAdd(current, amount) - current;
        if (inserted <= 0) {
            return 0L;
        }
        if (!simulate) {
            this.contents.put(key, current + inserted);
            this.listener.run();
        }
        return inserted;
    }

    @Override
    public long extract(AEKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0L;
        }
        long current = this.contents.getLong(key);
        long extracted = Math.min(current, amount);
        if (extracted <= 0) {
            return 0L;
        }
        if (!simulate) {
            long remaining = current - extracted;
            if (remaining <= 0) {
                this.contents.removeLong(key);
            } else {
                this.contents.put(key, remaining);
            }
            this.listener.run();
        }
        return extracted;
    }

    @Override
    public long amount(AEKey key) {
        return this.contents.getLong(key);
    }

    @Override
    public boolean isEmpty() {
        return this.contents.isEmpty();
    }

    @Override
    public Object2LongMap<AEKey> entries() {
        return Object2LongMaps.unmodifiable(this.contents);
    }

    @Override
    public void clear() {
        if (this.contents.isEmpty()) {
            return;
        }
        this.contents.clear();
        this.listener.run();
    }

    @Override
    public ListTag serializeNBT(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Object2LongMap.Entry<AEKey> entry : this.contents.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.put(KEY_TAG, entry.getKey().toTagGeneric(registries));
            tag.putLong(AMOUNT_TAG, entry.getLongValue());
            list.add(tag);
        }
        return list;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, ListTag tag) {
        this.contents.clear();
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag entryTag = tag.getCompound(i);
            @Nullable
            AEKey key = AEKey.fromTagGeneric(registries, entryTag.getCompound(KEY_TAG));
            long amount = entryTag.getLong(AMOUNT_TAG);
            if (key != null && amount > 0) {
                this.contents.put(key, amount);
            }
        }
        this.listener.run();
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
