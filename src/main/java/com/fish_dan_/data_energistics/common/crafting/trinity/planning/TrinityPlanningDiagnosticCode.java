package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

/**
 * Stable machine-readable reasons for Trinity planning fallback, rejection or runtime suspension.
 */
public enum TrinityPlanningDiagnosticCode {

    NO_PRODUCTIVE_CYCLE,
    SCC_KEY_LIMIT,
    VARIANT_LIMIT,
    MIP_TIMEOUT,
    MIP_NO_INTEGER_SOLUTION,
    MIP_INEXACT_RESULT,
    NO_EXECUTABLE_ORDER,
    ORDER_SEARCH_LIMIT,
    INSUFFICIENT_INPUT,
    ARITHMETIC_OVERFLOW,
    STALE_GRAPH,
    NO_ELIGIBLE_TRINITY_CPU,
    PLANNER_QUEUE_FULL,
    UNSUPPORTED_PATTERN,
    CALCULATION_CANCELLED,
    RUNTIME_DEADLOCK,
    INTERNAL_ERROR
}
