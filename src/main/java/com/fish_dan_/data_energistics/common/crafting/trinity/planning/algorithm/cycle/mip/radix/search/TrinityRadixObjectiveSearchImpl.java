package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixDigits;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixLinearEncoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixResultDecoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixVariable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixBuiltModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixInfeasibleException;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixModelLimitException;

import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.optimisation.integer.NodeKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Uses exact integer feasibility probes to select objective digits without big-M weights or floating-point rounding.
 */
final class TrinityRadixObjectiveSearchImpl implements TrinityRadixObjectiveSearch {

    private static final IntegerStrategy FEASIBILITY_STRATEGY = IntegerStrategy.DEFAULT
            .withParallelism(() -> 1)
            .withPriorityDefinitions(NodeKey.MIN_OBJECTIVE);

    private final TrinityRadixCodec codec;
    private final TrinityRadixResultDecoder resultDecoder;

    TrinityRadixObjectiveSearchImpl(
                                    TrinityRadixCodec codec,
                                    TrinityRadixResultDecoder resultDecoder) {
        if (codec == null || resultDecoder == null) {
            throw new IllegalArgumentException("A Trinity radix objective search requires codec and decoder");
        }
        this.codec = codec;
        this.resultDecoder = resultDecoder;
    }

    @Override
    public TrinityAlgorithmResult<Map<Variable, BigInteger>> optimize(
                                                                      TrinityRadixBuiltModel built,
                                                                      TrinityPlanningControl control,
                                                                      TrinityRadixSolverMetrics metrics) {
        requireInputs(built, control, metrics);
        Map<Variable, BigInteger> lastValues = Map.of();
        TrinityRadixVariable objective = built.objective();
        BigInteger certifiedValue = built.minimize() ?
                built.objectiveLowerBound() : built.objectiveUpperBound();
        TrinityAlgorithmResult<Map<Variable, BigInteger>> certified = probeObjectiveValue(
                built,
                certifiedValue,
                control,
                metrics);
        if (certified.successful()) {
            return certified;
        }
        if (certified.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
            return certified;
        }
        BigInteger adjacentValue = built.minimize() ?
                certifiedValue.add(BigInteger.ONE) : certifiedValue.subtract(BigInteger.ONE);
        boolean adjacentWithinBounds = built.minimize() ?
                adjacentValue.compareTo(built.objectiveUpperBound()) <= 0 :
                adjacentValue.compareTo(built.objectiveLowerBound()) >= 0;
        if (adjacentWithinBounds) {
            TrinityAlgorithmResult<Map<Variable, BigInteger>> adjacent = probeObjectiveValue(
                    built,
                    adjacentValue,
                    control,
                    metrics);
            if (adjacent.successful()) {
                return adjacent;
            }
            if (adjacent.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                return adjacent;
            }
        }
        for (int digit = objective.digits().size() - 1; digit >= 0; digit--) {
            if (control.cancellationRequested()) {
                return TrinityRadixDiagnostics.failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                        Map.of("passes", Integer.toString(metrics.passes())));
            }
            if (control.deadlineExceeded()) {
                return TrinityRadixDiagnostics.timeout(metrics, "before_digit", objective.name(), digit);
            }
            TrinityAlgorithmResult<Map<Variable, BigInteger>> selected = built.minimize() ?
                    minimizeDigit(built, digit, control, metrics) :
                    maximizeDigit(built, digit, control, metrics);
            if (!selected.successful()) {
                return selected;
            }
            lastValues = selected.value();
            objective.digits().get(digit).level(lastValues.get(objective.digits().get(digit)));
        }
        return TrinityAlgorithmResult.success(lastValues);
    }

    @Override
    public TrinityAlgorithmResult<Map<Variable, BigInteger>> findFeasible(
                                                                          TrinityRadixBuiltModel built,
                                                                          TrinityPlanningControl control,
                                                                          TrinityRadixSolverMetrics metrics) {
        requireInputs(built, control, metrics);
        if (control.cancellationRequested()) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes())));
        }
        if (control.deadlineExceeded()) {
            return TrinityRadixDiagnostics.timeout(metrics, "before_overflow_proof", built.objective().name(), -1);
        }
        ExpressionsBasedModel solverModel = built.model().model();
        applyDeadline(solverModel, control);
        long started = System.nanoTime();
        Optimisation.Result result = solverModel.minimise();
        metrics.addPass(Math.max(0L, System.nanoTime() - started));
        if (control.cancellationRequested()) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes())));
        }
        if (!result.getState().isFeasible()) {
            if (control.deadlineExceeded()) {
                return TrinityRadixDiagnostics.timeout(metrics, result.getState().name(), built.objective().name(), -1);
            }
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution",
                    Map.of("state", result.getState().name()));
        }
        return this.resultDecoder.decode(
                solverModel,
                built.model().variables(),
                built.model().columnEquations(),
                result);
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> probeObjectiveValue(
                                                                                  TrinityRadixBuiltModel built,
                                                                                  BigInteger certifiedValue,
                                                                                  TrinityPlanningControl control,
                                                                                  TrinityRadixSolverMetrics metrics) {
        TrinityRadixLinearEncoder encoder = built.model();
        TrinityRadixVariable objective = built.objective();
        TrinityRadixDigits encoded = this.codec.encode(certifiedValue, objective.digits().size());
        ExpressionsBasedModel probeModel = encoder.model().copy();
        for (int digit = 0; digit < objective.digits().size(); digit++) {
            int objectiveIndex = encoder.model().indexOf(objective.digits().get(digit));
            probeModel.getVariable(objectiveIndex).level(encoded.digit(digit));
        }
        TrinityAlgorithmResult<Map<Variable, BigInteger>> probe = solveProbeModel(
                built,
                probeModel,
                -1,
                certifiedValue.toString(),
                control,
                metrics);
        if (!probe.successful()) {
            return probe;
        }
        BigInteger decoded = objective.decode(probe.value());
        return decoded.equals(certifiedValue) ? probe :
                TrinityRadixDiagnostics.inexact("radix_certified_objective", decoded + "/" + certifiedValue);
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> minimizeDigit(
                                                                            TrinityRadixBuiltModel built,
                                                                            int digit,
                                                                            TrinityPlanningControl control,
                                                                            TrinityRadixSolverMetrics metrics) {
        Variable objectiveDigit = built.objective().digits().get(digit);
        int certifiedLower = certifiedMinimumDigit(built, digit);
        int upper = decimalInteger(objectiveDigit.getUpperLimit()).intValueExact();
        TrinityAlgorithmResult<Map<Variable, BigInteger>> lowerProbe = probeDigit(
                built,
                digit,
                certifiedLower,
                certifiedLower,
                control,
                metrics);
        if (lowerProbe.successful()) {
            return requireSelectedDigit(lowerProbe.value(), objectiveDigit, certifiedLower);
        }
        if (lowerProbe.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
            return lowerProbe;
        }
        if (certifiedLower >= upper) {
            return lowerProbe;
        }

        int lower = Math.addExact(certifiedLower, 1);
        Map<Variable, BigInteger> best = null;
        while (lower < upper) {
            int candidate = lower + (upper - lower) / 2;
            TrinityAlgorithmResult<Map<Variable, BigInteger>> probe = probeDigit(
                    built,
                    digit,
                    lower,
                    candidate,
                    control,
                    metrics);
            if (probe.successful()) {
                int selected = selectedDigit(probe.value(), objectiveDigit);
                if (selected < lower || selected > candidate) {
                    return TrinityRadixDiagnostics.inexact("radix_digit_min_probe", Integer.toString(selected));
                }
                upper = selected;
                best = probe.value();
            } else if (probe.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                lower = Math.addExact(candidate, 1);
            } else {
                return probe;
            }
        }
        if (best != null && selectedDigit(best, objectiveDigit) == lower) {
            return TrinityAlgorithmResult.success(best);
        }
        TrinityAlgorithmResult<Map<Variable, BigInteger>> finalProbe = probeDigit(
                built,
                digit,
                lower,
                lower,
                control,
                metrics);
        return finalProbe.successful() ? requireSelectedDigit(finalProbe.value(), objectiveDigit, lower) : finalProbe;
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> maximizeDigit(
                                                                            TrinityRadixBuiltModel built,
                                                                            int digit,
                                                                            TrinityPlanningControl control,
                                                                            TrinityRadixSolverMetrics metrics) {
        Variable objectiveDigit = built.objective().digits().get(digit);
        int lower = decimalInteger(objectiveDigit.getLowerLimit()).intValueExact();
        int certifiedUpper = certifiedMaximumDigit(built, digit);
        TrinityAlgorithmResult<Map<Variable, BigInteger>> upperProbe = probeDigit(
                built,
                digit,
                certifiedUpper,
                certifiedUpper,
                control,
                metrics);
        if (upperProbe.successful()) {
            return requireSelectedDigit(upperProbe.value(), objectiveDigit, certifiedUpper);
        }
        if (upperProbe.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
            return upperProbe;
        }
        if (certifiedUpper <= lower) {
            return upperProbe;
        }

        int upper = Math.subtractExact(certifiedUpper, 1);
        Map<Variable, BigInteger> best = null;
        while (lower < upper) {
            int candidate = lower + (upper - lower + 1) / 2;
            TrinityAlgorithmResult<Map<Variable, BigInteger>> probe = probeDigit(
                    built,
                    digit,
                    candidate,
                    upper,
                    control,
                    metrics);
            if (probe.successful()) {
                int selected = selectedDigit(probe.value(), objectiveDigit);
                if (selected < candidate || selected > upper) {
                    return TrinityRadixDiagnostics.inexact("radix_digit_max_probe", Integer.toString(selected));
                }
                lower = selected;
                best = probe.value();
            } else if (probe.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                upper = Math.subtractExact(candidate, 1);
            } else {
                return probe;
            }
        }
        if (best != null && selectedDigit(best, objectiveDigit) == lower) {
            return TrinityAlgorithmResult.success(best);
        }
        TrinityAlgorithmResult<Map<Variable, BigInteger>> finalProbe = probeDigit(
                built,
                digit,
                lower,
                lower,
                control,
                metrics);
        return finalProbe.successful() ? requireSelectedDigit(finalProbe.value(), objectiveDigit, lower) : finalProbe;
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> probeDigit(
                                                                         TrinityRadixBuiltModel built,
                                                                         int digit,
                                                                         int lowerBound,
                                                                         int upperBound,
                                                                         TrinityPlanningControl control,
                                                                         TrinityRadixSolverMetrics metrics) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("A Trinity radix digit probe requires an ordered interval");
        }
        if (control.cancellationRequested()) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes())));
        }
        if (control.deadlineExceeded()) {
            return TrinityRadixDiagnostics.timeout(metrics, "before_digit_probe", built.objective().name(), digit);
        }
        TrinityRadixLinearEncoder encoder = built.model();
        ExpressionsBasedModel probeModel = encoder.model().copy();
        Variable objectiveDigit = built.objective().digits().get(digit);
        int objectiveIndex = encoder.model().indexOf(objectiveDigit);
        Variable probeDigit = probeModel.getVariable(objectiveIndex);
        if (lowerBound == upperBound) {
            probeDigit.level(lowerBound);
        } else {
            probeDigit.lower(lowerBound).upper(upperBound);
        }
        TrinityAlgorithmResult<Map<Variable, BigInteger>> decoded = solveProbeModel(
                built,
                probeModel,
                digit,
                lowerBound + ".." + upperBound,
                control,
                metrics);
        if (!decoded.successful()) {
            return decoded;
        }
        int selected = selectedDigit(decoded.value(), objectiveDigit);
        if (selected < lowerBound || selected > upperBound) {
            return TrinityRadixDiagnostics.inexact("radix_digit_probe_bound", Integer.toString(selected));
        }
        return decoded;
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> solveProbeModel(
                                                                              TrinityRadixBuiltModel built,
                                                                              ExpressionsBasedModel probeModel,
                                                                              int digit,
                                                                              String bound,
                                                                              TrinityPlanningControl control,
                                                                              TrinityRadixSolverMetrics metrics) {
        if (control.cancellationRequested()) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes())));
        }
        if (control.deadlineExceeded()) {
            return TrinityRadixDiagnostics.timeout(metrics, "before_probe_solve", built.objective().name(), digit);
        }
        applyDeadline(probeModel, control);
        long started = System.nanoTime();
        Optimisation.Result result = probeModel.minimise();
        metrics.addPass(Math.max(0L, System.nanoTime() - started));
        if (control.cancellationRequested()) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes())));
        }
        if (!result.getState().isFeasible()) {
            if (control.deadlineExceeded()) {
                return TrinityRadixDiagnostics.timeout(metrics, result.getState().name(), built.objective().name(), digit);
            }
            if (result.getState() != Optimisation.State.INFEASIBLE) {
                return TrinityRadixDiagnostics.inexact("radix_digit_probe_state", result.getState().name());
            }
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution",
                    Map.of(
                            "state", result.getState().name(),
                            "objective", built.objective().name(),
                            "digit", Integer.toString(digit),
                            "bound", bound));
        }
        TrinityRadixLinearEncoder encoder = built.model();
        return this.resultDecoder.decode(
                probeModel,
                encoder.variables(),
                encoder.columnEquations(),
                result);
    }

    private static TrinityAlgorithmResult<Map<Variable, BigInteger>> requireSelectedDigit(
                                                                                          Map<Variable, BigInteger> values,
                                                                                          Variable objectiveDigit,
                                                                                          int expected) {
        int selected = selectedDigit(values, objectiveDigit);
        return selected == expected ? TrinityAlgorithmResult.success(values) :
                TrinityRadixDiagnostics.inexact("radix_certified_digit", selected + "/" + expected);
    }

    private static int selectedDigit(Map<Variable, BigInteger> values, Variable objectiveDigit) {
        BigInteger selected = values.get(objectiveDigit);
        if (selected == null) {
            throw new IllegalStateException("A Trinity radix digit probe did not decode its objective");
        }
        return selected.intValueExact();
    }

    private static int certifiedMinimumDigit(TrinityRadixBuiltModel built, int digit) {
        BigInteger residual = built.objectiveLowerBound().subtract(higherPrefix(built.objective(), digit));
        BigInteger derived = residual.signum() <= 0 ? BigInteger.ZERO :
                residual.divide(radixPlace(digit));
        BigInteger currentLower = decimalInteger(built.objective().digits().get(digit).getLowerLimit());
        BigInteger candidate = derived.max(currentLower);
        if (candidate.compareTo(decimalInteger(built.objective().digits().get(digit).getUpperLimit())) > 0) {
            throw new TrinityRadixInfeasibleException("objective_minimum_digit");
        }
        return checkedDigit(candidate, "minimum");
    }

    private static int certifiedMaximumDigit(TrinityRadixBuiltModel built, int digit) {
        BigInteger residual = built.objectiveUpperBound().subtract(higherPrefix(built.objective(), digit));
        if (residual.signum() < 0) {
            throw new TrinityRadixInfeasibleException("objective_upper_prefix");
        }
        BigInteger derived = residual.divide(radixPlace(digit))
                .min(BigInteger.valueOf(TrinityRadixDigits.BASE - 1L));
        BigInteger currentUpper = decimalInteger(built.objective().digits().get(digit).getUpperLimit());
        BigInteger candidate = derived.min(currentUpper);
        if (candidate.compareTo(decimalInteger(built.objective().digits().get(digit).getLowerLimit())) < 0) {
            throw new TrinityRadixInfeasibleException("objective_maximum_digit");
        }
        return checkedDigit(candidate, "maximum");
    }

    private static BigInteger higherPrefix(TrinityRadixVariable objective, int digit) {
        BigInteger prefix = BigInteger.ZERO;
        for (int index = digit + 1; index < objective.digits().size(); index++) {
            Variable fixed = objective.digits().get(index);
            BigInteger lower = decimalInteger(fixed.getLowerLimit());
            BigInteger upper = decimalInteger(fixed.getUpperLimit());
            if (!lower.equals(upper)) {
                throw new IllegalStateException("Higher Trinity radix objective digits must already be fixed");
            }
            prefix = prefix.add(lower.multiply(radixPlace(index)));
        }
        return prefix;
    }

    private static BigInteger radixPlace(int digit) {
        return BigInteger.valueOf(TrinityRadixDigits.BASE).pow(digit);
    }

    private static BigInteger decimalInteger(BigDecimal value) {
        if (value == null) {
            throw new TrinityRadixModelLimitException(Map.of("reason", "unbounded_radix_variable"));
        }
        return value.toBigIntegerExact();
    }

    private static int checkedDigit(BigInteger value, String role) {
        if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(TrinityRadixDigits.BASE - 1L)) > 0) {
            throw new TrinityRadixInfeasibleException("objective_" + role + "_digit");
        }
        return value.intValueExact();
    }

    private static void applyDeadline(ExpressionsBasedModel model, TrinityPlanningControl control) {
        model.options.integer(FEASIBILITY_STRATEGY);
        long remainingNanos = control.remainingNanos();
        long remainingMillis = Math.max(
                1L,
                TimeUnit.NANOSECONDS.toMillis(remainingNanos) +
                        (remainingNanos % 1_000_000L == 0L ? 0L : 1L));
        model.options.time_abort = remainingMillis;
        model.options.time_suffice = remainingMillis;
    }

    private static void requireInputs(
                                      TrinityRadixBuiltModel built,
                                      TrinityPlanningControl control,
                                      TrinityRadixSolverMetrics metrics) {
        if (built == null || control == null || metrics == null) {
            throw new IllegalArgumentException("A Trinity radix objective search request is incomplete");
        }
    }
}
