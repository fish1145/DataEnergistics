package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.selector;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Selects an accepted opportunity, continues a structural miss, or preserves a terminal diagnostic.
 * <p>
 * Stateless branch implementation that keeps the general solver lazy.
 */
public final class TrinityPlanningAttemptSelector {

    /**
     * @return stateless selector
     */
    public static TrinityPlanningAttemptSelector create() {
        return new TrinityPlanningAttemptSelector();
    }

    /**
     * @param attempt  opportunistic solver outcome
     * @param proved   adapts a fully verified opportunity into the caller's result type
     * @param fallback lazy general solver invoked only for a structural miss
     * @param <T>      opportunity value type
     * @param <R>      selected result type
     * @return proved adaptation, general fallback result or unchanged terminal failure
     */
    public <T, R> TrinityAlgorithmResult<R> select(
                                                   TrinityPlanningAttempt<T> attempt,
                                                   Function<T, R> proved,
                                                   Supplier<TrinityAlgorithmResult<R>> fallback) {
        if (attempt == null || proved == null || fallback == null) {
            throw new IllegalArgumentException("A Trinity opportunity selection requires all collaborators");
        }
        return switch (attempt.kind()) {
            case PROVED_OPTIMAL, FEASIBLE -> TrinityAlgorithmResult.success(proved.apply(attempt.value()));
            case NOT_APPLICABLE -> fallback.get();
            case TERMINAL -> TrinityAlgorithmResult.failure(attempt.diagnostic());
        };
    }
}
