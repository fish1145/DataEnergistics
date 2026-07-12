package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

/**
 * Opt-in provider contract for transferring one homogeneous group of Trinity routed crafting inputs.
 *
 * <p>
 * AE2's {@link ICraftingProvider#pushPattern} always represents one logical craft. This extension is deliberately
 * limited to Trinity providers whose persistent queue natively stores a logical count alongside one exact input
 * prototype.
 * </p>
 */
public interface TrinityBatchCraftingProvider extends ICraftingProvider {

    /**
     * Atomically accepts multiple identical logical crafts represented by one exact input prototype.
     *
     * <p>
     * The counters contain one logical craft's actual substituted inputs. The CPU separately consumes the remaining
     * identical copies before this call. Returning {@code false} must leave every counter unchanged; returning
     * {@code true} transfers ownership of the prototype and all {@code count} logical copies to the provider.
     * </p>
     *
     * @param patternDetails exact routed Trinity pattern selected by the crafting plan
     * @param inputHolder    one exact per-craft input prototype for every pattern input slot
     * @param count          positive number of logical crafts represented by the holder
     * @return whether the complete counted group was accepted
     */
    boolean pushPatternBatch(IPatternDetails patternDetails, KeyCounter[] inputHolder, long count);
}
