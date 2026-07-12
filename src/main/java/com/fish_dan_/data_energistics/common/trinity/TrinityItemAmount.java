package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;

import java.util.ArrayList;
import java.util.List;

/** Immutable item identity and positive long amount used by Trinity output and refund state. */
public record TrinityItemAmount(AEItemKey key, long amount) {

    public TrinityItemAmount {
        if (key == null) {
            throw new IllegalArgumentException("A Trinity item amount requires an item key");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("A Trinity item amount must be positive: " + amount);
        }
    }

    /**
     * Captures one non-empty stack without retaining its mutable count-bearing instance.
     *
     * @param stack source item and components
     * @return counted immutable item entry
     */
    public static TrinityItemAmount of(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("A Trinity item amount requires a non-empty stack");
        }
        return new TrinityItemAmount(AEItemKey.of(stack), stack.getCount());
    }

    /**
     * Multiplies one unit stack by a positive logical count without overflowing a long entry.
     *
     * @param stack      one logical unit output or input
     * @param multiplier positive number of identical logical units
     * @return ordered positive entries whose mathematical total equals stack count times multiplier
     */
    public static List<TrinityItemAmount> multiply(ItemStack stack, long multiplier) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("A multiplied Trinity item amount requires a non-empty stack");
        }
        if (multiplier <= 0L) {
            throw new IllegalArgumentException("A Trinity item amount multiplier must be positive: " + multiplier);
        }

        long unitAmount = stack.getCount();
        long multipliersPerEntry = Long.MAX_VALUE / unitAmount;
        AEItemKey key = AEItemKey.of(stack);
        ArrayList<TrinityItemAmount> result = new ArrayList<>();
        long remainingMultiplier = multiplier;
        while (remainingMultiplier > 0L) {
            long segmentMultiplier = Math.min(remainingMultiplier, multipliersPerEntry);
            result.add(new TrinityItemAmount(key, Math.multiplyExact(unitAmount, segmentMultiplier)));
            remainingMultiplier -= segmentMultiplier;
        }
        return List.copyOf(result);
    }

    /**
     * Reuses this immutable item identity with a different positive amount.
     *
     * @param amount replacement amount
     * @return replacement entry
     */
    public TrinityItemAmount withAmount(long amount) {
        return new TrinityItemAmount(this.key, amount);
    }
}
