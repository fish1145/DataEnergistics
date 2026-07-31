package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;

import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scale-independent exact conversion that never rounds solver output.
 */
final class TrinityIntegerResultVerifierImpl implements TrinityIntegerResultVerifier {

    @Override
    public TrinityAlgorithmResult<List<BigInteger>> verify(List<BigDecimal> values) {
        if (values == null) {
            throw new IllegalArgumentException("A Trinity integer verification requires solver values");
        }
        ArrayList<BigInteger> integers = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = values.get(index);
            if (value == null || value.stripTrailingZeros().scale() > 0) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                        Component.literal("ojAlgo returned a non-integral Trinity firing value"),
                        Map.of(
                                "index", Integer.toString(index),
                                "value", value == null ? "null" : value.toPlainString())));
            }
            integers.add(value.toBigIntegerExact());
        }
        return TrinityAlgorithmResult.success(List.copyOf(integers));
    }
}
