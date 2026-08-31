package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicResidualResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Calculates an exact feasible firing vector for one applicable deterministic cycle basis.
 * <p>
 * Refines exact primitive repetitions against every net lower bound without requiring a global objective solve.
 */
public final class TrinityDeterministicFiringCalculator {

    /**
     * Creates the exact constructive calculator.
     */
    public static TrinityDeterministicFiringCalculator create() {
        return new TrinityDeterministicFiringCalculator();
    }

    /**
     * Solves repeated primitive firings plus the unique acyclic residual into an exact feasible vector.
     */
    public TrinityAlgorithmResult<TrinityDeterministicFiringSolution> calculate(
                                                                                TrinityStronglyConnectedComponent component,
                                                                                TrinityCycleDemand demand,
                                                                                Map<AEKey, BigInteger> available,
                                                                                Set<AEKey> producibleInputs,
                                                                                TrinityDeterministicBasis basis,
                                                                                TrinityPlanningControl control) {
        Map<TrinityPatternVariant, BigInteger> primitiveFirings = basis.primitiveFirings();
        Map<AEKey, BigInteger> primitiveNet = basis.primitiveNet();
        BigInteger reservoirEffect = primitiveNet.getOrDefault(
                basis.reservoir(),
                TrinityDeterministicFiringMath.ZERO);
        if (reservoirEffect.signum() <= 0) {
            return TrinityDeterministicDiagnostics.unsupported();
        }
        for (AEKey key : component.keys()) {
            if (!key.equals(basis.reservoir()) &&
                    primitiveNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO).signum() != 0) {
                return TrinityDeterministicDiagnostics.unsupported();
            }
        }
        Map<AEKey, BigInteger> netLowerBounds = internalNetLowerBounds(
                component,
                demand,
                available,
                producibleInputs);
        BigInteger repetitions = initialRepetitions(netLowerBounds, primitiveNet);
        TrinityDeterministicResidualResult residual = null;
        int balancePasses = 0;
        int balancePassLimit = Math.addExact(component.cycleVariants().size(), 2);
        while (balancePasses < balancePassLimit) {
            TrinityDeterministicDiagnostics.StopState stop = TrinityDeterministicDiagnostics.stopState(control);
            if (stop != TrinityDeterministicDiagnostics.StopState.RUNNING) {
                return TrinityDeterministicDiagnostics.stopped(stop);
            }
            balancePasses = Math.incrementExact(balancePasses);
            TrinityAlgorithmResult<TrinityDeterministicResidualResult> solvedResidual = basis.residualTopology().solveResidual(
                    netLowerBounds,
                    primitiveNet,
                    repetitions);
            if (!solvedResidual.successful()) {
                return TrinityAlgorithmResult.failure(solvedResidual.diagnostic());
            }
            residual = solvedResidual.value();
            Map<AEKey, BigInteger> combinedNet = TrinityDeterministicFiringMath.addSigned(
                    TrinityDeterministicFiringMath.multiplySigned(primitiveNet, repetitions),
                    residual.netChange());
            BigInteger jump = requiredRepetitionJump(combinedNet, netLowerBounds, primitiveNet);
            if (jump.signum() < 0) {
                return TrinityDeterministicDiagnostics.unsupported();
            }
            if (jump.signum() == 0) {
                break;
            }
            repetitions = repetitions.add(jump);
        }
        if (residual == null || violatesLowerBounds(
                TrinityDeterministicFiringMath.addSigned(
                        TrinityDeterministicFiringMath.multiplySigned(primitiveNet, repetitions),
                        residual.netChange()),
                netLowerBounds)) {
            return TrinityDeterministicDiagnostics.unsupported();
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> baselineFirings = TrinityDeterministicFiringMath.aggregateRepeated(
                primitiveFirings,
                repetitions,
                residual.firings());
        if (baselineFirings.isEmpty()) {
            return TrinityDeterministicDiagnostics.unsupported();
        }
        Map<TrinityPatternVariant, BigInteger> firings = Collections.unmodifiableMap(
                new LinkedHashMap<>(baselineFirings));
        Map<AEKey, BigInteger> totalNet = TrinityDeterministicFiringMath.netChange(firings);
        if (violatesLowerBounds(totalNet, netLowerBounds)) {
            return TrinityDeterministicDiagnostics.unsupported();
        }
        return TrinityAlgorithmResult.success(new TrinityDeterministicFiringSolution(
                basis,
                firings,
                totalNet,
                balancePasses));
    }

    /**
     * Adds only finite internal-balance constraints to the requested exports. External inputs intentionally remain
     * unbounded here so the graph aggregator can build the complete plan and report their exact shortage afterwards.
     */
    private static Map<AEKey, BigInteger> internalNetLowerBounds(
                                                                 TrinityStronglyConnectedComponent component,
                                                                 TrinityCycleDemand demand,
                                                                 Map<AEKey, BigInteger> available,
                                                                 Set<AEKey> producibleInputs) {
        LinkedHashMap<AEKey, BigInteger> lower = new LinkedHashMap<>(demand.requiredNetChangeLowerBounds());
        for (AEKey key : component.keys()) {
            if (producibleInputs.contains(key)) {
                continue;
            }
            BigInteger finiteLower = demand.finalBalanceLowerBounds()
                    .getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                    .subtract(available.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            lower.merge(key, finiteLower, BigInteger::max);
        }
        return Collections.unmodifiableMap(lower);
    }

    private static BigInteger initialRepetitions(
                                                 Map<AEKey, BigInteger> netLowerBounds,
                                                 Map<AEKey, BigInteger> primitiveNet) {
        BigInteger repetitions = TrinityDeterministicFiringMath.ZERO;
        for (Map.Entry<AEKey, BigInteger> required : netLowerBounds.entrySet()) {
            BigInteger effect = primitiveNet.getOrDefault(required.getKey(), TrinityDeterministicFiringMath.ZERO);
            if (effect.signum() > 0) {
                repetitions = repetitions.max(TrinityDeterministicFiringMath.ceilDivide(
                        required.getValue(),
                        effect));
            }
        }
        return repetitions;
    }

    private static BigInteger requiredRepetitionJump(
                                                     Map<AEKey, BigInteger> combinedNet,
                                                     Map<AEKey, BigInteger> lowerBounds,
                                                     Map<AEKey, BigInteger> primitiveNet) {
        BigInteger jump = TrinityDeterministicFiringMath.ZERO;
        for (Map.Entry<AEKey, BigInteger> bound : lowerBounds.entrySet()) {
            BigInteger deficit = bound.getValue().subtract(
                    combinedNet.getOrDefault(bound.getKey(), TrinityDeterministicFiringMath.ZERO));
            if (deficit.signum() <= 0) {
                continue;
            }
            BigInteger effect = primitiveNet.getOrDefault(bound.getKey(), TrinityDeterministicFiringMath.ZERO);
            if (effect.signum() <= 0) {
                return BigInteger.valueOf(-1L);
            }
            jump = jump.max(TrinityDeterministicFiringMath.ceilDivide(deficit, effect));
        }
        return jump;
    }

    private static boolean violatesLowerBounds(
                                               Map<AEKey, BigInteger> net,
                                               Map<AEKey, BigInteger> lowerBounds) {
        return lowerBounds.entrySet().stream().anyMatch(entry -> net
                .getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)
                .compareTo(entry.getValue()) < 0);
    }
}
