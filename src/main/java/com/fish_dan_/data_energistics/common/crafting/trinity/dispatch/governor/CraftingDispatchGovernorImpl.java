package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

/**
 * Phase 5 observation implementation. Adaptive state transitions are enabled only after the observation contract is
 * integrated with every physical submission path.
 */
final class CraftingDispatchGovernorImpl implements CraftingDispatchGovernor {

    private final CraftingDispatchGovernorSettings settings;
    private final WindowAccumulator window = new WindowAccumulator();
    private CraftingDispatchGovernorState state = CraftingDispatchGovernorState.OBSERVING;
    private CraftingDispatchBudget budget;
    private long observedTicks;
    private long completedWindows;
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
        this.observedTicks = Math.incrementExact(this.observedTicks);
        if (metrics.serverTickNanos() > 0L) {
            this.tickEwmaNanos = this.ewmaInitialized ?
                    this.settings.ewmaAlpha() * metrics.serverTickNanos() +
                            (1.0D - this.settings.ewmaAlpha()) * this.tickEwmaNanos :
                    metrics.serverTickNanos();
            this.ewmaInitialized = true;
        }
        this.window.add(metrics);
        if (this.window.samples() < this.settings.metricsWindowTicks()) {
            return;
        }
        this.completedWindows = Math.incrementExact(this.completedWindows);
        this.lastQueueRatio = this.window.averageQueueRatio();
        this.lastStaleRatio = this.window.staleRatio();
        this.lastAcceptanceRatio = this.window.acceptanceRatio();
        this.lastBusiestWorkerShare = this.window.busiestWorkerShare();
        this.lastProposalFailures = this.window.proposalFailures();
        this.window.reset();
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
