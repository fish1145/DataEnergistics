package com.fish_dan_.data_energistics.integration.ae2cs.capacity;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** Read-only joint slot accounting for AECS input inventories, on the server thread. */
@NullMarked
public final class AecsInputCapacity {

    private AecsInputCapacity() {}

    /** Captures item insertion limits without changing live stacks or invoking inventory notifications. */
    public static long capture(InternalInventory inventory, KeyCounter[] prototype, long requested) {
        List<Input> inputs = inputs(prototype);
        long[][] room = new long[inputs.size()][inventory.size()];
        boolean[] empty = new boolean[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            empty[slot] = inventory.getStackInSlot(slot).isEmpty();
            for (int index = 0; index < inputs.size(); index++) {
                if (inputs.get(index).key() instanceof AEItemKey item) {
                    var stack = item.toStack(item.getMaxStackSize());
                    room[index][slot] = stack.getCount() - inventory.insertItem(slot, stack, true).getCount();
                }
            }
        }
        return capacity(inputs, room, empty, requested);
    }

    /** Captures generic input slots; items and fluids compete for the same physical slot. */
    public static long capture(GenericInternalInventory inventory, KeyCounter[] prototype, long requested) {
        List<Input> inputs = inputs(prototype);
        long[][] room = new long[inputs.size()][inventory.size()];
        boolean[] empty = new boolean[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            empty[slot] = inventory.getAmount(slot) == 0L;
            for (int index = 0; index < inputs.size(); index++) {
                AEKey key = inputs.get(index).key();
                room[index][slot] = inventory.insert(slot, key, inventory.getMaxAmount(key), Actionable.SIMULATE);
            }
        }
        return capacity(inputs, room, empty, requested);
    }

    private static List<Input> inputs(KeyCounter[] prototype) {
        KeyCounter totals = new KeyCounter();
        for (KeyCounter counter : prototype) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("Negative AECS input amount");
                }
                if (amount > 0L) {
                    totals.set(entry.getKey(), Math.addExact(totals.get(entry.getKey()), amount));
                }
            }
        }
        List<Input> result = new ArrayList<>();
        for (var entry : totals) {
            result.add(new Input(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    private static long capacity(List<Input> inputs, long[][] room, boolean[] empty, long requested) {
        if (requested <= 0L) {
            throw new IllegalArgumentException("Requested AECS crafts must be positive");
        }
        if (inputs.isEmpty()) {
            return 1L;
        }
        long upper = requested;
        for (int index = 0; index < inputs.size(); index++) {
            long total = 0L;
            for (long amount : room[index]) {
                if (amount < 0L) {
                    throw new IllegalStateException("Negative simulated AECS slot capacity");
                }
                total = Math.addExact(total, amount);
            }
            upper = Math.min(upper, total / inputs.get(index).amount());
        }
        long lower = 0L;
        while (lower < upper) {
            long middle = lower + (upper - lower) / 2L + 1L;
            if (fits(inputs, room, empty, middle)) {
                lower = middle;
            } else {
                upper = middle - 1L;
            }
        }
        return lower;
    }

    private static boolean fits(List<Input> inputs, long[][] room, boolean[] empty, long crafts) {
        boolean[] claimed = new boolean[empty.length];
        for (int index = 0; index < inputs.size(); index++) {
            long remaining = Math.multiplyExact(inputs.get(index).amount(), crafts);
            // Occupied compatible stacks have exclusive space for their existing key.
            for (int slot = 0; slot < empty.length; slot++) {
                if (!empty[slot]) {
                    remaining -= Math.min(remaining, room[index][slot]);
                }
            }
            // AECS input slots have uniform insertion rules. Claim each empty slot for only one key.
            for (int slot = 0; slot < empty.length && remaining > 0L; slot++) {
                if (empty[slot] && !claimed[slot] && room[index][slot] > 0L) {
                    claimed[slot] = true;
                    remaining -= Math.min(remaining, room[index][slot]);
                }
            }
            if (remaining > 0L) {
                return false;
            }
        }
        return true;
    }

    private record Input(AEKey key, long amount) {}
}
