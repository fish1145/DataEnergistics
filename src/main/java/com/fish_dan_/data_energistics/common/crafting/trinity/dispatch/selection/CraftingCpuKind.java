package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

/**
 * Identifies the explicitly supported ownership boundary for one crafting CPU selection snapshot.
 */
public enum CraftingCpuKind {
    /**
     * Native AE2 crafting CPU cluster.
     */
    NATIVE,
    /**
     * Trinity Data Core coordinator backed by independent workers.
     */
    TRINITY,
    /**
     * External CPU with explicit compile-time selection and submission adapters.
     */
    SUPPORTED_EXTERNAL
}
