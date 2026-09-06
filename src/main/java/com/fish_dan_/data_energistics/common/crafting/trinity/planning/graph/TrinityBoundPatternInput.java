package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * One exact alternative selected for an ordered pattern input slot.
 *
 * @param slotIndex        ordered input-slot index
 * @param alternativeIndex ordered alternative index inside the slot
 * @param template         selected key and template amount
 * @param multiplier       number of templates consumed by one logical firing
 * @param remainingKey     key returned once for each consumed template, if any
 * @param reusableRule     authoritative unit-tool rule frozen on the server, or null for the legacy input contract
 * @param byproducts       exact per-tool-unit transition byproducts, excluding the retained tool and recipe output
 */
public record TrinityBoundPatternInput(
                                       int slotIndex,
                                       int alternativeIndex,
                                       GenericStack template,
                                       long multiplier,
                                       @Nullable AEKey remainingKey,
                                       @Nullable ReusableInputRule reusableRule,
                                       List<GenericStack> byproducts) {

    /**
     * Rejects incomplete bindings before exact quantities are derived.
     */
    public TrinityBoundPatternInput {
        if (slotIndex < 0 || alternativeIndex < 0 || template == null || template.what() == null ||
                template.amount() <= 0L || multiplier <= 0L) {
            throw new IllegalArgumentException("A Trinity bound input requires a legal slot, template and multiplier");
        }
        byproducts = List.copyOf(byproducts);
        if (reusableRule == null && !byproducts.isEmpty()) {
            throw new IllegalArgumentException("Legacy input bindings cannot declare reusable-tool byproducts");
        }
        if (reusableRule != null) {
            if (!reusableRule.initialKey().equals(template.what())) {
                throw new IllegalArgumentException("Reusable binding rule must describe the exact template");
            }
            ReusableInputRule.Result expected = reusableRule.advance(reusableRule.initialKey(), 1L);
            if (!Objects.equals(remainingKey, expected.successor()) || !byproducts.equals(expected.byproducts())) {
                throw new IllegalArgumentException("Reusable binding must retain the exact one-use transition");
            }
        }
    }

    /** Captures the unchanged legacy per-template remainder contract. */
    public TrinityBoundPatternInput(int slotIndex, int alternativeIndex, GenericStack template,
                                    long multiplier, @Nullable AEKey remainingKey) {
        this(slotIndex, alternativeIndex, template, multiplier, remainingKey, null, List.of());
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
        return this.remainingKey == null ? BigInteger.ZERO :
                this.reusableRule == null ? BigInteger.valueOf(this.multiplier) : consumedAmount();
    }
}
