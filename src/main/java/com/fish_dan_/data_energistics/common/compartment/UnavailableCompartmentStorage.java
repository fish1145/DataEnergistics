package com.fish_dan_.data_energistics.common.compartment;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * Read-only empty storage returned when a compartment role is not currently available to a formed structure.
 */
public final class UnavailableCompartmentStorage implements CompartmentStorage {

    public static final UnavailableCompartmentStorage INSTANCE = new UnavailableCompartmentStorage();

    private static final Object2LongMap<AEKey> EMPTY_ENTRIES = Object2LongMaps.unmodifiable(
            new Object2LongOpenHashMap<>());

    private UnavailableCompartmentStorage() {}

    @Override
    public long insert(AEKey key, long amount, boolean simulate) {
        return 0L;
    }

    @Override
    public long extract(AEKey key, long amount, boolean simulate) {
        return 0L;
    }

    @Override
    public long amount(AEKey key) {
        return 0L;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Object2LongMap<AEKey> entries() {
        return EMPTY_ENTRIES;
    }

    @Override
    public void clear() {}

    @Override
    public ListTag serializeNBT(HolderLookup.Provider registries) {
        return new ListTag();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, ListTag tag) {
        throw new IllegalStateException("Unavailable compartment storage cannot load persistent data");
    }
}
