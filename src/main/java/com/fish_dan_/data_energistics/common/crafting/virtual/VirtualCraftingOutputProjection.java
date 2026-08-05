package com.fish_dan_.data_energistics.common.crafting.virtual;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletion;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletionMode;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable logical and dispatch-time views of one pattern's declared outputs.
 */
public final class VirtualCraftingOutputProjection {

    private final List<GenericStack> logicalOutputs;
    private final List<VirtualCraftingCompletion> virtualCompletionsPerCraft;

    VirtualCraftingOutputProjection(List<GenericStack> logicalOutputs,
                                    List<VirtualCraftingCompletion> virtualCompletionsPerCraft) {
        this.logicalOutputs = List.copyOf(logicalOutputs);
        this.virtualCompletionsPerCraft = List.copyOf(virtualCompletionsPerCraft);
    }

    /**
     * Returns the outputs used by crafting publication and planning.
     *
     * @return immutable logical output list
     */
    public List<GenericStack> logicalOutputs() {
        return this.logicalOutputs;
    }

    /**
     * Reports whether at least one declared output is completed virtually.
     *
     * @return whether this projection has a dispatch-time result
     */
    public boolean hasVirtualOutputs() {
        return !this.virtualCompletionsPerCraft.isEmpty();
    }

    /**
     * Computes the exact immutable completion tokens for an accepted logical batch.
     *
     * @param acceptedLogicalCrafts positive logical craft count accepted by the provider
     * @return aggregated completion tokens scaled by the accepted count
     * @throws ArithmeticException when a scaled result cannot be represented by AE2's positive {@code long} amount
     */
    public List<VirtualCraftingCompletion> virtualCompletions(long acceptedLogicalCrafts) {
        if (acceptedLogicalCrafts <= 0L) {
            throw new IllegalArgumentException("Accepted virtual crafting count must be positive");
        }
        LinkedHashMap<CompletionIdentity, BigInteger> scaled = new LinkedHashMap<>();
        BigInteger count = BigInteger.valueOf(acceptedLogicalCrafts);
        for (VirtualCraftingCompletion completion : this.virtualCompletionsPerCraft) {
            GenericStack output = completion.stack();
            scaled.merge(
                    new CompletionIdentity(output.what(), completion.mode()),
                    BigInteger.valueOf(output.amount()).multiply(count),
                    BigInteger::add);
        }
        ArrayList<VirtualCraftingCompletion> completions = new ArrayList<>(scaled.size());
        scaled.forEach((identity, amount) -> {
            long exactAmount = amount.longValueExact();
            if (exactAmount <= 0L) {
                throw new ArithmeticException("Virtual crafting completion amount must remain positive");
            }
            completions.add(new VirtualCraftingCompletion(
                    new GenericStack(identity.key(), exactAmount),
                    identity.mode()));
        });
        return Collections.unmodifiableList(completions);
    }

    static List<GenericStack> immutableStacks(Map<AEKey, BigInteger> amounts) {
        ArrayList<GenericStack> stacks = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> {
            long exactAmount = amount.longValueExact();
            if (exactAmount <= 0L) {
                throw new ArithmeticException("Virtual crafting output amount must remain positive");
            }
            stacks.add(new GenericStack(key, exactAmount));
        });
        return Collections.unmodifiableList(stacks);
    }

    private record CompletionIdentity(AEKey key, VirtualCraftingCompletionMode mode) {}
}
