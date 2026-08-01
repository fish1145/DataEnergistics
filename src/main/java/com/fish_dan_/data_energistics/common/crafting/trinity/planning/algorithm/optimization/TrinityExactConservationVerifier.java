package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Recomputes every solver balance and bound with BigInteger before one result can constrain the next pass.
 */
public interface TrinityExactConservationVerifier {

    /**
     * @return stateless exact verifier
     */
    static TrinityExactConservationVerifier create() {
        return new TrinityExactConservationVerifierImpl();
    }

    /**
     * @param variants                     complete model transition set
     * @param firings                      exact non-zero firing values
     * @param initialInputs                exact seed and external values
     * @param upperBounds                  finite input upper bounds; absent keys are intentionally
     *                                     unbounded/upstream-craftable
     * @param finalLowerBounds             required final balance per key
     * @param requiredNetChangeLowerBounds required net effects independent of seed
     * @return recomputed signed net change or {@code MIP_INEXACT_RESULT}
     */
    TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                          List<TrinityPatternVariant> variants,
                                                          Map<TrinityPatternVariant, BigInteger> firings,
                                                          Map<AEKey, BigInteger> initialInputs,
                                                          Map<AEKey, BigInteger> upperBounds,
                                                          Map<AEKey, BigInteger> finalLowerBounds,
                                                          Map<AEKey, BigInteger> requiredNetChangeLowerBounds);

    /**
     * Adapts the legacy single-target net constraint to the generalized map contract.
     *
     * @param variants          complete model transition set
     * @param firings           exact non-zero firing values
     * @param initialInputs     exact seed and external values
     * @param upperBounds       finite input upper bounds
     * @param finalLowerBounds  required final balance per key
     * @param target            requested productive key
     * @param requiredTargetNet required net target effect independent of seed
     * @return recomputed signed net change or {@code MIP_INEXACT_RESULT}
     */
    default TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                                  List<TrinityPatternVariant> variants,
                                                                  Map<TrinityPatternVariant, BigInteger> firings,
                                                                  Map<AEKey, BigInteger> initialInputs,
                                                                  Map<AEKey, BigInteger> upperBounds,
                                                                  Map<AEKey, BigInteger> finalLowerBounds,
                                                                  AEKey target,
                                                                  BigInteger requiredTargetNet) {
        if (target == null || requiredTargetNet == null || requiredTargetNet.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity conservation target net must be positive");
        }
        return verify(
                variants,
                firings,
                initialInputs,
                upperBounds,
                finalLowerBounds,
                Map.of(target, requiredTargetNet));
    }
}
