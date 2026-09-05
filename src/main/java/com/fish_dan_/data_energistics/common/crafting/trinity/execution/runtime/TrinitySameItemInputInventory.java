package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.util.List;
import java.util.function.ToLongFunction;

/** Server-thread view of physical component variants available to one dispatch allocation. */
public final class TrinitySameItemInputInventory {

    private final TrinitySameItemPolicy policy;
    private final KeyCounter owned;
    private final KeyCounter network;
    private final ToLongFunction<AEKey> networkAvailability;

    public TrinitySameItemInputInventory(TrinitySameItemPolicy policy, KeyCounter owned, KeyCounter network,
                                         ToLongFunction<AEKey> networkAvailability) {
        this.policy = policy;
        this.owned = owned;
        this.network = network;
        this.networkAvailability = networkAvailability;
    }

    /**
     * Lists each authorised physical variant once. Network counts are permission-checked simulations, and merely
     * permit a subsequent borrowing transaction; they never create CPU ownership.
     */
    public List<GenericStack> candidates(AEKey plannedKey) {
        if (!this.policy.allowsSameItem(plannedKey)) {
            return List.of();
        }
        AEKey logicalKey = this.policy.normalizeKey(plannedKey);
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        this.owned.forEach(entry -> keys.add(entry.getKey()));
        this.network.forEach(entry -> keys.add(entry.getKey()));
        ObjectArrayList<GenericStack> candidates = new ObjectArrayList<>();
        for (AEKey actual : keys) {
            if (actual.equals(plannedKey) || !this.policy.normalizeKey(actual).equals(logicalKey)) {
                continue;
            }
            long ownedAmount = this.owned.get(actual);
            long networkAmount = this.network.get(actual) > 0L ? this.networkAvailability.applyAsLong(actual) : 0L;
            long available = ownedAmount + Math.min(Long.MAX_VALUE - ownedAmount, networkAmount);
            if (available > 0L) {
                candidates.add(new GenericStack(actual, available));
            }
        }
        return List.copyOf(candidates);
    }
}
