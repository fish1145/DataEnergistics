package com.fish_dan_.data_energistics.menu.crafting.projection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection.TrinityAe2AmountProjection;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Projects Trinity plans and standalone diagnostics into AE2's native confirmation counters.
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
        return summarize(plan.bytes(), false, amounts);
    }

    /**
     * Projects exact shortages and zero-counter unresolved rows without using AE2's concrete-plan summary path.
     *
     * @param plan standalone exact Trinity shortage diagnostic
     * @return native AE2 confirmation summary
     */
    public static CraftingPlanSummary createDiagnostic(TrinityDiagnosedCraftingPlan plan) {
        if (plan.ae2FallbackEstimate()) {
            throw new IllegalArgumentException("An AE2 fallback diagnostic must use its native delegate summary");
        }
        LinkedHashMap<AEKey, Amounts> amounts = new LinkedHashMap<>();
        TrinityPlanningDiagnostic diagnostic = plan.diagnostic();
        if (diagnostic.inputShortage().isPresent()) {
            TrinityPlanningDiagnostic.InputShortage shortage = diagnostic.inputShortage().orElseThrow();
            Amounts shortageAmounts = amounts.computeIfAbsent(shortage.key(), ignored -> new Amounts());
            shortageAmounts.addStored(shortage.available());
            shortageAmounts.addMissing(shortage.missing());
        } else if (diagnostic.partialPlan().isPresent()) {
            TrinityPlanningDiagnostic.PartialPlan partial = diagnostic.partialPlan().orElseThrow();
            partial.usedItems().forEach((key, amount) -> amounts
                    .computeIfAbsent(key, ignored -> new Amounts())
                    .addStored(amount));
            partial.emittedItems().forEach((key, amount) -> amounts
                    .computeIfAbsent(key, ignored -> new Amounts())
                    .addCrafting(amount));
            partial.inputRequirements().forEach((key, requirement) -> amounts
                    .computeIfAbsent(key, ignored -> new Amounts())
                    .addMissing(requirement.missing()));
            partial.missingItems().keySet().stream()
                    .filter(key -> !partial.inputRequirements().containsKey(key))
                    .forEach(key -> amounts.computeIfAbsent(key, ignored -> new Amounts()));
        } else {
            amounts.computeIfAbsent(plan.finalOutput().what(), ignored -> new Amounts());
        }
        return summarize(plan.bytes(), true, amounts);
    }

    private static CraftingPlanSummary summarize(long bytes,
                                                 boolean simulation,
                                                 LinkedHashMap<AEKey, Amounts> amounts) {
        ArrayList<CraftingPlanSummaryEntry> entries = new ArrayList<>(amounts.size());
        amounts.forEach((key, value) -> entries.add(new CraftingPlanSummaryEntry(
                key,
                TrinityAe2AmountProjection.toAe2Amount(value.missing),
                TrinityAe2AmountProjection.toAe2Amount(value.stored),
                TrinityAe2AmountProjection.toAe2Amount(value.crafting))));
        Collections.sort(entries);
        return new CraftingPlanSummary(bytes, simulation, List.copyOf(entries));
    }

    private static final class Amounts {

        private BigInteger missing = BigInteger.ZERO;
        private BigInteger stored = BigInteger.ZERO;
        private BigInteger crafting = BigInteger.ZERO;

        private void addMissing(BigInteger amount) {
            this.missing = this.missing.add(amount);
        }

        private void addStored(BigInteger amount) {
            this.stored = this.stored.add(amount);
        }

        private void addCrafting(BigInteger amount) {
            this.crafting = this.crafting.add(amount);
        }
    }
}
