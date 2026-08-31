package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes exact amount validation and AE2 boundary conversion for immutable planning values.
 */
final class TrinityPlanAmounts {

    private TrinityPlanAmounts() {}

    static Map<AEKey, BigInteger> copyPositive(Map<AEKey, BigInteger> source, String role) {
        return copy(source, role, false);
    }

    static Map<AEKey, BigInteger> copySignedNonZero(Map<AEKey, BigInteger> source, String role) {
        return copy(source, role, true);
    }

    private static Map<AEKey, BigInteger> copy(
                                               Map<AEKey, BigInteger> source,
                                               String role,
                                               boolean signed) {
        if (source == null) {
            throw new IllegalArgumentException("Trinity " + role + " amounts are required");
        }
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null) {
                throw new IllegalArgumentException("Trinity " + role + " cannot contain null");
            }
            if (amount.signum() == 0 || (!signed && amount.signum() < 0)) {
                throw new IllegalArgumentException("Trinity " + role + " must contain non-zero valid amounts");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
