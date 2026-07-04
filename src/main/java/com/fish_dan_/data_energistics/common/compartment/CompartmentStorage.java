package com.fish_dan_.data_energistics.common.compartment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * Stores real compartment contents as AE keys with long amounts.
 *
 * <p>UI slots are views or configuration surfaces. This storage is the authoritative key amount state.
 */
public interface CompartmentStorage {

    /**
     * Inserts up to {@code amount} for {@code key}.
     *
     * @return inserted amount
     */
    long insert(AEKey key, long amount, boolean simulate);

    /**
     * Extracts up to {@code amount} for {@code key}.
     *
     * @return extracted amount
     */
    long extract(AEKey key, long amount, boolean simulate);

    /**
     * Returns the stored amount for {@code key}.
     */
    long amount(AEKey key);

    /**
     * Returns whether no positive key amounts are stored.
     */
    boolean isEmpty();

    /**
     * Returns read-only entry view of stored keys.
     */
    Object2LongMap<AEKey> entries();

    /**
     * Clears all stored contents.
     */
    void clear();

    /**
     * Serializes storage to NBT.
     */
    ListTag serializeNBT(HolderLookup.Provider registries);

    /**
     * Replaces current storage with NBT data.
     */
    void deserializeNBT(HolderLookup.Provider registries, ListTag tag);
}
