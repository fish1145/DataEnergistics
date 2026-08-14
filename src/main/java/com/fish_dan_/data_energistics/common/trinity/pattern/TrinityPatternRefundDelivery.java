package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Two-phase delivery contract for an already-collected Trinity installed-pattern refund.
 *
 * <p>
 * {@link #prepare(List)} validates the delivery context before any core is mutated.
 * {@link #deliver(List)} runs only after every mounted core cleared its exact captured slots. Implementations must
 * deliver every remainder through AE storage, the player inventory, and the final world-drop fallback in that order.
 * </p>
 */
public interface TrinityPatternRefundDelivery {

    /**
     * Captures and validates the delivery context without inserting, dropping, or mutating any offered pattern.
     *
     * @param patterns immutable slot-ordered copies of every installed encoded pattern in the aggregate
     * @return true when {@link #deliver(List)} may be invoked for this exact aggregate
     */
    boolean prepare(List<ItemStack> patterns);

    /**
     * Delivers installed patterns after the core transaction committed.
     *
     * <p>
     * Implementations must first use AE storage, then the player's inventory, and finally create world drops for every
     * rejected remainder. A rejected world drop is returned as the exact ordered suffix that remains undelivered.
     * </p>
     *
     * @param patterns immutable slot-ordered copies of every installed encoded pattern in the aggregate
     * @return ordered remaining suffix; empty only when every offered pattern was delivered
     */
    List<ItemStack> deliver(List<ItemStack> patterns);
}
