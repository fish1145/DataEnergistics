package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputShortage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.PartialPlan;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges material evidence retained along one selected diagnostic route without promoting unresolved demand to an
 * exact shortage.
 *
 * <p>
 * The accumulator is request-confined. Exact requirements survive a merge only when their missing amount still equals
 * the complete unresolved amount for the key after nested outputs have been reconciled.
 * </p>
 */
public final class TrinityDiagnosticMaterialAccumulator {

    private PartialPlan accumulated;

    private TrinityDiagnosticMaterialAccumulator(PartialPlan accumulated) {
        if (accumulated == null) {
            throw new IllegalArgumentException("A Trinity diagnostic material accumulator requires initial evidence");
        }
        this.accumulated = accumulated;
    }

    /**
     * Starts one request-confined accumulator from the graph progress already retained by the caller.
     */
    public static TrinityDiagnosticMaterialAccumulator create(PartialPlan accumulated) {
        return new TrinityDiagnosticMaterialAccumulator(accumulated);
    }

    /**
     * Promotes a conclusive one-key shortage to the same material representation used by multi-key diagnostics.
     */
    public static PartialPlan fromShortage(InputShortage shortage) {
        if (shortage == null) {
            throw new IllegalArgumentException("A Trinity diagnostic shortage cannot be null");
        }
        LinkedHashMap<AEKey, BigInteger> used = new LinkedHashMap<>();
        if (shortage.available().signum() > 0) {
            used.put(shortage.key(), shortage.available());
        }
        return new PartialPlan(
                used,
                Map.of(),
                Map.of(shortage.key(), shortage.missing()),
                Map.of(shortage.key(), new InputRequirement(
                        shortage.required(),
                        shortage.available(),
                        shortage.missing())));
    }

    /**
     * Adds evidence from the same selected route after reconciling its emitted outputs against earlier unresolved
     * demand.
     */
    public void add(PartialPlan nested) {
        if (nested == null) {
            throw new IllegalArgumentException("Nested Trinity diagnostic material evidence cannot be null");
        }
        this.accumulated = merge(this.accumulated, nested);
    }

    /**
     * Returns the immutable merged material view.
     */
    public PartialPlan snapshot() {
        return this.accumulated;
    }

    private static PartialPlan merge(PartialPlan accumulated, PartialPlan nested) {
        LinkedHashMap<AEKey, BigInteger> used = sum(accumulated.usedItems(), nested.usedItems());
        LinkedHashMap<AEKey, BigInteger> emitted = sum(accumulated.emittedItems(), nested.emittedItems());

        LinkedHashMap<AEKey, BigInteger> remainingAccumulated = new LinkedHashMap<>(
                accumulated.missingItems());
        nested.emittedItems().forEach((key, amount) -> subtractPositive(remainingAccumulated, key, amount));

        LinkedHashMap<AEKey, BigInteger> missing = sum(remainingAccumulated, nested.missingItems());
        LinkedHashMap<AEKey, InputRequirement> exactCandidates = new LinkedHashMap<>();
        accumulated.inputRequirements().forEach((key, requirement) -> {
            if (requirement.missing().equals(remainingAccumulated.get(key))) {
                mergeRequirement(exactCandidates, key, requirement);
            }
        });
        nested.inputRequirements().forEach((key, requirement) -> mergeRequirement(
                exactCandidates,
                key,
                requirement));

        LinkedHashMap<AEKey, InputRequirement> exactRequirements = new LinkedHashMap<>();
        exactCandidates.forEach((key, requirement) -> {
            if (requirement.missing().equals(missing.get(key))) {
                exactRequirements.put(key, requirement);
            }
        });
        return new PartialPlan(used, emitted, missing, exactRequirements);
    }

    private static LinkedHashMap<AEKey, BigInteger> sum(
                                                        Map<AEKey, BigInteger> left,
                                                        Map<AEKey, BigInteger> right) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>(left);
        right.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        return result;
    }

    private static void subtractPositive(
                                         Map<AEKey, BigInteger> amounts,
                                         AEKey key,
                                         BigInteger amount) {
        BigInteger existing = amounts.get(key);
        if (existing == null) {
            return;
        }
        BigInteger remaining = existing.subtract(amount);
        if (remaining.signum() > 0) {
            amounts.put(key, remaining);
        } else {
            amounts.remove(key);
        }
    }

    private static void mergeRequirement(
                                         Map<AEKey, InputRequirement> requirements,
                                         AEKey key,
                                         InputRequirement added) {
        requirements.compute(key, (ignored, existing) -> existing == null ? added : new InputRequirement(
                existing.required().add(added.required()),
                existing.available().add(added.available()),
                existing.missing().add(added.missing())));
    }
}
