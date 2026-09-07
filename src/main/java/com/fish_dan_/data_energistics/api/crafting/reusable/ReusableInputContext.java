package com.fish_dan_.data_energistics.api.crafting.reusable;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import lombok.Builder;

import java.util.List;
import java.util.Optional;

/**
 * Server-thread-only rule query for one exact input slot, before any ownership transfer.
 * Adapters must not retain this context or its live world, pattern and action-source references.
 * The amount is the number of tool units required by this slot; rules describe one such unit.
 * Machine-owned units must never be extracted from, or refunded to, the CPU as supplied inputs.
 *
 * @param pattern      original published pattern, not an extraction wrapper
 * @param actualInput  exact tool identity and positive slot quantity
 * @param exactInputs  immutable complete input snapshot in original slot order
 * @param inputSlot    index in the original pattern's input array
 * @param ownership    physical owner before submission
 * @param actionSource actor used for machine access checks
 * @param level        live server level, valid only during the callback
 * @param recipeId     authoritative recipe identity when available
 * @param machineMode  authoritative machine operating mode when available
 * @param target       exact execution target selected for this query
 */
@Builder
public record ReusableInputContext(IPatternDetails pattern, GenericStack actualInput, List<GenericStack> exactInputs, int inputSlot,
                                   Ownership ownership, IActionSource actionSource, ServerLevel level,
                                   Optional<ResourceLocation> recipeId, Optional<ResourceLocation> machineMode,
                                   CountedCraftingTarget target) {

    public ReusableInputContext {
        exactInputs = List.copyOf(exactInputs);
        if (!(actualInput.what() instanceof AEItemKey) || actualInput.amount() <= 0L) {
            throw new IllegalArgumentException("Reusable input must be an exact item with positive quantity");
        }
        if (inputSlot < 0 || inputSlot >= pattern.getInputs().length) {
            throw new IllegalArgumentException("Reusable input slot is outside the original pattern");
        }
        if (exactInputs.size() != pattern.getInputs().length || !actualInput.equals(exactInputs.get(inputSlot))) {
            throw new IllegalArgumentException("Reusable input must match its complete slot snapshot");
        }
        for (GenericStack input : exactInputs) {
            if (input.amount() <= 0L) {
                throw new IllegalArgumentException("Exact input snapshot quantities must be positive");
            }
        }
    }

    /** Physical ownership, independent of whether the recipe consumes durability. */
    public enum Ownership {
        CPU_SUPPLIED,
        MACHINE_OWNED
    }
}
