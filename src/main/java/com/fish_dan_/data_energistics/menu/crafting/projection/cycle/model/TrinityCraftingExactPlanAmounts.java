package com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/** Exact confirmation-table counters retained alongside AE2's long-only compatibility summary. */
public record TrinityCraftingExactPlanAmounts(
                                              AEKey key,
                                              BigInteger missing,
                                              BigInteger stored,
                                              BigInteger crafting) {

    public TrinityCraftingExactPlanAmounts {
        if (missing.signum() < 0 || stored.signum() < 0 || crafting.signum() < 0 ||
                missing.signum() + stored.signum() + crafting.signum() == 0) {
            throw new IllegalArgumentException("A Trinity exact plan row requires non-negative non-empty counters");
        }
    }
}
