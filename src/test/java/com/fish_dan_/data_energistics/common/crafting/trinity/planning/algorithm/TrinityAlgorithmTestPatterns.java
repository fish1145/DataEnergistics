package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrinityAlgorithmTestPatterns {

    private TrinityAlgorithmTestPatterns() {}

    public static TrinityPatternVariant variant(String name,
                                                Map<AEKey, BigInteger> inputs,
                                                Map<AEKey, BigInteger> outputs) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        inputs.forEach((key, amount) -> net.merge(key, amount.negate(), BigInteger::add));
        outputs.forEach((key, amount) -> net.merge(key, amount, BigInteger::add));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        AEKey primaryOutput = outputs.keySet().stream().findFirst().orElseThrow();
        return new TrinityPatternVariant(
                new TrinityPatternIdentity("definition-" + name, "publication-" + name),
                primaryOutput,
                0,
                List.of(),
                List.of(),
                inputs,
                outputs,
                net);
    }

    public static Map<AEKey, BigInteger> amounts(Object... keysAndAmounts) {
        if (keysAndAmounts.length % 2 != 0) {
            throw new IllegalArgumentException("Test amounts require key/amount pairs");
        }
        LinkedHashMap<AEKey, BigInteger> amounts = new LinkedHashMap<>();
        for (int index = 0; index < keysAndAmounts.length; index += 2) {
            AEKey key = (AEKey) keysAndAmounts[index];
            BigInteger amount = (BigInteger) keysAndAmounts[index + 1];
            amounts.put(key, amount);
        }
        return amounts;
    }
}
