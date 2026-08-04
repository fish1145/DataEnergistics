package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;

import org.ojalgo.type.context.NumberContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Canonicalizes only values accepted by the active solver's integrality contract before exact conservation checks.
 */
public interface TrinityIntegerResultVerifier {

    /**
     * @return stateless exact verifier
     */
    static TrinityIntegerResultVerifier create() {
        return new TrinityIntegerResultVerifierImpl();
    }

    /**
     * @param values               raw ojAlgo decimal values
     * @param integralityTolerance the same tolerance used by the integer solver that produced the values
     * @return exact integers or {@code MIP_INEXACT_RESULT}
     */
    TrinityAlgorithmResult<List<BigInteger>> verify(
                                                    List<BigDecimal> values,
                                                    NumberContext integralityTolerance);
}
