package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Moves final delivery out of a completed job's working inventory without changing physical components. */
public final class TrinityCompletionInputExtractor {

    private TrinityCompletionInputExtractor() {}

    /** Returns the exact delivery slices, or leaves inventory untouched when the whole delivery is unavailable. */
    public static @Nullable List<GenericStack> extract(TrinitySameItemPolicy policy, AEKey target, long amount,
                                                       ListCraftingInventory inventory) {
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        keys.add(target);
        if (policy.allowsSameItem(target)) {
            inventory.list.forEach(entry -> {
                if (policy.normalizeKey(entry.getKey()).equals(policy.normalizeKey(target))) {
                    keys.add(entry.getKey());
                }
            });
        }
        ObjectArrayList<GenericStack> slices = new ObjectArrayList<>();
        long remaining = amount;
        for (AEKey key : keys) {
            long extracted = inventory.extract(key, remaining, Actionable.MODULATE);
            if (extracted > 0L) {
                slices.add(new GenericStack(key, extracted));
                remaining -= extracted;
            }
            if (remaining == 0L) {
                return List.copyOf(slices);
            }
        }
        for (GenericStack slice : slices) {
            inventory.insert(slice.what(), slice.amount(), Actionable.MODULATE);
        }
        return null;
    }
}
