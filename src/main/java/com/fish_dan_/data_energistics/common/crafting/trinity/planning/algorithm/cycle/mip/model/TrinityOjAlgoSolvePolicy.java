package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;

import org.ojalgo.optimisation.ExpressionsBasedModel;

import java.util.concurrent.TimeUnit;

/** Bounds each ojAlgo invocation so cancellation cannot leave an unbounded shared-daemon task behind. */
public final class TrinityOjAlgoSolvePolicy {

    private static final long MAX_CALL_MILLIS = 5_000L;

    private TrinityOjAlgoSolvePolicy() {}

    /** Applies one request-aware abort limit and optionally stops as soon as a feasible integer witness exists. */
    public static void configure(
                                 ExpressionsBasedModel model,
                                 TrinityPlanningControl control,
                                 boolean firstFeasible) {
        long abortMillis = MAX_CALL_MILLIS;
        if (control.deadlineConfigured()) {
            long remainingNanos = control.remainingNanos();
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos) +
                    (remainingNanos % 1_000_000L == 0L ? 0L : 1L);
            abortMillis = Math.min(abortMillis, Math.max(1L, remainingMillis));
        }
        model.options.time_abort = abortMillis;
        model.options.time_suffice = firstFeasible ? 1L : abortMillis;
    }
}
