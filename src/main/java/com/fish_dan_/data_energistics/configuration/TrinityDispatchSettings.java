package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchGovernorSettings;

import java.util.concurrent.TimeUnit;

/** Immutable dispatch-governor settings published as one configuration snapshot. */
public record TrinityDispatchSettings(
                                      int hardGridAttempts,
                                      int hardProviderAttempts,
                                      int hardCommitBudgetMs,
                                      int safeGridAttempts,
                                      int safeProviderAttempts,
                                      int safeCommitBudgetMs,
                                      int safeActorPermits,
                                      int safeRetryBackoffTicks,
                                      int warmupTicks,
                                      int metricsWindowTicks,
                                      double ewmaAlpha,
                                      int transitionWindows,
                                      int cooldownTicks,
                                      int safeHoldTicks)
        implements DataEnergisticsSettings.TrinityDispatch {

    public TrinityDispatchSettings {
        if (hardGridAttempts <= 0 || hardProviderAttempts <= 0 || hardCommitBudgetMs <= 0 ||
                safeGridAttempts <= 0 || safeProviderAttempts <= 0 || safeCommitBudgetMs <= 0 ||
                safeActorPermits <= 0 || safeRetryBackoffTicks <= 0 || warmupTicks <= 0 || metricsWindowTicks <= 0 ||
                transitionWindows <= 0 || cooldownTicks < 0 || safeHoldTicks <= 0) {
            throw new IllegalArgumentException("Trinity dispatch governor integer settings are out of range");
        }
        if (!Double.isFinite(ewmaAlpha) || ewmaAlpha <= 0.0D || ewmaAlpha > 1.0D) {
            throw new IllegalArgumentException("Trinity dispatch EWMA alpha must be in (0, 1]");
        }
        if (safeGridAttempts > hardGridAttempts || safeProviderAttempts > hardProviderAttempts ||
                safeCommitBudgetMs > hardCommitBudgetMs) {
            throw new IllegalArgumentException("Trinity dispatch SAFE budgets must not exceed hard budgets");
        }
    }

    public CraftingDispatchGovernorSettings governorSettings() {
        CraftingDispatchBudget hardBudget = new CraftingDispatchBudget(
                new CraftingDispatchLimits(
                        this.hardGridAttempts,
                        this.hardProviderAttempts,
                        TimeUnit.MILLISECONDS.toNanos(this.hardCommitBudgetMs)),
                DispatchProposalLimits.DEFAULT_PER_GRID_OUTSTANDING,
                this.hardProviderAttempts,
                DispatchProposalLimits.DEFAULT_QUEUE_CAPACITY,
                1,
                true);
        CraftingDispatchBudget safeBudget = new CraftingDispatchBudget(
                new CraftingDispatchLimits(
                        this.safeGridAttempts,
                        this.safeProviderAttempts,
                        TimeUnit.MILLISECONDS.toNanos(this.safeCommitBudgetMs)),
                this.safeActorPermits,
                this.safeProviderAttempts,
                this.safeActorPermits,
                this.safeRetryBackoffTicks,
                false);
        return CraftingDispatchGovernorSettings.defaults(
                hardBudget,
                safeBudget,
                this.warmupTicks,
                this.metricsWindowTicks,
                this.ewmaAlpha,
                this.transitionWindows,
                this.cooldownTicks,
                this.safeHoldTicks);
    }

    public static TrinityDispatchSettings defaults() {
        return new TrinityDispatchSettings(256, 16, 30, 16, 2, 2, 1, 8, 200, 20, 0.25D, 3, 60, 200);
    }
}
