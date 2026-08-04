package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.selector;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Stateless branch implementation that keeps the general solver lazy.
 */
final class TrinityPlanningAttemptSelectorImpl implements TrinityPlanningAttemptSelector {

    @Override
    public <T, R> TrinityAlgorithmResult<R> select(
                                                   TrinityPlanningAttempt<T> attempt,
                                                   Function<T, R> proved,
                                                   Supplier<TrinityAlgorithmResult<R>> fallback) {
        if (attempt == null || proved == null || fallback == null) {
            throw new IllegalArgumentException("A Trinity opportunity selection requires all collaborators");
        }
        return switch (attempt.kind()) {
            case PROVED_OPTIMAL -> TrinityAlgorithmResult.success(proved.apply(attempt.value()));
            case NOT_APPLICABLE -> fallback.get();
            case TERMINAL -> TrinityAlgorithmResult.failure(attempt.diagnostic());
        };
    }
}
