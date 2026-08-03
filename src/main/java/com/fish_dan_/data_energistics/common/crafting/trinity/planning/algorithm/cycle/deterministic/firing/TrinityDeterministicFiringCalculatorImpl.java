package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicResidualResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityShiftedFiringOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Refines exact primitive repetitions against every net lower bound before delegating only tie-safe shifts.
 */
final class TrinityDeterministicFiringCalculatorImpl implements TrinityDeterministicFiringCalculator {

    private final TrinityShiftedFiringOptimizer firingOptimizer;

    TrinityDeterministicFiringCalculatorImpl(TrinityShiftedFiringOptimizer firingOptimizer) {
        if (firingOptimizer == null) {
            throw new IllegalArgumentException("A deterministic firing calculator requires an optimizer");
        }
        this.firingOptimizer = firingOptimizer;
    }

    @Override
    public TrinityAlgorithmResult<TrinityDeterministicFiringSolution> calculate(
                                                                                TrinityStronglyConnectedComponent component,
                                                                                TrinityCycleDemand demand,
                                                                                Map<AEKey, BigInteger> available,
                                                                                Set<AEKey> producibleInputs,
                                                                                TrinityDeterministicBasis basis,
                                                                                TrinityPlanningControl control) {
        if (component == null || demand == null || available == null || producibleInputs == null || basis == null ||
                control == null) {
            throw new IllegalArgumentException("A deterministic firing calculation request is incomplete");
        }
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
        for (AEKey demanded : demand.requiredNetChangeLowerBounds().keySet()) {
            if (primitiveNet.getOrDefault(demanded, TrinityDeterministicFiringMath.ZERO).signum() < 0) {
                return TrinityDeterministicDiagnostics.unsupported();
            }
        }

        Map<AEKey, BigInteger> netLowerBounds = netLowerBounds(
                component,
                demand,
                available,
                producibleInputs);
        BigInteger repetitions = initialRepetitions(demand, primitiveNet);
        TrinityDeterministicResidualResult residual = null;
        int balancePasses = 0;
        int balancePassLimit = Math.addExact(component.cycleVariants().size(), 2);
        while (balancePasses < balancePassLimit) {
            balancePasses = Math.incrementExact(balancePasses);
            TrinityAlgorithmResult<TrinityDeterministicResidualResult> solvedResidual = basis.residualTopology().solveResidual(
                    demand.requiredNetChangeLowerBounds(),
                    primitiveNet,
                    repetitions);
            if (!solvedResidual.successful()) {
                return TrinityAlgorithmResult.failure(solvedResidual.diagnostic());
            }
            residual = solvedResidual.value();
            if (hasPositiveEffectOnPrimitiveAxis(residual.netChange(), primitiveNet)) {
                return TrinityDeterministicDiagnostics.unsupported();
            }

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
        if (residual == null || !satisfies(
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
        boolean leastFiringsProven = hasClosedOutputBoundary(component);
        Map<TrinityPatternVariant, BigInteger> firings;
        if (leastFiringsProven) {
            firings = Collections.unmodifiableMap(new LinkedHashMap<>(baselineFirings));
        } else {
            TrinityPlanningAttempt<Map<TrinityPatternVariant, BigInteger>> optimized = this.firingOptimizer.optimize(
                    component,
                    demand,
                    available,
                    producibleInputs,
                    baselineFirings,
                    control);
            if (optimized.kind() != TrinityPlanningAttempt.Kind.PROVED_OPTIMAL) {
                return TrinityAlgorithmResult.failure(optimized.diagnostic());
            }
            firings = optimized.value();
        }
        Map<AEKey, BigInteger> totalNet = TrinityDeterministicFiringMath.netChange(firings);
        if (!satisfies(totalNet, netLowerBounds)) {
            return TrinityDeterministicDiagnostics.unsupported();
        }
        return TrinityAlgorithmResult.success(new TrinityDeterministicFiringSolution(
                basis,
                firings,
                totalNet,
                balancePasses,
                leastFiringsProven));
    }

    private static Map<AEKey, BigInteger> netLowerBounds(
                                                         TrinityStronglyConnectedComponent component,
                                                         TrinityCycleDemand demand,
                                                         Map<AEKey, BigInteger> available,
                                                         Set<AEKey> producibleInputs) {
        LinkedHashMap<AEKey, BigInteger> lower = new LinkedHashMap<>(
                demand.requiredNetChangeLowerBounds());
        LinkedHashSet<AEKey> touched = new LinkedHashSet<>(component.keys());
        component.cycleVariants().forEach(variant -> touched.addAll(variant.netChange().keySet()));
        touched.addAll(demand.finalBalanceLowerBounds().keySet());
        for (AEKey key : touched) {
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
                                                 TrinityCycleDemand demand,
                                                 Map<AEKey, BigInteger> primitiveNet) {
        BigInteger repetitions = TrinityDeterministicFiringMath.ZERO;
        for (Map.Entry<AEKey, BigInteger> required : demand.requiredNetChangeLowerBounds().entrySet()) {
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

    private static boolean satisfies(
                                     Map<AEKey, BigInteger> net,
                                     Map<AEKey, BigInteger> lowerBounds) {
        return lowerBounds.entrySet().stream().allMatch(entry -> net
                .getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static boolean hasPositiveEffectOnPrimitiveAxis(
                                                            Map<AEKey, BigInteger> residualNet,
                                                            Map<AEKey, BigInteger> primitiveNet) {
        return primitiveNet.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .anyMatch(entry -> residualNet
                        .getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)
                        .signum() > 0);
    }

    /**
     * The residual reverse-DAG calculation is a componentwise least fixed point only when no transition can
     * compensate for additional firings by producing a boundary key. Under that monotone boundary condition,
     * every feasible vector contains the residual vector and enough copies of the primitive reservoir vector.
     */
    private static boolean hasClosedOutputBoundary(TrinityStronglyConnectedComponent component) {
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        return component.cycleVariants().stream()
                .allMatch(variant -> internalKeys.containsAll(variant.outputs().keySet()));
    }
}
