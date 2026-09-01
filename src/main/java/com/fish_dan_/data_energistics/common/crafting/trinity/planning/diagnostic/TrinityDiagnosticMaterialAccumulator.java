package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputShortage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.PartialPlan;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;
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
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> used = new Object2ObjectLinkedOpenHashMap<>();
        if (shortage.available().signum() > 0) {
            used.put(shortage.key(), shortage.available());
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missing = new Object2ObjectLinkedOpenHashMap<>();
        missing.put(shortage.key(), shortage.missing());
        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> requirements = new Object2ObjectLinkedOpenHashMap<>();
        requirements.put(shortage.key(), new InputRequirement(
                shortage.required(),
                shortage.available(),
                shortage.missing()));
        return new PartialPlan(
                used,
                Map.of(),
                missing,
                requirements);
    }

    /**
     * Adds evidence from the same selected route after reconciling its emitted outputs against earlier unresolved
     * demand.
     */
    public void add(PartialPlan nested) {
        this.accumulated = merge(this.accumulated, nested);
    }

    /**
     * Returns the immutable merged material view.
     */
    public PartialPlan snapshot() {
        return this.accumulated;
    }

    private static PartialPlan merge(PartialPlan accumulated, PartialPlan nested) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> used = sum(accumulated.usedItems(), nested.usedItems());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> emitted = sum(accumulated.emittedItems(), nested.emittedItems());

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> remainingAccumulated = new Object2ObjectLinkedOpenHashMap<>(
                accumulated.missingItems());
        nested.emittedItems().forEach((key, amount) -> subtractPositive(remainingAccumulated, key, amount));

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missing = sum(remainingAccumulated, nested.missingItems());
        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> exactCandidates = new Object2ObjectLinkedOpenHashMap<>();
        accumulated.inputRequirements().forEach((key, requirement) -> {
            if (requirement.missing().equals(remainingAccumulated.get(key))) {
                mergeRequirement(exactCandidates, key, requirement);
            }
        });
        nested.inputRequirements().forEach((key, requirement) -> mergeRequirement(
                exactCandidates,
                key,
                requirement));

        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> exactRequirements = new Object2ObjectLinkedOpenHashMap<>();
        exactCandidates.forEach((key, requirement) -> {
            if (requirement.missing().equals(missing.get(key))) {
                exactRequirements.put(key, requirement);
            }
        });
        return new PartialPlan(used, emitted, missing, exactRequirements);
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> sum(
                                                                         Map<AEKey, BigInteger> left,
                                                                         Map<AEKey, BigInteger> right) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>(left);
        right.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        return result;
    }

    private static void subtractPositive(
                                         Object2ObjectMap<AEKey, BigInteger> amounts,
                                         AEKey key,
                                         BigInteger amount) {
        BigInteger remaining = amounts.getOrDefault(key, BigInteger.ZERO).subtract(amount);
        if (remaining.signum() > 0) {
            amounts.put(key, remaining);
        } else {
            amounts.remove(key);
        }
    }

    private static void mergeRequirement(
                                         Object2ObjectMap<AEKey, InputRequirement> requirements,
                                         AEKey key,
                                         InputRequirement added) {
        requirements.merge(key, added, (existing, value) -> new InputRequirement(
                existing.required().add(value.required()),
                existing.available().add(value.available()),
                existing.missing().add(value.missing())));
    }
}
