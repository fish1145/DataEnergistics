package com.fish_dan_.data_energistics.common.acceleration;

/**
 * Computes the largest constant-state progress segment before a machine reaches its next work boundary.
 */
public final class BatchTickProgression {

    private BatchTickProgression() {}

    /**
     * Advances progress to either the end of the available tick budget or the next completion boundary.
     *
     * @param progress       current progress in {@code [0, duration)}
     * @param duration       positive ticks required for one completion
     * @param availableTicks positive ticks available in the current batch
     * @return elapsed ticks, next progress, and whether a completion boundary was reached
     */
    public static Segment advanceToBoundary(int progress, int duration, int availableTicks) {
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (progress < 0 || progress >= duration) {
            throw new IllegalArgumentException("progress must be within [0, duration)");
        }
        if (availableTicks <= 0) {
            throw new IllegalArgumentException("availableTicks must be positive");
        }

        int ticksToBoundary = duration - progress;
        if (availableTicks < ticksToBoundary) {
            return new Segment(availableTicks, progress + availableTicks, false);
        }
        return new Segment(ticksToBoundary, 0, true);
    }

    /**
     * One constant-state progression segment.
     */
    public record Segment(int elapsedTicks, int progress, boolean reachedBoundary) {}
}
