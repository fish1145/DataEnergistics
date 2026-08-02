package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchAccountingDelta;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

import java.util.UUID;

/**
 * Server-thread-only inputs for one final provider ownership transition.
 *
 * <p>
 * The request deliberately retains the live admission and prototype only for the duration of the synchronous call.
 * It is neither an asynchronous proposal nor a persistable value.
 * </p>
 *
 * @param workerNumber Trinity worker number used for structured failure context
 * @param jobId        current crafting job identity
 * @param provider     live provider revalidated immediately before this request
 * @param pattern      exact live pattern being submitted
 * @param target       provider-local route fixed during preparation
 * @param admission    one-shot admission matching the accounting delta
 * @param prototype    mutable one-craft holder passed to the provider exactly once
 * @param window       shared grid dispatch window
 * @param submission   active provider submission scope
 * @param accounting   prevalidated resource and job-accounting transition
 */
public record CraftingDispatchCommitRequest(
                                            int workerNumber,
                                            UUID jobId,
                                            ICraftingProvider provider,
                                            IPatternDetails pattern,
                                            CraftingDispatchTarget target,
                                            CountedCraftingAdmission admission,
                                            KeyCounter[] prototype,
                                            CraftingDispatchWindow window,
                                            CraftingDispatchWindow.SubmissionScope submission,
                                            CraftingDispatchAccountingDelta accounting) {

    public CraftingDispatchCommitRequest {
        if (workerNumber < 0) {
            throw new IllegalArgumentException("Crafting dispatch worker number must not be negative");
        }
        if (jobId == null || provider == null || pattern == null || target == null || admission == null ||
                prototype == null || window == null || submission == null || accounting == null) {
            throw new IllegalArgumentException("Crafting dispatch commit request is incomplete");
        }
        for (KeyCounter input : prototype) {
            if (input == null) {
                throw new IllegalArgumentException("Crafting dispatch prototype must not contain null counters");
            }
        }
    }
}
