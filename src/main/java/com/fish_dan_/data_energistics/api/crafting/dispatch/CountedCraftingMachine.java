package com.fish_dan_.data_energistics.api.crafting.dispatch;

import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.Direction;

/**
 * Opt-in bulk input delivery for an AE2 crafting-machine capability.
 *
 * <p>
 * A machine also registers its read-only capacity through {@link CraftingMachineCapacityRegistration}. Capacity alone
 * does not authorize scaling an ordinary {@link ICraftingMachine} submission. Calls run on the server thread; pattern
 * identity and per-slot inputs retain their original meaning, while {@code count} describes identical logical copies.
 * Implementations must not retain or mutate the caller's prototype. Existing single-craft entry points remain
 * available.
 * </p>
 */
public interface CountedCraftingMachine extends ICraftingMachine {

    /**
     * Rechecks live input capacity and accepts a complete batch, or rejects it without changing machine state.
     *
     * <p>
     * Invoke {@code transferOwnership} exactly once immediately before the first machine mutation, after all rejecting
     * checks. After that boundary, failure or an exception must not cause the caller to resend the inputs.
     * Implementations
     * must preserve the actual received assets; they must not keep a second consumable copy of the prototype.
     * </p>
     *
     * @param patternDetails    original registered pattern, not a multiplied or synthetic pattern
     * @param prototype         exact per-craft input counters in pattern input order; amounts must be non-negative
     * @param count             positive number of logical crafts to receive in full
     * @param inputSide         receiving machine face
     * @param transferOwnership non-throwing callback marking the irreversible transfer of all batch inputs
     * @return whether the entire batch was accepted; false before ownership transfer leaves inputs and machine
     *         unchanged
     * @throws IllegalArgumentException if the logical count or prototype amounts are invalid
     */
    boolean pushPatternBatch(IPatternDetails patternDetails, KeyCounter[] prototype, long count, Direction inputSide,
                             Runnable transferOwnership);
}
