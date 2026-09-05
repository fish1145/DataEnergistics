package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * CPU-owned exact-key material that cannot currently fit in AE2's long-backed physical working window.
 */
public final class TrinityExactWorkingInventory {

    private static final BigInteger MAX_PHYSICAL_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int MAX_BIG_INTEGER_BYTES = 512;
    private static final String ENTRIES_TAG = "entries";
    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";

    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> overflow = new Object2ObjectLinkedOpenHashMap<>();

    /** Returns the exact amount retained outside the physical KeyCounter window. */
    public BigInteger amount(AEKey key) {
        return this.overflow.getOrDefault(key, BigInteger.ZERO);
    }

    /** Returns the complete amount owned across the physical window and exact overflow. */
    public BigInteger totalAmount(AEKey key, ListCraftingInventory physical) {
        return BigInteger.valueOf(physical.list.get(key)).add(amount(key));
    }

    /** Returns a read-only exact snapshot for planning and status aggregation. */
    public Map<AEKey, BigInteger> snapshot() {
        return Object2ObjectMaps.unmodifiable(this.overflow);
    }

    public boolean isEmpty() {
        return this.overflow.isEmpty();
    }

    public void clear() {
        this.overflow.clear();
    }

    /** Deposits one extracted physical chunk, filling the KeyCounter window before retaining exact overflow. */
    public void deposit(AEKey key, long amount, ListCraftingInventory physical) {
        deposit(key, BigInteger.valueOf(amount), physical);
    }

    /** Deposits exact CPU ownership without imposing a long-sized logical ceiling. */
    public void deposit(AEKey key, BigInteger amount, ListCraftingInventory physical) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Trinity exact working inventory deposits must be positive");
        }
        long current = physical.list.get(key);
        long room = Long.MAX_VALUE - current;
        long physicalAmount = amount.min(BigInteger.valueOf(room)).longValueExact();
        if (physicalAmount > 0L) {
            physical.insert(key, physicalAmount, Actionable.MODULATE);
        }
        BigInteger remaining = amount.subtract(BigInteger.valueOf(physicalAmount));
        if (remaining.signum() > 0) {
            this.overflow.merge(key, remaining, BigInteger::add);
        }
    }

    /** Refills every key's long window from exact CPU ownership before provider selection. */
    public boolean refillPhysicalWindows(ListCraftingInventory physical) {
        boolean movedAny = false;
        for (AEKey key : new ObjectArrayList<>(this.overflow.keySet())) {
            long current = physical.list.get(key);
            long room = Long.MAX_VALUE - current;
            if (room == 0L) {
                continue;
            }
            BigInteger stored = this.overflow.get(key);
            long moved = stored.min(BigInteger.valueOf(room)).longValueExact();
            physical.insert(key, moved, Actionable.MODULATE);
            put(key, stored.subtract(BigInteger.valueOf(moved)));
            movedAny = true;
        }
        return movedAny;
    }

    /** Removes exact CPU ownership for transactional rollback and returns it to the network in long chunks. */
    public void rollback(
                         AEKey key,
                         BigInteger amount,
                         ListCraftingInventory physical,
                         MEStorage network,
                         IActionSource source) {
        discard(key, amount, physical);
        BigInteger returning = amount;
        while (returning.signum() > 0) {
            long chunk = returning.min(MAX_PHYSICAL_AMOUNT).longValueExact();
            long inserted = network.insert(key, chunk, Actionable.MODULATE, source);
            if (inserted <= 0L || inserted > chunk) {
                this.overflow.merge(key, returning, BigInteger::add);
                throw new IllegalStateException("AE storage could not roll back Trinity exact working input");
            }
            returning = returning.subtract(BigInteger.valueOf(inserted));
        }
    }

    /** Discards virtual ownership that originated from a confirmed non-consuming source. */
    public void discard(AEKey key, BigInteger amount, ListCraftingInventory physical) {
        if (amount.signum() <= 0 || totalAmount(key, physical).compareTo(amount) < 0) {
            throw new IllegalStateException("Trinity exact working inventory lost rollback ownership");
        }
        BigInteger stored = this.overflow.getOrDefault(key, BigInteger.ZERO);
        BigInteger overflowAmount = stored.min(amount);
        put(key, stored.subtract(overflowAmount));
        BigInteger remaining = amount.subtract(overflowAmount);
        while (remaining.signum() > 0) {
            long chunk = remaining.min(MAX_PHYSICAL_AMOUNT).longValueExact();
            long extracted = physical.extract(key, chunk, Actionable.MODULATE);
            if (extracted != chunk) {
                throw new IllegalStateException("Trinity exact working inventory lost rollback ownership");
            }
            remaining = remaining.subtract(BigInteger.valueOf(extracted));
        }
    }

    /** Returns all exact overflow that the network currently accepts, retaining any rejected remainder. */
    public void returnAll(MEStorage network, IActionSource source) {
        for (AEKey key : new ObjectArrayList<>(this.overflow.keySet())) {
            BigInteger remaining = this.overflow.get(key);
            while (remaining.signum() > 0) {
                long chunk = remaining.min(MAX_PHYSICAL_AMOUNT).longValueExact();
                long inserted = network.insert(key, chunk, Actionable.MODULATE, source);
                if (inserted <= 0L || inserted > chunk) {
                    break;
                }
                remaining = remaining.subtract(BigInteger.valueOf(inserted));
            }
            put(key, remaining);
        }
    }

    /** Offers all exact overflow to a durable idle-recovery sink in long-sized physical chunks. */
    public boolean recover(BiFunction<AEKey, Long, Long> recovery) {
        for (AEKey key : new ObjectArrayList<>(this.overflow.keySet())) {
            BigInteger remaining = this.overflow.get(key);
            while (remaining.signum() > 0) {
                long offered = remaining.min(MAX_PHYSICAL_AMOUNT).longValueExact();
                long recovered = recovery.apply(key, offered);
                if (recovered < 0L || recovered > offered) {
                    throw new IllegalStateException(
                            "Trinity exact working inventory recovery violated the insertion contract for " + key);
                }
                if (recovered == 0L) {
                    break;
                }
                remaining = remaining.subtract(BigInteger.valueOf(recovered));
            }
            put(key, remaining);
        }
        return this.overflow.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        this.overflow.forEach((key, amount) -> {
            byte[] encoded = amount.toByteArray();
            if (encoded.length > MAX_BIG_INTEGER_BYTES) {
                throw new IllegalArgumentException("Trinity working inventory exceeds the persistence byte limit");
            }
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_TAG, key.toTagGeneric(registries));
            entry.putByteArray(AMOUNT_TAG, encoded);
            entries.add(entry);
        });
        root.put(ENTRIES_TAG, entries);
        return root;
    }

    public void load(CompoundTag root, HolderLookup.Provider registries) {
        this.overflow.clear();
        ListTag entries = root.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (Tag encodedEntry : entries) {
            CompoundTag entry = (CompoundTag) encodedEntry;
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(KEY_TAG));
            byte[] encoded = entry.getByteArray(AMOUNT_TAG);
            if (key == null || encoded.length == 0 || encoded.length > MAX_BIG_INTEGER_BYTES) {
                throw new IllegalArgumentException("Trinity working inventory contains damaged exact ownership");
            }
            BigInteger amount = new BigInteger(encoded);
            if (amount.signum() <= 0 || this.overflow.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Trinity working inventory requires unique positive entries");
            }
        }
    }

    private void put(AEKey key, BigInteger amount) {
        if (amount.signum() == 0) {
            this.overflow.remove(key);
        } else {
            this.overflow.put(key, amount);
        }
    }
}
