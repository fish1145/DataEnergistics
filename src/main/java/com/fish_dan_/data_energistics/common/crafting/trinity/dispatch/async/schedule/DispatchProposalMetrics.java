package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

/**
 * Immutable process-local proposal facts drained by one grid Governor.
 *
 * @param admitted         proposals accepted by the bounded executor
 * @param rejected         proposals rejected before background calculation
 * @param completed        completed pure proposal calculations
 * @param failed           calculations that completed with an isolated failure
 * @param stale            proposals discarded after a server-thread generation or route revalidation failed
 * @param queueWaitNanos   accumulated executor queue wait
 * @param calculationNanos accumulated pure proposal calculation time
 * @param queueDepth       current global executor queue depth
 * @param queueCapacity    fixed global executor queue capacity
 * @param outstanding      current outstanding tickets for this grid
 */
public record DispatchProposalMetrics(
        int admitted,
        int rejected,
        int completed,
        int failed,
        int stale,
        long queueWaitNanos,
        long calculationNanos,
        int queueDepth,
        int queueCapacity,
        int outstanding) {

    public DispatchProposalMetrics {
        if (admitted < 0 || rejected < 0 || completed < 0 || failed < 0 || stale < 0 ||
                queueWaitNanos < 0L || calculationNanos < 0L ||
                queueDepth < 0 || queueCapacity <= 0 || outstanding < 0) {
            throw new IllegalArgumentException("Dispatch proposal metrics are out of range");
        }
        if (failed > completed || queueDepth > queueCapacity) {
            throw new IllegalArgumentException("Dispatch proposal metrics are internally inconsistent");
        }
    }
}
