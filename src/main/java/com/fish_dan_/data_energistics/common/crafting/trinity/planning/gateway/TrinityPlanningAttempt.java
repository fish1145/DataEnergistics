package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

/**
 * Immutable discriminated planner outcome containing an executable Trinity plan or an explicit AE2 fallback reason.
 */
public final class TrinityPlanningAttempt {

    private final Outcome outcome;

    private TrinityPlanningAttempt(Outcome outcome) {
        this.outcome = outcome;
    }

    /**
     * @param plan validated executable plan
     * @return successful attempt
     */
    public static TrinityPlanningAttempt success(TrinityCraftingPlan plan) {
        if (plan == null || plan.simulation()) {
            throw new IllegalArgumentException("A successful Trinity planning attempt requires an executable plan");
        }
        return new TrinityPlanningAttempt(new SuccessfulOutcome(plan));
    }

    /**
     * @param diagnostic explicit fallback reason
     * @return failed attempt
     */
    public static TrinityPlanningAttempt failure(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A failed Trinity planning attempt requires a diagnostic");
        }
        return new TrinityPlanningAttempt(new FailedOutcome(diagnostic));
    }

    /**
     * @return executable Trinity plan
     * @throws IllegalStateException when this attempt is a failure
     */
    public TrinityCraftingPlan plan() {
        return this.outcome.plan();
    }

    /**
     * @return explicit fallback diagnostic
     * @throws IllegalStateException when this attempt is successful
     */
    public TrinityPlanningDiagnostic diagnostic() {
        return this.outcome.diagnostic();
    }

    /**
     * @return whether this attempt contains an executable Trinity plan
     */
    public boolean successful() {
        return this.outcome.successful();
    }

    private sealed interface Outcome permits SuccessfulOutcome, FailedOutcome {

        TrinityCraftingPlan plan();

        TrinityPlanningDiagnostic diagnostic();

        boolean successful();
    }

    private record SuccessfulOutcome(TrinityCraftingPlan plan) implements Outcome {

        @Override
        public TrinityPlanningDiagnostic diagnostic() {
            throw new IllegalStateException("A successful Trinity planning attempt has no diagnostic");
        }

        @Override
        public boolean successful() {
            return true;
        }
    }

    private record FailedOutcome(TrinityPlanningDiagnostic diagnostic) implements Outcome {

        @Override
        public TrinityCraftingPlan plan() {
            throw new IllegalStateException("A failed Trinity planning attempt has no plan");
        }

        @Override
        public boolean successful() {
            return false;
        }
    }
}
