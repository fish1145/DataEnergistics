package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitContext;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitHook;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.entrypoint.provider.ResolvedProviderBinding;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderUploadWorkstations.PreparedWorkstationChange;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Coordinates one provider-leaf inventory mutation with its prepared machine-side changes. */
final class PatternProviderUploadCommit {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private PatternProviderUploadCommit() {}

    static Result attempt(PatternContainer container,
                          ItemStack encodedPattern,
                          ObjectList<PreparedWorkstationChange> changes) {
        InternalInventory patternInventory;
        try {
            patternInventory = container.getTerminalPatternInventory();
            if (patternInventory.size() <= 0) {
                return Result.noCommit(encodedPattern);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to resolve the encoded-pattern inventory for {} before workstation state changed",
                    container,
                    exception);
            return Result.failed(encodedPattern);
        }

        if (!applyWorkstationChanges(changes, container)) {
            return Result.failed(encodedPattern);
        }

        MutationResult mutation;
        try {
            mutation = mutatePatternInventory(container, patternInventory, encodedPattern);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed before the encoded-pattern inventory for {} could be mutated; rolling back workstation changes",
                    container,
                    exception);
            rollbackWorkstationChanges(changes, container);
            return Result.failed(encodedPattern);
        }

        if (mutation.indeterminate()) {
            completeIndeterminateWorkstationChanges(changes, container);
            refreshPatternProviderAfterMutation(container, 0, true);
            return Result.indeterminate(encodedPattern);
        }
        if (mutation.committedCount() <= 0) {
            rollbackWorkstationChanges(changes, container);
            return Result.noCommit(encodedPattern);
        }

        completeWorkstationChanges(changes, mutation.committedCount(), container);
        notifyCommittedPatternUpload(container, encodedPattern, mutation.committedCount());
        return Result.committed(mutation.remainder(), mutation.committedCount());
    }

    private static MutationResult mutatePatternInventory(PatternContainer container,
                                                         InternalInventory patternInventory,
                                                         ItemStack encodedPattern) {
        long matchingCountBefore = countMatchingEncodedPatterns(patternInventory, encodedPattern);
        @Nullable
        ItemStack reportedRemainder = null;
        @Nullable
        RuntimeException insertionFailure = null;
        try {
            reportedRemainder = patternInventory.addItems(encodedPattern.copy(), false);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to insert encoded pattern into {}; checking the target inventory for committed patterns",
                    container, exception);
            insertionFailure = exception;
        }

        try {
            int committedCount = countCommittedPatternDelta(
                    matchingCountBefore, patternInventory, encodedPattern);
            if (committedCount <= 0) {
                return MutationResult.noCommit(encodedPattern);
            }

            if (reportedRemainder != null) {
                int reportedCommittedCount = countReportedPatternCommit(encodedPattern, reportedRemainder);
                if (reportedCommittedCount != committedCount) {
                    LOGGER.warn("Pattern provider {} reported {} of {} patterns committed, but its inventory changed by {}; " +
                            "using the inventory delta as the committed count",
                            container, reportedCommittedCount, encodedPattern.getCount(), committedCount);
                }
            }
            return MutationResult.committed(
                    createRemainderAfterCommit(encodedPattern, committedCount),
                    committedCount);
        } catch (RuntimeException verificationFailure) {
            if (insertionFailure != null) {
                verificationFailure.addSuppressed(insertionFailure);
            }
            LOGGER.error(
                    "Could not determine the real encoded-pattern inventory delta for {} after mutation was attempted; preserving applied workstation state",
                    container,
                    verificationFailure);
            return MutationResult.indeterminate(encodedPattern);
        }
    }

    private static boolean applyWorkstationChanges(ObjectList<PreparedWorkstationChange> changes,
                                                   PatternContainer provider) {
        for (int index = 0; index < changes.size(); index++) {
            PreparedWorkstationChange prepared = changes.get(index);
            try {
                prepared.change().apply();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to apply workstation upload registration {} at {} {} side {} before uploading to {}; rolling back {} attempted changes",
                        prepared.registrationId(),
                        prepared.dimensionId(),
                        prepared.workstationPosition(),
                        prepared.inputSide(),
                        provider,
                        index + 1,
                        exception);
                rollbackWorkstationChanges(changes, index + 1, provider);
                return false;
            }
        }
        return true;
    }

    private static void completeWorkstationChanges(ObjectList<PreparedWorkstationChange> changes,
                                                   int committedCount,
                                                   PatternContainer provider) {
        for (PreparedWorkstationChange prepared : changes) {
            try {
                prepared.change().complete(committedCount);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to complete workstation upload registration {} at {} {} side {} after committing {} encoded patterns to {}",
                        prepared.registrationId(),
                        prepared.dimensionId(),
                        prepared.workstationPosition(),
                        prepared.inputSide(),
                        committedCount,
                        provider,
                        exception);
            }
        }
    }

    private static void completeIndeterminateWorkstationChanges(ObjectList<PreparedWorkstationChange> changes,
                                                                PatternContainer provider) {
        for (PreparedWorkstationChange prepared : changes) {
            try {
                prepared.change().completeIndeterminate();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to complete indeterminate workstation upload registration {} at {} {} side {} after {} reached an unknown inventory state",
                        prepared.registrationId(),
                        prepared.dimensionId(),
                        prepared.workstationPosition(),
                        prepared.inputSide(),
                        provider,
                        exception);
            }
        }
    }

    private static void rollbackWorkstationChanges(ObjectList<PreparedWorkstationChange> changes,
                                                   PatternContainer provider) {
        rollbackWorkstationChanges(changes, changes.size(), provider);
    }

    private static void rollbackWorkstationChanges(ObjectList<PreparedWorkstationChange> changes,
                                                   int appliedCount,
                                                   PatternContainer provider) {
        for (int index = appliedCount - 1; index >= 0; index--) {
            PreparedWorkstationChange prepared = changes.get(index);
            try {
                prepared.change().rollback();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to roll back workstation upload registration {} at {} {} side {} after {} accepted no encoded pattern",
                        prepared.registrationId(),
                        prepared.dimensionId(),
                        prepared.workstationPosition(),
                        prepared.inputSide(),
                        provider,
                        exception);
            }
        }
    }

    private static long countMatchingEncodedPatterns(InternalInventory inventory, ItemStack encodedPattern) {
        long matchingCount = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, encodedPattern)) {
                matchingCount += stack.getCount();
            }
        }
        return matchingCount;
    }

    private static int countReportedPatternCommit(ItemStack encodedPattern, ItemStack reportedRemainder) {
        int boundedRemainderCount = Math.min(encodedPattern.getCount(), reportedRemainder.getCount());
        return encodedPattern.getCount() - boundedRemainderCount;
    }

    private static int countCommittedPatternDelta(long matchingCountBefore, InternalInventory inventory,
                                                  ItemStack encodedPattern) {
        long matchingCountAfter = countMatchingEncodedPatterns(inventory, encodedPattern);
        long positiveDelta = Math.max(0, matchingCountAfter - matchingCountBefore);
        return (int) Math.min(encodedPattern.getCount(), positiveDelta);
    }

    private static ItemStack createRemainderAfterCommit(ItemStack encodedPattern, int committedCount) {
        if (committedCount >= encodedPattern.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = encodedPattern.copy();
        remainder.shrink(committedCount);
        return remainder;
    }

    private static void notifyCommittedPatternUpload(PatternContainer container,
                                                     ItemStack encodedPattern,
                                                     int committedCount) {
        refreshPatternProviderAfterMutation(container, committedCount, false);

        Optional<ResolvedProviderBinding> resolved;
        try {
            resolved = PatternProviderRuntimeBindings.resolve(container);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to resolve a post-commit provider plugin after committing {} encoded patterns to {}",
                    committedCount, container, exception);
            return;
        }
        if (resolved.isEmpty()) {
            return;
        }
        ResolvedProviderBinding binding = resolved.get();
        PatternProviderPostCommitHook hook = binding.registration().postCommitHook();
        if (hook == null) {
            return;
        }
        try {
            hook.afterCommit(new PatternProviderPostCommitContext(
                    container,
                    binding.identity(),
                    encodedPattern,
                    committedCount));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Pattern provider post-commit hook '{}' failed after committing {} encoded patterns to identity {}",
                    binding.registration().metadata().registrationId(),
                    committedCount,
                    binding.identity(),
                    exception);
        }
    }

    private static void refreshPatternProviderAfterMutation(PatternContainer container,
                                                            int committedCount,
                                                            boolean indeterminate) {
        if (!(container instanceof PatternProviderLogicHost providerHost)) {
            return;
        }
        try {
            providerHost.getLogic().updatePatterns();
        } catch (RuntimeException exception) {
            if (indeterminate) {
                LOGGER.error("Failed to update patterns after an indeterminate encoded-pattern mutation in {}",
                        container, exception);
            } else {
                LOGGER.error("Failed to update patterns after committing {} encoded patterns to {}",
                        committedCount, container, exception);
            }
        }
        try {
            providerHost.saveChanges();
        } catch (RuntimeException exception) {
            if (indeterminate) {
                LOGGER.error("Failed to save {} after an indeterminate encoded-pattern mutation",
                        container, exception);
            } else {
                LOGGER.error("Failed to save pattern provider after committing {} encoded patterns to {}",
                        committedCount, container, exception);
            }
        }
    }

    record Result(ItemStack remainder,
                  int committedCount,
                  boolean indeterminate,
                  boolean failed) {

        private static Result noCommit(ItemStack remainder) {
            return new Result(remainder, 0, false, false);
        }

        private static Result committed(ItemStack remainder, int committedCount) {
            return new Result(remainder, committedCount, false, false);
        }

        private static Result indeterminate(ItemStack remainder) {
            return new Result(remainder, 0, true, false);
        }

        private static Result failed(ItemStack remainder) {
            return new Result(remainder, 0, false, true);
        }
    }

    private record MutationResult(ItemStack remainder, int committedCount, boolean indeterminate) {

        private static MutationResult noCommit(ItemStack remainder) {
            return new MutationResult(remainder, 0, false);
        }

        private static MutationResult committed(ItemStack remainder, int committedCount) {
            return new MutationResult(remainder, committedCount, false);
        }

        private static MutationResult indeterminate(ItemStack remainder) {
            return new MutationResult(remainder, 0, true);
        }
    }
}
