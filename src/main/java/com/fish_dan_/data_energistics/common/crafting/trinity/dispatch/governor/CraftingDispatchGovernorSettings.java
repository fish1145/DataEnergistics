package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import java.util.concurrent.TimeUnit;

/**
 * Pure-logic Governor thresholds separated from NeoForge config storage.
 */
public record CraftingDispatchGovernorSettings(
                                               CraftingDispatchBudget hardBudget,
                                               CraftingDispatchBudget safeBudget,
                                               int warmupTicks,
                                               int metricsWindowTicks,
                                               double ewmaAlpha,
                                               int transitionWindows,
                                               int cooldownTicks,
                                               int safeHoldTicks,
                                               long overloadTickNanos,
                                               long recoveryTickNanos,
                                               long emergencyTickNanos,
                                               double overloadQueueRatio,
                                               double recoveryQueueRatio,
                                               double overloadStaleRatio,
                                               double recoveryStaleRatio,
                                               double recoveryAcceptanceRatio) {

    public CraftingDispatchGovernorSettings {
        if (hardBudget == null || safeBudget == null) {
            throw new IllegalArgumentException("Hard and SAFE dispatch budgets are required");
        }
        if (warmupTicks <= 0 || metricsWindowTicks <= 0 || transitionWindows <= 0 ||
                cooldownTicks < 0 || safeHoldTicks <= 0) {
            throw new IllegalArgumentException("Dispatch Governor tick settings are out of range");
        }
        if (invalidRatio(ewmaAlpha, false) ||
                invalidRatio(overloadQueueRatio, true) ||
                invalidRatio(recoveryQueueRatio, true) ||
                invalidRatio(overloadStaleRatio, true) ||
                invalidRatio(recoveryStaleRatio, true) ||
                invalidRatio(recoveryAcceptanceRatio, true)) {
            throw new IllegalArgumentException("Dispatch Governor ratios must be finite values in their valid range");
        }
        if (recoveryTickNanos <= 0L ||
                recoveryTickNanos >= overloadTickNanos ||
                overloadTickNanos >= emergencyTickNanos) {
            throw new IllegalArgumentException("Dispatch Governor tick thresholds are not ordered");
        }
        if (recoveryQueueRatio > overloadQueueRatio || recoveryStaleRatio > overloadStaleRatio) {
            throw new IllegalArgumentException("Dispatch Governor recovery thresholds exceed overload thresholds");
        }
        validateBudgetOrdering(hardBudget, safeBudget);
    }

    public static CraftingDispatchGovernorSettings defaults(
                                                            CraftingDispatchBudget hardBudget,
                                                            CraftingDispatchBudget safeBudget,
                                                            int warmupTicks,
                                                            int metricsWindowTicks,
                                                            double ewmaAlpha,
                                                            int transitionWindows,
                                                            int cooldownTicks,
                                                            int safeHoldTicks) {
        return new CraftingDispatchGovernorSettings(
                hardBudget,
                safeBudget,
                warmupTicks,
                metricsWindowTicks,
                ewmaAlpha,
                transitionWindows,
                cooldownTicks,
                safeHoldTicks,
                TimeUnit.MILLISECONDS.toNanos(45L),
                TimeUnit.MILLISECONDS.toNanos(35L),
                TimeUnit.MILLISECONDS.toNanos(100L),
                0.75D,
                0.25D,
                0.20D,
                0.05D,
                0.80D);
    }

    private static boolean invalidRatio(double value, boolean allowZero) {
        return !Double.isFinite(value) || value > 1.0D || (allowZero ? value < 0.0D : value <= 0.0D);
    }

    private static void validateBudgetOrdering(CraftingDispatchBudget hard, CraftingDispatchBudget safe) {
        if (!hard.asynchronousEnabled() || safe.asynchronousEnabled()) {
            throw new IllegalArgumentException("Hard dispatch must be asynchronous and SAFE dispatch must be synchronous");
        }
        if (safe.dispatchLimits().maxAttemptsPerGrid() > hard.dispatchLimits().maxAttemptsPerGrid() ||
                safe.dispatchLimits().maxAttemptsPerProvider() > hard.dispatchLimits().maxAttemptsPerProvider() ||
                safe.dispatchLimits().maxServerSubmissionNanos() > hard.dispatchLimits().maxServerSubmissionNanos() ||
                safe.dispatchLimits().maxCapacityCaptureNanos() > hard.dispatchLimits().maxCapacityCaptureNanos() ||
                safe.actorPermits() > hard.actorPermits() ||
                safe.providerQuantum() > hard.providerQuantum() ||
                safe.proposalHighWater() > hard.proposalHighWater() ||
                safe.retryBackoffTicks() < hard.retryBackoffTicks()) {
            throw new IllegalArgumentException("SAFE dispatch budget is not ordered below the hard budget");
        }
    }
}
