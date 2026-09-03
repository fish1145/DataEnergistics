package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.PartialPlan;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request-local accounting used to continue graph diagnosis around one fully proved cycle without making it
 * executable.
 *
 * @param evidence          exact compressed cycle proof
 * @param actualInputs      real inventory that may be reserved for the diagnostic branch
 * @param inputRequirements conclusive finite-input shortages
 * @param boundaryInputs    complete inputs that an earlier graph component must produce
 */
public record TrinityCycleDiagnosticOutcome(
                                            TrinityCycleDiagnosticEvidence evidence,
                                            Map<AEKey, BigInteger> actualInputs,
                                            Map<AEKey, InputRequirement> inputRequirements,
                                            Map<AEKey, BigInteger> boundaryInputs) {

    /**
     * Validates the owned accounting and rejects overlapping actual, missing and predecessor-produced allocations.
     */
    public TrinityCycleDiagnosticOutcome {
        actualInputs = validatePositiveAmounts(actualInputs, "actual input");
        boundaryInputs = validatePositiveAmounts(boundaryInputs, "boundary input");
        for (Map.Entry<AEKey, InputRequirement> shortage : inputRequirements.entrySet()) {
            if (!actualInputs.getOrDefault(shortage.getKey(), BigInteger.ZERO).equals(shortage.getValue().available())) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle shortage must match its actual input");
            }
        }
        for (AEKey key : boundaryInputs.keySet()) {
            if (actualInputs.containsKey(key) || inputRequirements.containsKey(key)) {
                throw new IllegalArgumentException(
                        "A Trinity diagnostic cycle input cannot be both reserved and predecessor-produced");
            }
        }
        inputRequirements = Collections.unmodifiableMap(inputRequirements);
    }

    /**
     * Splits the proved schedule inputs using the same all-or-produce-upstream policy as graph demand aggregation.
     */
    public static TrinityCycleDiagnosticOutcome create(
                                                       TrinityCycleDiagnosticEvidence evidence,
                                                       Map<AEKey, BigInteger> available,
                                                       Set<AEKey> producibleInputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> actual = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> shortages = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> boundary = new Object2ObjectLinkedOpenHashMap<>();
        evidence.initialInputs().forEach((key, required) -> {
            BigInteger stored = available.getOrDefault(key, BigInteger.ZERO);
            if (stored.signum() < 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle inventory cannot be negative");
            }
            if (stored.compareTo(required) >= 0) {
                actual.put(key, required);
            } else if (producibleInputs.contains(key)) {
                boundary.put(key, required);
            } else {
                if (stored.signum() > 0) {
                    actual.put(key, stored);
                }
                BigInteger missing = required.subtract(stored);
                shortages.put(key, new InputRequirement(required, stored, missing));
            }
        });
        return new TrinityCycleDiagnosticOutcome(evidence, actual, shortages, boundary);
    }

    /**
     * Produces the non-executable material view attached to a terminal diagnostic.
     */
    public PartialPlan materials() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missing = new Object2ObjectLinkedOpenHashMap<>();
        this.inputRequirements.forEach((key, requirement) -> missing.put(key, requirement.missing()));
        return new PartialPlan(
                this.actualInputs,
                this.evidence.emittedItems(),
                missing,
                this.inputRequirements,
                List.of());
    }

    private static Map<AEKey, BigInteger> validatePositiveAmounts(
                                                                  Map<AEKey, BigInteger> source,
                                                                  String role) {
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle " + role + " must be positive");
            }
        });
        return Collections.unmodifiableMap(source);
    }
}
