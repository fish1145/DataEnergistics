package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

import java.util.concurrent.TimeUnit;

/**
 * Deterministic Phase 5 Governor. It observes immutable metrics and publishes one budget for the next grid tick.
 */
final class CraftingDispatchGovernorImpl implements CraftingDispatchGovernor {

    private static final long COMMIT_RECOVERY_STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(1L);

    private final CraftingDispatchGovernorSettings settings;
    private final WindowAccumulator window = new WindowAccumulator();
    private CraftingDispatchGovernorState state = CraftingDispatchGovernorState.OBSERVING;
    private CraftingDispatchBudget budget;
    private long observedTicks;
    private long completedWindows;
    private int stateTicks;
    private int cooldownRemainingTicks;
    private int overloadWindows;
    private int recoveryWindows;
    private int emergencyTicks;
    private boolean ewmaInitialized;
    private double tickEwmaNanos;
    private double lastQueueRatio;
    private double lastStaleRatio;
    private double lastAcceptanceRatio = 1.0D;
    private double lastBusiestWorkerShare;
    private int lastProposalFailures;

    CraftingDispatchGovernorImpl(CraftingDispatchGovernorSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Crafting dispatch Governor settings are required");
        }
        this.settings = settings;
        this.budget = settings.hardBudget();
    }

    @Override
    public CraftingDispatchBudget budget() {
        return this.budget;
    }

    @Override
    public void observe(CraftingDispatchMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("Crafting dispatch metrics are required");
        }
        try {
            observeValidated(metrics);
        } catch (RuntimeException exception) {
            recordUnexpectedFailure("metrics observation", exception);
        }
    }

    @Override
    public void recordUnexpectedFailure(String source, RuntimeException failure) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Crafting dispatch failure source is required");
        }
        if (failure == null) {
            throw new IllegalArgumentException("Crafting dispatch failure is required");
        }
        Data_Energistics.LOGGER.error(
                "Trinity dispatch Governor entered SAFE mode after an unexpected {} failure",
                source,
                failure);
        enterSafe();
    }

    @Override
    public CraftingDispatchGovernorSnapshot snapshot() {
        return new CraftingDispatchGovernorSnapshot(
                this.state,
                this.budget,
                this.observedTicks,
                this.completedWindows,
                this.tickEwmaNanos,
                this.lastQueueRatio,
                this.lastStaleRatio,
                this.lastAcceptanceRatio,
                this.lastBusiestWorkerShare,
                this.lastProposalFailures);
    }

    private void observeValidated(CraftingDispatchMetrics metrics) {
        this.observedTicks = Math.incrementExact(this.observedTicks);
        this.stateTicks = Math.incrementExact(this.stateTicks);
        if (this.state == CraftingDispatchGovernorState.ADAPTIVE && this.cooldownRemainingTicks > 0) {
            this.cooldownRemainingTicks--;
        }
        updateTickEwma(metrics.serverTickNanos());

        if (this.state == CraftingDispatchGovernorState.SAFE) {
            captureWindow(metrics);
            if (this.stateTicks >= this.settings.safeHoldTicks()) {
                enterObserving();
            }
            return;
        }

        if (metrics.failedProposals() > 0) {
            this.lastProposalFailures = metrics.failedProposals();
            Data_Energistics.LOGGER.error(
                    "Trinity dispatch Governor entered SAFE mode after {} proposal or Actor failures",
                    metrics.failedProposals());
            enterSafe();
            return;
        }

        if (metrics.serverTickNanos() > this.settings.emergencyTickNanos()) {
            this.emergencyTicks = Math.incrementExact(this.emergencyTicks);
        } else {
            this.emergencyTicks = 0;
        }
        if (this.emergencyTicks >= this.settings.transitionWindows()) {
            Data_Energistics.LOGGER.warn(
                    "Trinity dispatch Governor entered SAFE mode after {} consecutive server ticks above {} ns",
                    this.emergencyTicks,
                    this.settings.emergencyTickNanos());
            enterSafe();
            return;
        }

        if (!captureWindow(metrics)) {
            return;
        }
        if (this.state == CraftingDispatchGovernorState.OBSERVING) {
            if (this.stateTicks >= this.settings.warmupTicks()) {
                this.state = CraftingDispatchGovernorState.ADAPTIVE;
                this.stateTicks = 0;
                resetDecisionCounters();
                Data_Energistics.LOGGER.info("Trinity dispatch Governor completed observation and entered ADAPTIVE mode");
            }
            return;
        }
        applyAdaptiveDecision();
    }

    private void updateTickEwma(long serverTickNanos) {
        if (serverTickNanos <= 0L) {
            return;
        }
        this.tickEwmaNanos = this.ewmaInitialized ?
                this.settings.ewmaAlpha() * serverTickNanos +
                        (1.0D - this.settings.ewmaAlpha()) * this.tickEwmaNanos :
                serverTickNanos;
        this.ewmaInitialized = true;
    }

    private boolean captureWindow(CraftingDispatchMetrics metrics) {
        this.window.add(metrics);
        if (this.window.samples() < this.settings.metricsWindowTicks()) {
            return false;
        }
        this.completedWindows = Math.incrementExact(this.completedWindows);
        this.lastQueueRatio = this.window.averageQueueRatio();
        this.lastStaleRatio = this.window.staleRatio();
        this.lastAcceptanceRatio = this.window.acceptanceRatio();
        this.lastBusiestWorkerShare = this.window.busiestWorkerShare();
        this.lastProposalFailures = this.window.proposalFailures();
        this.window.reset();
        return true;
    }

    private void applyAdaptiveDecision() {
        boolean overloaded = this.tickEwmaNanos >= this.settings.overloadTickNanos() ||
                this.lastQueueRatio >= this.settings.overloadQueueRatio() ||
                this.lastStaleRatio >= this.settings.overloadStaleRatio();
        boolean recoverable = this.tickEwmaNanos <= this.settings.recoveryTickNanos() &&
                this.lastQueueRatio <= this.settings.recoveryQueueRatio() &&
                this.lastStaleRatio <= this.settings.recoveryStaleRatio() &&
                this.lastAcceptanceRatio >= this.settings.recoveryAcceptanceRatio();
        if (overloaded) {
            this.overloadWindows = Math.incrementExact(this.overloadWindows);
            this.recoveryWindows = 0;
        } else if (recoverable) {
            this.recoveryWindows = Math.incrementExact(this.recoveryWindows);
            this.overloadWindows = 0;
        } else {
            resetDecisionCounters();
        }
        if (this.cooldownRemainingTicks > 0) {
            return;
        }
        if (this.overloadWindows >= this.settings.transitionWindows()) {
            CraftingDispatchBudget adjusted = decreaseBudget(this.budget);
            publishAdjustment(adjusted, "reduced");
        } else if (this.recoveryWindows >= this.settings.transitionWindows()) {
            CraftingDispatchBudget adjusted = increaseBudget(this.budget);
            publishAdjustment(adjusted, "recovered");
        }
    }

    private void publishAdjustment(CraftingDispatchBudget adjusted, String action) {
        resetDecisionCounters();
        if (adjusted.equals(this.budget)) {
            return;
        }
        this.budget = adjusted;
        this.cooldownRemainingTicks = this.settings.cooldownTicks();
        Data_Energistics.LOGGER.info("Trinity dispatch Governor {} its ADAPTIVE physical budget to {}", action, adjusted);
    }

    private CraftingDispatchBudget decreaseBudget(CraftingDispatchBudget current) {
        CraftingDispatchBudget safe = this.settings.safeBudget();
        CraftingDispatchLimits limits = current.dispatchLimits();
        int providerAttempts = decrease(limits.maxAttemptsPerProvider(), safe.dispatchLimits().maxAttemptsPerProvider());
        return new CraftingDispatchBudget(
                new CraftingDispatchLimits(
                        decrease(limits.maxAttemptsPerGrid(), safe.dispatchLimits().maxAttemptsPerGrid()),
                        providerAttempts,
                        decrease(limits.maxServerSubmissionNanos(), safe.dispatchLimits().maxServerSubmissionNanos()),
                        limits.maxCapacityCaptureNanos()),
                decrease(current.actorPermits(), safe.actorPermits()),
                Math.min(providerAttempts, decrease(current.providerQuantum(), safe.providerQuantum())),
                decrease(current.proposalHighWater(), safe.proposalHighWater()),
                increase(current.retryBackoffTicks(), safe.retryBackoffTicks(), 1),
                true);
    }

    private CraftingDispatchBudget increaseBudget(CraftingDispatchBudget current) {
        CraftingDispatchBudget hard = this.settings.hardBudget();
        CraftingDispatchLimits limits = current.dispatchLimits();
        int providerAttempts = increase(
                limits.maxAttemptsPerProvider(),
                hard.dispatchLimits().maxAttemptsPerProvider(),
                1);
        return new CraftingDispatchBudget(
                new CraftingDispatchLimits(
                        increase(limits.maxAttemptsPerGrid(), hard.dispatchLimits().maxAttemptsPerGrid(), 8),
                        providerAttempts,
                        increase(
                                limits.maxServerSubmissionNanos(),
                                hard.dispatchLimits().maxServerSubmissionNanos()),
                        limits.maxCapacityCaptureNanos()),
                increase(current.actorPermits(), hard.actorPermits(), 1),
                Math.min(providerAttempts, increase(current.providerQuantum(), hard.providerQuantum(), 1)),
                increase(current.proposalHighWater(), hard.proposalHighWater(), 8),
                decreaseToward(current.retryBackoffTicks(), hard.retryBackoffTicks()),
                true);
    }

    private void enterSafe() {
        this.state = CraftingDispatchGovernorState.SAFE;
        this.budget = this.settings.safeBudget();
        this.stateTicks = 0;
        this.cooldownRemainingTicks = 0;
        this.emergencyTicks = 0;
        this.window.reset();
        resetDecisionCounters();
    }

    private void enterObserving() {
        this.state = CraftingDispatchGovernorState.OBSERVING;
        this.budget = this.settings.hardBudget();
        this.stateTicks = 0;
        this.cooldownRemainingTicks = 0;
        this.emergencyTicks = 0;
        this.ewmaInitialized = false;
        this.tickEwmaNanos = 0.0D;
        this.window.reset();
        resetDecisionCounters();
        Data_Energistics.LOGGER.info("Trinity dispatch Governor completed SAFE hold and re-entered OBSERVING mode");
    }

    private void resetDecisionCounters() {
        this.overloadWindows = 0;
        this.recoveryWindows = 0;
    }

    private static int decrease(int current, int lowerBound) {
        int reduction = current / 4 + (current % 4 == 0 ? 0 : 1);
        return Math.max(lowerBound, current - reduction);
    }

    private static long decrease(long current, long lowerBound) {
        long reduction = current / 4L + (current % 4L == 0L ? 0L : 1L);
        return Math.max(lowerBound, current - reduction);
    }

    private static int increase(int current, int upperBound, int step) {
        return current >= upperBound ? upperBound : (int) Math.min(upperBound, (long) current + step);
    }

    private static long increase(long current, long upperBound) {
        if (current >= upperBound || current > upperBound - COMMIT_RECOVERY_STEP_NANOS) {
            return upperBound;
        }
        return current + COMMIT_RECOVERY_STEP_NANOS;
    }

    private static int decreaseToward(int current, int lowerBound) {
        return current <= lowerBound ? lowerBound : current - 1;
    }

    /**
     * Mutable facts retained only until one fixed metrics window is complete.
     */
    private static final class WindowAccumulator {

        private int samples;
        private double queueRatioSum;
        private int accepted;
        private int rejected;
        private int stale;
        private int proposalFailures;
        private double busiestWorkerShare;

        private void add(CraftingDispatchMetrics metrics) {
            this.samples = Math.incrementExact(this.samples);
            this.queueRatioSum += metrics.queueRatio();
            this.accepted = Math.addExact(this.accepted, metrics.acceptedProviderCalls());
            this.rejected = Math.addExact(this.rejected, metrics.rejectedProviderCalls());
            this.stale = Math.addExact(this.stale, metrics.staleProposals());
            this.proposalFailures = Math.addExact(this.proposalFailures, metrics.failedProposals());
            this.busiestWorkerShare = Math.max(this.busiestWorkerShare, metrics.busiestWorkerShare());
        }

        private int samples() {
            return this.samples;
        }

        private double averageQueueRatio() {
            return this.samples == 0 ? 0.0D : this.queueRatioSum / (double) this.samples;
        }

        private double staleRatio() {
            int outcomes = Math.addExact(Math.addExact(this.accepted, this.rejected), this.stale);
            return outcomes == 0 ? 0.0D : (double) this.stale / (double) outcomes;
        }

        private double acceptanceRatio() {
            int settled = Math.addExact(this.accepted, this.rejected);
            return settled == 0 ? 1.0D : (double) this.accepted / (double) settled;
        }

        private double busiestWorkerShare() {
            return this.busiestWorkerShare;
        }

        private int proposalFailures() {
            return this.proposalFailures;
        }

        private void reset() {
            this.samples = 0;
            this.queueRatioSum = 0.0D;
            this.accepted = 0;
            this.rejected = 0;
            this.stale = 0;
            this.proposalFailures = 0;
            this.busiestWorkerShare = 0.0D;
        }
    }
}
