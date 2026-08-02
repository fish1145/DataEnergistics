package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.selector;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TrinityPlanningAttemptSelectorTest {

    @Test
    void selectsOnlyProofFallsBackOnlyForStructuralMissAndPreservesTerminalFailure() {
        TrinityPlanningAttemptSelector selector = TrinityPlanningAttemptSelector.create();
        TrinityPlanningDiagnostic notApplicable = TrinityPlanningDiagnostic.of(
                TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN,
                "The opportunity cannot prove this graph");
        TrinityPlanningDiagnostic terminal = TrinityPlanningDiagnostic.of(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                "The shared planning deadline was exhausted");
        AtomicInteger fallbackCalls = new AtomicInteger();

        TrinityAlgorithmResult<Integer> proved = selector.select(
                TrinityPlanningAttempt.provedOptimal(7),
                value -> value * 2,
                () -> {
                    fallbackCalls.incrementAndGet();
                    return TrinityAlgorithmResult.success(99);
                });
        TrinityAlgorithmResult<Integer> continued = selector.select(
                TrinityPlanningAttempt.<Integer>notApplicable(notApplicable),
                value -> value,
                () -> {
                    fallbackCalls.incrementAndGet();
                    return TrinityAlgorithmResult.success(11);
                });
        TrinityAlgorithmResult<Integer> stopped = selector.select(
                TrinityPlanningAttempt.<Integer>terminal(terminal),
                value -> value,
                () -> {
                    fallbackCalls.incrementAndGet();
                    return TrinityAlgorithmResult.success(13);
                });

        assertEquals(14, proved.value());
        assertEquals(11, continued.value());
        assertEquals(terminal, stopped.diagnostic());
        assertEquals(1, fallbackCalls.get());
    }
}
