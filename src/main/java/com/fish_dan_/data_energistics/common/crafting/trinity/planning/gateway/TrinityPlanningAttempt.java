package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import java.util.Optional;

/**
 * Immutable planner outcome containing an executable plan, terminal Trinity simulation, or explicit AE2 fallback.
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
     * @param simulation exact non-executable Trinity diagnostic that makes further AE2 calculation unnecessary
     * @return failed attempt with a terminal confirmation-page result
     */
    public static TrinityPlanningAttempt authoritativeSimulation(TrinityDiagnosedCraftingPlan simulation) {
        if (simulation == null || !simulation.simulation() || simulation.ae2FallbackEstimate()) {
            throw new IllegalArgumentException("A terminal Trinity diagnostic requires a standalone simulation");
        }
        return new TrinityPlanningAttempt(new AuthoritativeSimulationOutcome(simulation));
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

    /**
     * @return constant-size diagnostic result when Trinity fully resolved a non-executable request
     */
    public Optional<TrinityDiagnosedCraftingPlan> authoritativeSimulation() {
        return this.outcome instanceof AuthoritativeSimulationOutcome(TrinityDiagnosedCraftingPlan simulation) ?
                Optional.of(simulation) :
                Optional.empty();
    }

    private sealed interface Outcome permits SuccessfulOutcome, AuthoritativeSimulationOutcome, FailedOutcome {

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

    private record AuthoritativeSimulationOutcome(TrinityDiagnosedCraftingPlan simulation) implements Outcome {

        @Override
        public TrinityCraftingPlan plan() {
            throw new IllegalStateException("An authoritative Trinity simulation has no executable plan");
        }

        @Override
        public TrinityPlanningDiagnostic diagnostic() {
            return this.simulation.diagnostic();
        }

        @Override
        public boolean successful() {
            return false;
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
