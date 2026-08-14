package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

/**
 * Immutable process-local proposal facts drained by one grid Governor.
 *
 * @param admitted               proposals accepted by the scheduler
 * @param rejected               proposals rejected before background calculation
 * @param completed              completed pure proposal calculations
 * @param failed                 calculations that completed with an isolated failure
 * @param stale                  proposals discarded after a server-thread generation or route revalidation failed
 * @param queueWaitNanos         accumulated virtual-thread scheduling wait
 * @param calculationNanos       accumulated pure proposal calculation time
 * @param globalOutstanding      current accepted tickets across all grids
 * @param globalOutstandingLimit fixed global ticket limit
 * @param gridOutstanding        current outstanding tickets for this grid
 */
public record DispatchProposalMetrics(
                                      int admitted,
                                      int rejected,
                                      int completed,
                                      int failed,
                                      int stale,
                                      long queueWaitNanos,
                                      long calculationNanos,
                                      int globalOutstanding,
                                      int globalOutstandingLimit,
                                      int gridOutstanding) {

    public DispatchProposalMetrics {
        if (admitted < 0 || rejected < 0 || completed < 0 || failed < 0 || stale < 0 ||
                queueWaitNanos < 0L || calculationNanos < 0L ||
                globalOutstanding < 0 || globalOutstandingLimit <= 0 || gridOutstanding < 0) {
            throw new IllegalArgumentException("Dispatch proposal metrics are out of range");
        }
        if (failed > completed || globalOutstanding > globalOutstandingLimit) {
            throw new IllegalArgumentException("Dispatch proposal metrics are internally inconsistent");
        }
    }
}
