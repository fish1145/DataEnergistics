package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Rejects approximate solver values before exact BigInteger conservation verification.
 */
public interface TrinityIntegerResultVerifier {

    /**
     * @return stateless exact verifier
     */
    static TrinityIntegerResultVerifier create() {
        return new TrinityIntegerResultVerifierImpl();
    }

    /**
     * @param values raw ojAlgo decimal values
     * @return exact integers or {@code MIP_INEXACT_RESULT}
     */
    TrinityAlgorithmResult<List<BigInteger>> verify(List<BigDecimal> values);
}
