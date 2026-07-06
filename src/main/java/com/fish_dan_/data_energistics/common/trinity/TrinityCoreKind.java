package com.fish_dan_.data_energistics.common.trinity;

/**
 * Identifies the static capability a trinity core block contributes when a future structure reader scans it.
 */
public enum TrinityCoreKind {
    /** Storage type cores increase the number of distinct stored keys the trinity structure may support. */
    STORAGE_TYPES,
    /** Parallel CPU cores increase the number of crafting jobs the trinity structure may process in parallel. */
    PARALLEL_CPU,
    /** Pattern processing cores provide rows of pattern slots for future crafting dispatch. */
    PATTERN_PROCESSING
}
