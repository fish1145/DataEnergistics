package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

/**
 * Describes how a planning-progress snapshot may be rendered.
 *
 * <p>
 * Only {@link #EXACT} represents a completed portion of a known finite unit of work. A solver budget is a safety
 * boundary rather than an estimate of remaining work, so {@link #COUNTER} must never be rendered as a percentage.
 * </p>
 */
public enum TrinityPlanningProgressMeasure {
    /** A finite, monotonic unit count is known and may be rendered as a percentage. */
    EXACT,
    /** Observed work and a safety cap are known, but the cap is not a completion total. */
    COUNTER,
    /** The current algorithm has no finite, truthful completion total. */
    INDETERMINATE,
    /** Queue and terminal states intentionally have no work counter. */
    NONE
}
