package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalMetrics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;

import java.math.BigInteger;

/**
 * Immutable per-grid, per-tick facts consumed by the Governor.
 *
 * <p>
 * Durations are observations only. Correctness remains guarded by the dispatch window's physical-call and
 * server-thread budgets.
 * </p>
 */
public record CraftingDispatchMetrics(
                                      long serverTickNanos,
                                      long capacityCaptureNanos,
                                      long proposalQueueNanos,
                                      long proposalCalculationNanos,
                                      long commitNanos,
                                      int admittedProposals,
                                      int rejectedProposals,
                                      int completedProposals,
                                      int failedProposals,
                                      int acceptedProviderCalls,
                                      int rejectedProviderCalls,
                                      int staleProposals,
                                      BigInteger committedLogicalCrafts,
                                      int physicalCalls,
                                      int proposalQueueDepth,
                                      int proposalQueueCapacity,
                                      int outstandingProposals,
                                      double busiestWorkerShare) {

    public CraftingDispatchMetrics {
        if (serverTickNanos < 0L || capacityCaptureNanos < 0L || proposalQueueNanos < 0L ||
                proposalCalculationNanos < 0L || commitNanos < 0L) {
            throw new IllegalArgumentException("Crafting dispatch durations must not be negative");
        }
        if (admittedProposals < 0 || rejectedProposals < 0 || completedProposals < 0 || failedProposals < 0 ||
                failedProposals > completedProposals ||
                acceptedProviderCalls < 0 || rejectedProviderCalls < 0 || staleProposals < 0 ||
                physicalCalls < 0 || proposalQueueDepth < 0 || proposalQueueCapacity <= 0 ||
                outstandingProposals < 0) {
            throw new IllegalArgumentException("Crafting dispatch counters are out of range");
        }
        if (committedLogicalCrafts == null || committedLogicalCrafts.signum() < 0) {
            throw new IllegalArgumentException("Committed logical craft count must not be negative");
        }
        if (!Double.isFinite(busiestWorkerShare) || busiestWorkerShare < 0.0D || busiestWorkerShare > 1.0D) {
            throw new IllegalArgumentException("Busiest worker share must be in [0, 1]");
        }
    }

    /**
     * Captures one completed grid window and its independently reset proposal metrics.
     */
    public static CraftingDispatchMetrics capture(
                                                  long serverTickNanos,
                                                  CraftingDispatchWindow window,
                                                  DispatchProposalMetrics proposals,
                                                  double busiestWorkerShare) {
        int rejected = 0;
        for (CraftingDispatchStatus status : CraftingDispatchStatus.values()) {
            if (status != CraftingDispatchStatus.ACCEPTED) {
                rejected = Math.addExact(rejected, window.resultCount(status));
            }
        }
        return new CraftingDispatchMetrics(
                serverTickNanos,
                window.capacityCaptureNanos(),
                proposals.queueWaitNanos(),
                proposals.calculationNanos(),
                window.serverSubmissionNanos(),
                proposals.admitted(),
                proposals.rejected(),
                proposals.completed(),
                proposals.failed(),
                window.resultCount(CraftingDispatchStatus.ACCEPTED),
                rejected,
                proposals.stale(),
                window.committedLogicalCrafts(),
                window.attemptCount(),
                proposals.queueDepth(),
                proposals.queueCapacity(),
                proposals.outstanding(),
                busiestWorkerShare);
    }

    /**
     * @return current global proposal queue utilization
     */
    public double queueRatio() {
        return (double) this.proposalQueueDepth / (double) this.proposalQueueCapacity;
    }

    /**
     * @return stale fraction among provider outcomes, or zero when no outcome exists
     */
    public double staleRatio() {
        int outcomes = Math.addExact(
                Math.addExact(this.acceptedProviderCalls, this.rejectedProviderCalls),
                this.staleProposals);
        return outcomes == 0 ? 0.0D : (double) this.staleProposals / (double) outcomes;
    }

    /**
     * @return accepted fraction among non-stale provider outcomes, or one when no provider was attempted
     */
    public double acceptanceRatio() {
        int settled = Math.addExact(this.acceptedProviderCalls, this.rejectedProviderCalls);
        return settled == 0 ? 1.0D : (double) this.acceptedProviderCalls / (double) settled;
    }

    /**
     * @return logical craft throughput per physical call, or zero before any physical call
     */
    public double logicalCraftsPerPhysicalCall() {
        return this.physicalCalls == 0 ?
                0.0D :
                this.committedLogicalCrafts.doubleValue() / (double) this.physicalCalls;
    }
}
