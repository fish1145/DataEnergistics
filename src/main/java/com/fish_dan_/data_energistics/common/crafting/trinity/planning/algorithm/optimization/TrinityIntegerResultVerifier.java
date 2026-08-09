package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;

import net.minecraft.network.chat.Component;

import org.ojalgo.type.context.NumberContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Canonicalizes only values accepted by the active solver's integrality contract before exact conservation checks.
 * <p>
 * Solver-contract conversion whose candidates remain subject to exact {@link BigInteger} model verification.
 */
public final class TrinityIntegerResultVerifier {

    /**
     * @return stateless exact verifier
     */
    public static TrinityIntegerResultVerifier create() {
        return new TrinityIntegerResultVerifier();
    }

    /**
     * @param values               raw ojAlgo decimal values
     * @param integralityTolerance the same tolerance used by the integer solver that produced the values
     * @return exact integers or {@code MIP_INEXACT_RESULT}
     */
    public TrinityAlgorithmResult<List<BigInteger>> verify(
                                                           List<BigDecimal> values,
                                                           NumberContext integralityTolerance) {
        if (values == null || integralityTolerance == null) {
            throw new IllegalArgumentException("A Trinity integer verification requires values and solver tolerance");
        }
        ArrayList<BigInteger> integers = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = values.get(index);
            BigDecimal nearest = value == null ? null : value.setScale(0, RoundingMode.HALF_EVEN);
            BigDecimal displacement = value == null ? null : value.subtract(nearest).abs();
            if (value == null || !integralityTolerance.isZero(displacement.doubleValue())) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.inexact_result"),
                        Map.of(
                                "index", Integer.toString(index),
                                "value", value == null ? "null" : value.toPlainString())));
            }
            integers.add(nearest.toBigIntegerExact());
        }
        return TrinityAlgorithmResult.success(List.copyOf(integers));
    }
}
