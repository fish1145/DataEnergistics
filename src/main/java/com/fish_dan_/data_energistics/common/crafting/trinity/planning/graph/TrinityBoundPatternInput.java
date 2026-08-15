package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * One exact alternative selected for an ordered pattern input slot.
 *
 * @param slotIndex        ordered input-slot index
 * @param alternativeIndex ordered alternative index inside the slot
 * @param template         selected key and template amount
 * @param multiplier       number of templates consumed by one logical firing
 * @param remainingKey     key returned once for each consumed template, if any
 */
public record TrinityBoundPatternInput(
                                       int slotIndex,
                                       int alternativeIndex,
                                       GenericStack template,
                                       long multiplier,
                                       @Nullable AEKey remainingKey) {

    /**
     * Rejects incomplete bindings before exact quantities are derived.
     */
    public TrinityBoundPatternInput {
        if (slotIndex < 0 || alternativeIndex < 0 || template == null || template.what() == null ||
                template.amount() <= 0L || multiplier <= 0L) {
            throw new IllegalArgumentException("A Trinity bound input requires a legal slot, template and multiplier");
        }
    }

    /**
     * @return exact amount consumed by one logical firing
     */
    public BigInteger consumedAmount() {
        return BigInteger.valueOf(this.template.amount()).multiply(BigInteger.valueOf(this.multiplier));
    }

    /**
     * A remaining key represents one returned unit per selected template, not the selected stack's storage amount.
     *
     * @return exact returned amount, or zero when the input has no remainder
     */
    public BigInteger remainingAmount() {
        return this.remainingKey == null ? BigInteger.ZERO : BigInteger.valueOf(this.multiplier);
    }
}
