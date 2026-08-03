package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime;

/**
 * Server-thread scheduling disposition returned after one worker operation.
 *
 * @param kind    event or retry disposition
 * @param retryAt absolute server tick for {@link Kind#RETRY_AT}, otherwise {@code -1}
 */
public record TrinityWorkerSchedulingHint(Kind kind, long retryAt) {

    private static final TrinityWorkerSchedulingHint READY = new TrinityWorkerSchedulingHint(Kind.READY, -1L);
    private static final TrinityWorkerSchedulingHint WAITING_EVENT = new TrinityWorkerSchedulingHint(
            Kind.WAITING_EVENT,
            -1L);
    private static final TrinityWorkerSchedulingHint IDLE = new TrinityWorkerSchedulingHint(Kind.IDLE, -1L);

    public TrinityWorkerSchedulingHint {
        if (kind == null) {
            throw new IllegalArgumentException("Trinity worker scheduling kind must not be null");
        }
        if ((kind == Kind.RETRY_AT) != (retryAt >= 0L)) {
            throw new IllegalArgumentException("Trinity worker retry scheduling requires exactly one non-negative tick");
        }
    }

    /** @return worker has immediate server-thread work */
    public static TrinityWorkerSchedulingHint ready() {
        return READY;
    }

    /** @return worker waits for output, proposal, resume or another explicit event */
    public static TrinityWorkerSchedulingHint waitingEvent() {
        return WAITING_EVENT;
    }

    /** @return worker owns no executable or cleanup work */
    public static TrinityWorkerSchedulingHint idle() {
        return IDLE;
    }

    /**
     * @param retryAt absolute server tick
     * @return worker scheduled for a deterministic retry
     */
    public static TrinityWorkerSchedulingHint retryAt(long retryAt) {
        return new TrinityWorkerSchedulingHint(Kind.RETRY_AT, retryAt);
    }

    /** Worker queue destination. */
    public enum Kind {
        READY,
        WAITING_EVENT,
        RETRY_AT,
        IDLE
    }
}
