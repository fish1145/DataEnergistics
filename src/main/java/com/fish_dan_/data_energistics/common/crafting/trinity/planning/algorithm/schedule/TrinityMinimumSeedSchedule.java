package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimum internal seed found for one fixed firing vector within the bounded compressed state space.
 *
 * @param externalInputs exact boundary balances required before execution
 * @param minimumSeed    exact internal balances required before execution
 * @param schedule       executable proof starting with both input categories
 */
public record TrinityMinimumSeedSchedule(
                                         Map<AEKey, BigInteger> externalInputs,
                                         Map<AEKey, BigInteger> minimumSeed,
                                         TrinityCompressedSchedule schedule) {

    /**
     * Copies the positive seed and retains one complete executable schedule.
     */
    public TrinityMinimumSeedSchedule {
        if (externalInputs == null || minimumSeed == null || schedule == null) {
            throw new IllegalArgumentException("A Trinity minimum-seed result requires seed and schedule");
        }
        externalInputs = copyPositive(externalInputs, "external input");
        minimumSeed = copyPositive(minimumSeed, "minimum seed");
    }

    private static Map<AEKey, BigInteger> copyPositive(Map<AEKey, BigInteger> source, String role) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity " + role + " must contain positive amounts");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
