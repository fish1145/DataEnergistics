package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory.TrinityExactWorkingInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;

/** Moves final delivery out of a completed job's working inventory without changing physical components. */
public final class TrinityCompletionInputExtractor {

    private TrinityCompletionInputExtractor() {}

    /** Returns the exact delivery slices, or leaves inventory untouched when the whole delivery is unavailable. */
    public static @Nullable Map<AEKey, BigInteger> extract(TrinitySameItemPolicy policy, AEKey target, BigInteger amount,
                                                           ListCraftingInventory inventory, TrinityExactWorkingInventory exactInventory) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("A completion extraction amount must not be negative");
        }
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        keys.add(target);
        if (policy.allowsSameItem(target)) {
            inventory.list.forEach(entry -> {
                if (policy.normalizeKey(entry.getKey()).equals(policy.normalizeKey(target))) {
                    keys.add(entry.getKey());
                }
            });
            for (AEKey key : exactInventory.snapshot().keySet()) {
                if (policy.normalizeKey(key).equals(policy.normalizeKey(target))) {
                    keys.add(key);
                }
            }
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> slices = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger remaining = amount;
        for (AEKey key : keys) {
            BigInteger extracted = exactInventory.totalAmount(key, inventory).min(remaining);
            if (extracted.signum() > 0) {
                slices.put(key, extracted);
                remaining = remaining.subtract(extracted);
            }
            if (remaining.signum() == 0) {
                break;
            }
        }
        if (remaining.signum() > 0) {
            return null;
        }
        slices.forEach((key, quantity) -> exactInventory.discard(key, quantity, inventory));
        return Collections.unmodifiableMap(slices);
    }
}
