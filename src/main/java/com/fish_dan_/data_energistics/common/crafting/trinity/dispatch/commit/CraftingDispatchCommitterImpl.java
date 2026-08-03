package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchAccountingDelta;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;

import appeng.api.stacks.KeyCounter;

/**
 * Applies the counted-provider ownership contract without allowing a failed provider to duplicate CPU resources.
 */
final class CraftingDispatchCommitterImpl implements CraftingDispatchCommitter {

    @Override
    public CraftingDispatchResult commit(CraftingDispatchCommitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Crafting dispatch commit request must not be null");
        }
        CraftingDispatchAccountingDelta accounting = request.accounting();
        long admittedCount;
        try {
            admittedCount = request.admission().count();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} failed to report its admitted count for pattern {} on Trinity worker {} job {}",
                    request.provider(),
                    request.pattern().getDefinition(),
                    request.workerNumber(),
                    request.jobId(),
                    exception);
            return beforeOwnership(request, CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP, false);
        }
        if (admittedCount != accounting.logicalCrafts()) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} changed its admitted count from {} to {} for pattern {} on Trinity worker {} job {}",
                    request.provider(),
                    accounting.logicalCrafts(),
                    admittedCount,
                    request.pattern().getDefinition(),
                    request.workerNumber(),
                    request.jobId());
            return beforeOwnership(request, CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP, false);
        }

        KeyCounter[] unchangedPrototype = copyInputCounters(request.prototype());
        CraftingDispatchWindow.Acquisition acquisition = request.submission().tryAcquire(request.target());
        if (acquisition != CraftingDispatchWindow.Acquisition.ACQUIRED) {
            boolean settled = releaseBeforeOwnership(request);
            return new CraftingDispatchResult(
                    acquisition == CraftingDispatchWindow.Acquisition.WINDOW_EXHAUSTED ?
                            CraftingDispatchStatus.BUDGET_EXHAUSTED :
                            CraftingDispatchStatus.STALE,
                    0L,
                    false,
                    false,
                    settled);
        }

        boolean accepted = false;
        RuntimeException providerFailure = null;
        try {
            accepted = request.admission().commit(request.prototype());
        } catch (RuntimeException exception) {
            providerFailure = exception;
        }

        boolean ownershipTransferred = accepted || transferredInputOwnership(request, unchangedPrototype);
        if (!ownershipTransferred) {
            CraftingDispatchStatus status = providerFailure == null ?
                    CraftingDispatchStatus.REJECTED :
                    CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP;
            if (providerFailure != null) {
                Data_Energistics.LOGGER.error(
                        "Crafting provider {} target {} failed before taking ownership of {} crafts for pattern {} on Trinity worker {} job {}",
                        request.provider(),
                        request.target().stableIdentity(),
                        admittedCount,
                        request.pattern().getDefinition(),
                        request.workerNumber(),
                        request.jobId(),
                        providerFailure);
            }
            return beforeOwnership(request, status, true);
        }

        if (providerFailure != null) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} target {} failed after taking ownership of {} crafts for pattern {} on Trinity worker {} job {}",
                    request.provider(),
                    request.target().stableIdentity(),
                    admittedCount,
                    request.pattern().getDefinition(),
                    request.workerNumber(),
                    request.jobId(),
                    providerFailure);
        } else if (!accepted) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} target {} returned false after taking ownership of {} crafts for pattern {} on Trinity worker {} job {}",
                    request.provider(),
                    request.target().stableIdentity(),
                    admittedCount,
                    request.pattern().getDefinition(),
                    request.workerNumber(),
                    request.jobId());
        }

        request.window().recordCommittedLogicalCrafts(admittedCount);
        boolean settled = applyAfterOwnership(request);
        CraftingDispatchStatus status = accepted && providerFailure == null && settled ?
                CraftingDispatchStatus.ACCEPTED :
                CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP;
        request.window().recordResult(request.provider(), request.pattern(), request.target(), status);
        return new CraftingDispatchResult(status, admittedCount, true, true, settled);
    }

    private static CraftingDispatchResult beforeOwnership(
                                                          CraftingDispatchCommitRequest request,
                                                          CraftingDispatchStatus status,
                                                          boolean attempted) {
        boolean settled = releaseBeforeOwnership(request);
        request.window().recordResult(request.provider(), request.pattern(), request.target(), status);
        return new CraftingDispatchResult(status, 0L, attempted, false, settled);
    }

    private static boolean applyAfterOwnership(CraftingDispatchCommitRequest request) {
        try {
            request.accounting().applyAfterOwnership();
            return true;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity worker {} job {} failed to apply accounting after provider {} target {} took input ownership; no resources will be refunded",
                    request.workerNumber(),
                    request.jobId(),
                    request.provider(),
                    request.target().stableIdentity(),
                    exception);
            return false;
        }
    }

    private static boolean releaseBeforeOwnership(CraftingDispatchCommitRequest request) {
        try {
            request.accounting().releaseBeforeOwnership();
            return true;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity worker {} job {} failed to release an uncommitted dispatch for provider {} target {}",
                    request.workerNumber(),
                    request.jobId(),
                    request.provider(),
                    request.target().stableIdentity(),
                    exception);
            return false;
        }
    }

    private static boolean transferredInputOwnership(
                                                     CraftingDispatchCommitRequest request,
                                                     KeyCounter[] unchangedPrototype) {
        if (!inputCountersMatch(unchangedPrototype, request.prototype())) {
            return true;
        }
        CountedCraftingAdmission admission = request.admission();
        try {
            return admission.hasTransferredInputOwnership();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} target {} failed to report input ownership for pattern {} on Trinity worker {} job {}; treating ownership as transferred",
                    request.provider(),
                    request.target().stableIdentity(),
                    request.pattern().getDefinition(),
                    request.workerNumber(),
                    request.jobId(),
                    exception);
            return true;
        }
    }

    private static KeyCounter[] copyInputCounters(KeyCounter[] source) {
        KeyCounter[] copy = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            KeyCounter counter = new KeyCounter();
            counter.addAll(source[index]);
            copy[index] = counter;
        }
        return copy;
    }

    private static boolean inputCountersMatch(KeyCounter[] expected, KeyCounter[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (counterDiffers(expected[index], actual[index]) || counterDiffers(actual[index], expected[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean counterDiffers(KeyCounter expected, KeyCounter actual) {
        for (var entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) {
                return true;
            }
        }
        return false;
    }
}
