package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory;

import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Server-thread-owned exact balances for expected outputs and accepted virtual completions. Only individual AE2
 * transfers use long amounts; accumulating, comparing and persisting the owned balance never projects it to long.
 */
public final class TrinityExactKeyInventory {

    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> amounts = new Object2ObjectLinkedOpenHashMap<>();
    private final Consumer<AEKey> listener;

    public TrinityExactKeyInventory(Consumer<AEKey> listener) {
        this.listener = listener;
    }

    public BigInteger amount(AEKey key) {
        return this.amounts.getOrDefault(key, BigInteger.ZERO);
    }

    /** Returns a stable read-only copy whose entries cannot mutate the live ledger. */
    public Map<AEKey, BigInteger> snapshot() {
        return Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(this.amounts));
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    /** Adds an already accepted physical chunk to exact ownership. */
    public void insert(AEKey key, long amount, Actionable mode) {
        insert(key, BigInteger.valueOf(amount), mode);
    }

    public void insert(AEKey key, BigInteger amount, Actionable mode) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Exact crafting inventory additions must not be negative");
        }
        if (mode == Actionable.MODULATE && amount.signum() > 0) {
            this.amounts.merge(key, amount, BigInteger::add);
            this.listener.accept(key);
        }
    }

    /** Takes at most one physical transfer without losing the exact remainder. */
    public long extract(AEKey key, long maximum, Actionable mode) {
        return extract(key, BigInteger.valueOf(maximum), mode).longValueExact();
    }

    public BigInteger extract(AEKey key, BigInteger maximum, Actionable mode) {
        if (maximum.signum() < 0) {
            throw new IllegalArgumentException("Exact crafting inventory extraction must not be negative");
        }
        BigInteger available = amount(key);
        BigInteger extracted = available.min(maximum);
        if (mode == Actionable.MODULATE && extracted.signum() > 0) {
            BigInteger remaining = available.subtract(extracted);
            if (remaining.signum() == 0) {
                this.amounts.remove(key);
            } else {
                this.amounts.put(key, remaining);
            }
            this.listener.accept(key);
        }
        return extracted;
    }

    public void clear() {
        var keys = new ObjectArrayList<>(this.amounts.keySet());
        this.amounts.clear();
        keys.forEach(this.listener);
    }

    public ListTag writeToNBT(HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        this.amounts.forEach((key, amount) -> {
            CompoundTag entry = key.toTagGeneric(registries);
            entry.putByteArray("#", TrinityBigIntegerEncoding.encode(amount, "exact output balance"));
            encoded.add(entry);
        });
        return encoded;
    }

    /** Reads both the former AE2 long counter entries and current exact entries at the persistence boundary. */
    public void readFromNBT(ListTag encoded, HolderLookup.Provider registries) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> restored = new Object2ObjectLinkedOpenHashMap<>();
        for (Tag element : encoded) {
            if (!(element instanceof CompoundTag entry)) {
                throw new IllegalArgumentException("Exact output balances require compound entries");
            }
            AEKey key = AEKey.fromTagGeneric(registries, entry);
            BigInteger amount;
            if (entry.contains("#", Tag.TAG_LONG)) {
                amount = BigInteger.valueOf(entry.getLong("#"));
            } else if (entry.contains("#", Tag.TAG_BYTE_ARRAY)) {
                amount = TrinityBigIntegerEncoding.decode(entry.getByteArray("#"), "exact output balance");
            } else {
                throw new IllegalArgumentException("Exact output balance is missing its amount");
            }
            if (key == null || amount.signum() < 0 || restored.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Exact output balances require unique keys and non-negative amounts");
            }
        }
        clear();
        restored.forEach((key, amount) -> insert(key, amount, Actionable.MODULATE));
    }
}
