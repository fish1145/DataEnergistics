package com.fish_dan_.data_energistics.menu.crafting.projection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Projects a compact Trinity plan into AE2's native confirmation counters without exposing mutable pattern objects.
 */
public final class TrinityCraftingPlanSummaryProjection {

    private TrinityCraftingPlanSummaryProjection() {}

    /**
     * Maps external inputs to stored amounts and every pattern-declared output to the amount still to be crafted.
     *
     * @param plan validated executable Trinity plan
     * @return native AE2 confirmation summary
     */
    public static CraftingPlanSummary create(TrinityCraftingPlan plan) {
        LinkedHashMap<AEKey, Amounts> amounts = new LinkedHashMap<>();
        plan.initialExpectedInputs().forEach((key, amount) -> amounts
                .computeIfAbsent(key, ignored -> new Amounts())
                .addStored(amount));
        plan.plannedOutputs().forEach((key, amount) -> amounts
                .computeIfAbsent(key, ignored -> new Amounts())
                .addCrafting(amount));

        ArrayList<CraftingPlanSummaryEntry> entries = new ArrayList<>(amounts.size());
        amounts.forEach((key, value) -> entries.add(new CraftingPlanSummaryEntry(
                key,
                0L,
                value.stored,
                value.crafting)));
        Collections.sort(entries);
        return new CraftingPlanSummary(plan.bytes(), false, List.copyOf(entries));
    }

    private static final class Amounts {

        private long stored;
        private long crafting;

        private void addStored(BigInteger amount) {
            this.stored = Math.addExact(this.stored, amount.longValueExact());
        }

        private void addCrafting(BigInteger amount) {
            this.crafting = Math.addExact(this.crafting, amount.longValueExact());
        }
    }
}
