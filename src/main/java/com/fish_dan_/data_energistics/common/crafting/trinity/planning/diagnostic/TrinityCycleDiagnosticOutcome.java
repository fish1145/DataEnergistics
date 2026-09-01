package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.PartialPlan;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
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
     * Freezes the accounting and rejects overlapping actual, missing and predecessor-produced allocations.
     */
    public TrinityCycleDiagnosticOutcome {
        if (evidence == null || actualInputs == null || inputRequirements == null || boundaryInputs == null) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle outcome requires complete accounting");
        }
        Map<AEKey, BigInteger> copiedActualInputs = copyPositiveAmounts(actualInputs, "actual input");
        Map<AEKey, BigInteger> copiedBoundaryInputs = copyPositiveAmounts(boundaryInputs, "boundary input");
        LinkedHashMap<AEKey, InputRequirement> copiedRequirements = new LinkedHashMap<>();
        inputRequirements.forEach((key, requirement) -> {
            if (key == null || requirement == null) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle shortage cannot be null");
            }
            if (!copiedActualInputs.getOrDefault(key, BigInteger.ZERO).equals(requirement.available())) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle shortage must match its actual input");
            }
            copiedRequirements.put(key, requirement);
        });
        if (copiedBoundaryInputs.keySet().stream().anyMatch(key -> copiedActualInputs.containsKey(key) ||
                copiedRequirements.containsKey(key))) {
            throw new IllegalArgumentException(
                    "A Trinity diagnostic cycle input cannot be both reserved and predecessor-produced");
        }
        actualInputs = copiedActualInputs;
        inputRequirements = Collections.unmodifiableMap(copiedRequirements);
        boundaryInputs = copiedBoundaryInputs;
    }

    /**
     * Splits the proved schedule inputs using the same all-or-produce-upstream policy as graph demand aggregation.
     */
    public static TrinityCycleDiagnosticOutcome create(
                                                       TrinityCycleDiagnosticEvidence evidence,
                                                       Map<AEKey, BigInteger> available,
                                                       Set<AEKey> producibleInputs) {
        if (evidence == null || available == null || producibleInputs == null) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle split request is incomplete");
        }
        LinkedHashMap<AEKey, BigInteger> actual = new LinkedHashMap<>();
        LinkedHashMap<AEKey, InputRequirement> shortages = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> boundary = new LinkedHashMap<>();
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
        LinkedHashMap<AEKey, BigInteger> missing = new LinkedHashMap<>();
        this.inputRequirements.forEach((key, requirement) -> missing.put(key, requirement.missing()));
        return new PartialPlan(
                this.actualInputs,
                this.evidence.emittedItems(),
                missing,
                this.inputRequirements);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(
                                                              Map<AEKey, BigInteger> source,
                                                              String role) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
