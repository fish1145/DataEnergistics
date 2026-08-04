package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;

/**
 * Immutable discriminated result containing either one algorithm value or one stable planning diagnostic.
 *
 * @param <T> immutable result type
 */
public final class TrinityAlgorithmResult<T> {

    private final Outcome<T> outcome;

    private TrinityAlgorithmResult(Outcome<T> outcome) {
        this.outcome = outcome;
    }

    /**
     * @param value complete immutable value
     * @param <T>   value type
     * @return successful result
     */
    public static <T> TrinityAlgorithmResult<T> success(T value) {
        if (value == null) {
            throw new IllegalArgumentException("A successful Trinity algorithm value is required");
        }
        return new TrinityAlgorithmResult<>(new SuccessfulOutcome<>(value));
    }

    /**
     * @param diagnostic stable bounded-planning failure
     * @param <T>        expected value type
     * @return failed result
     */
    public static <T> TrinityAlgorithmResult<T> failure(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A failed Trinity algorithm diagnostic is required");
        }
        return new TrinityAlgorithmResult<>(new FailedOutcome<>(diagnostic));
    }

    /**
     * @return complete value
     * @throws IllegalStateException when this result is a failure
     */
    public T value() {
        return this.outcome.value();
    }

    /**
     * @return stable failure diagnostic
     * @throws IllegalStateException when this result is successful
     */
    public TrinityPlanningDiagnostic diagnostic() {
        return this.outcome.diagnostic();
    }

    /**
     * @return whether this result contains a complete value
     */
    public boolean successful() {
        return this.outcome.successful();
    }

    private sealed interface Outcome<T> permits SuccessfulOutcome, FailedOutcome {

        T value();

        TrinityPlanningDiagnostic diagnostic();

        boolean successful();
    }

    private record SuccessfulOutcome<T>(T value) implements Outcome<T> {

        @Override
        public TrinityPlanningDiagnostic diagnostic() {
            throw new IllegalStateException("A successful Trinity algorithm result has no diagnostic");
        }

        @Override
        public boolean successful() {
            return true;
        }
    }

    private record FailedOutcome<T>(TrinityPlanningDiagnostic diagnostic) implements Outcome<T> {

        @Override
        public T value() {
            throw new IllegalStateException("A failed Trinity algorithm result has no value");
        }

        @Override
        public boolean successful() {
            return false;
        }
    }
}
