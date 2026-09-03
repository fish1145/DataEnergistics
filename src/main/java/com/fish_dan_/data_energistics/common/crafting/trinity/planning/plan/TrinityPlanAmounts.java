package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;

/**
 * Centralizes exact amount validation and AE2 boundary conversion for immutable planning values.
 */
final class TrinityPlanAmounts {

    private TrinityPlanAmounts() {}

    static Map<AEKey, BigInteger> validatePositive(Map<AEKey, BigInteger> source, String role) {
        return validate(source, role, false);
    }

    static Map<AEKey, BigInteger> validateSignedNonZero(Map<AEKey, BigInteger> source, String role) {
        return validate(source, role, true);
    }

    private static Map<AEKey, BigInteger> validate(
                                                   Map<AEKey, BigInteger> source,
                                                   String role,
                                                   boolean signed) {
        source.forEach((key, amount) -> {
            if (amount.signum() == 0 || (!signed && amount.signum() < 0)) {
                throw new IllegalArgumentException("Trinity " + role + " must contain non-zero valid amounts");
            }
        });
        return Collections.unmodifiableMap(source);
    }
}
