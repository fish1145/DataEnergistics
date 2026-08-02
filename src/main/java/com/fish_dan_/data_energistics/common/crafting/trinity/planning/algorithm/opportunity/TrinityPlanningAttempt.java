package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;

/**
 * Distinguishes an opportunistic proof from a structural miss and a terminal planning failure.
 *
 * @param <T> immutable proven value type
 */
public final class TrinityPlanningAttempt<T> {

    private final Outcome<T> outcome;

    private TrinityPlanningAttempt(Outcome<T> outcome) {
        this.outcome = outcome;
    }

    /**
     * @param value completely verified optimal value
     * @param <T>   value type
     * @return proved opportunity result
     */
    public static <T> TrinityPlanningAttempt<T> provedOptimal(T value) {
        if (value == null) {
            throw new IllegalArgumentException("A proved Trinity planning value is required");
        }
        return new TrinityPlanningAttempt<>(new ProvedOptimal<>(value));
    }

    /**
     * @param diagnostic stable explanation of the unsupported opportunity boundary
     * @param <T>        expected value type
     * @return non-terminal miss that must continue through the general planner
     */
    public static <T> TrinityPlanningAttempt<T> notApplicable(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A Trinity not-applicable diagnostic is required");
        }
        return new TrinityPlanningAttempt<>(new NotApplicable<>(diagnostic));
    }

    /**
     * @param diagnostic cancellation, shared-budget exhaustion or other terminal planning failure
     * @param <T>        expected value type
     * @return terminal result that must not start another solver
     */
    public static <T> TrinityPlanningAttempt<T> terminal(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A terminal Trinity planning diagnostic is required");
        }
        return new TrinityPlanningAttempt<>(new Terminal<>(diagnostic));
    }

    /**
     * @return discriminant used by the cycle-plan selector
     */
    public Kind kind() {
        return this.outcome.kind();
    }

    /**
     * @return completely verified optimal value
     * @throws IllegalStateException when this attempt did not prove a value
     */
    public T value() {
        return this.outcome.value();
    }

    /**
     * @return stable non-success diagnostic
     * @throws IllegalStateException when this attempt proved a value
     */
    public TrinityPlanningDiagnostic diagnostic() {
        return this.outcome.diagnostic();
    }

    /**
     * Closed set of opportunity outcomes prevents a local fast-path miss from becoming a terminal rejection.
     */
    public enum Kind {
        PROVED_OPTIMAL,
        NOT_APPLICABLE,
        TERMINAL
    }

    private sealed interface Outcome<T> permits ProvedOptimal, NotApplicable, Terminal {

        Kind kind();

        T value();

        TrinityPlanningDiagnostic diagnostic();
    }

    private record ProvedOptimal<T>(T value) implements Outcome<T> {

        @Override
        public Kind kind() {
            return Kind.PROVED_OPTIMAL;
        }

        @Override
        public TrinityPlanningDiagnostic diagnostic() {
            throw new IllegalStateException("A proved Trinity planning attempt has no diagnostic");
        }
    }

    private record NotApplicable<T>(TrinityPlanningDiagnostic diagnostic) implements Outcome<T> {

        @Override
        public Kind kind() {
            return Kind.NOT_APPLICABLE;
        }

        @Override
        public T value() {
            throw new IllegalStateException("A not-applicable Trinity planning attempt has no value");
        }
    }

    private record Terminal<T>(TrinityPlanningDiagnostic diagnostic) implements Outcome<T> {

        @Override
        public Kind kind() {
            return Kind.TERMINAL;
        }

        @Override
        public T value() {
            throw new IllegalStateException("A terminal Trinity planning attempt has no value");
        }
    }
}
