package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.selector;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Selects a proved opportunity, continues a structural miss, or preserves a terminal diagnostic.
 */
public interface TrinityPlanningAttemptSelector {

    /**
     * @return stateless selector
     */
    static TrinityPlanningAttemptSelector create() {
        return new TrinityPlanningAttemptSelectorImpl();
    }

    /**
     * @param attempt  opportunistic solver outcome
     * @param proved   adapts a fully verified opportunity into the caller's result type
     * @param fallback lazy general solver invoked only for a structural miss
     * @param <T>      opportunity value type
     * @param <R>      selected result type
     * @return proved adaptation, general fallback result or unchanged terminal failure
     */
    <T, R> TrinityAlgorithmResult<R> select(
                                            TrinityPlanningAttempt<T> attempt,
                                            Function<T, R> proved,
                                            Supplier<TrinityAlgorithmResult<R>> fallback);
}
