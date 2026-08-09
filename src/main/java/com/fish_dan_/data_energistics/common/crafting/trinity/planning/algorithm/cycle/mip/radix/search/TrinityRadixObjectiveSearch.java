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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Proves one radix objective exactly using a certified full-value probe followed by bounded per-digit feasibility.
 * <p>
 * Selects objective digits with exact integer optimization, avoiding big-M weights and repeated feasibility searches.
 */
public final class TrinityRadixObjectiveSearch {

    /**
     * Creates an objective search sharing the encoder's radix codec and exact result decoder.
     */
    public static TrinityRadixObjectiveSearch create(
                                                     TrinityRadixCodec codec,
                                                     TrinityRadixResultDecoder resultDecoder) {
        return new TrinityRadixObjectiveSearch(codec, resultDecoder);
    }

    @SuppressWarnings("unchecked")
    private static final IntegerStrategy INTEGER_STRATEGY = IntegerStrategy.DEFAULT
            .withParallelism(() -> 1)
            .withPriorityDefinitions(NodeKey.MIN_OBJECTIVE);

    private final TrinityRadixCodec codec;
    private final TrinityRadixResultDecoder resultDecoder;

    TrinityRadixObjectiveSearch(
                                TrinityRadixCodec codec,
                                TrinityRadixResultDecoder resultDecoder) {
        if (codec == null || resultDecoder == null) {
            throw new IllegalArgumentException("A Trinity radix objective search requires codec and decoder");
        }
        this.codec = codec;
        this.resultDecoder = resultDecoder;
    }

    /**
     * Selects the exact lexicographic optimum for the assembled objective.
     */
    public TrinityAlgorithmResult<Map<Variable, BigInteger>> optimize(
                                                                      TrinityRadixBuiltModel built,
                                                                      TrinityPlanningControl control,
                                                                      TrinityRadixSolverMetrics metrics) {
        requireInputs(built, control, metrics);
        Map<Variable, BigInteger> lastValues = Map.of();
        Map<Integer, Integer> fixedDigits = new LinkedHashMap<>();
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
            TrinityAlgorithmResult<Map<Variable, BigInteger>> selected = optimizeDigit(
                    built,
                    digit,
                    fixedDigits,
                    control,
                    metrics);
            if (!selected.successful()) {
                return selected;
            }
            lastValues = selected.value();
            fixedDigits.put(digit, selectedDigit(lastValues, objective.digits().get(digit)));
        }
        return TrinityAlgorithmResult.success(lastValues);
    }

    /**
     * Finds any exactly decoded witness in a proof-domain model used only to distinguish overflow from infeasibility.
     */
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
                metrics,
                false);
        if (!probe.successful()) {
            return probe;
        }
        BigInteger decoded = objective.decode(probe.value());
        return decoded.equals(certifiedValue) ? probe :
                TrinityRadixDiagnostics.inexact("radix_certified_objective", decoded + "/" + certifiedValue);
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> optimizeDigit(
                                                                            TrinityRadixBuiltModel built,
                                                                            int digit,
                                                                            Map<Integer, Integer> fixedDigits,
                                                                            TrinityPlanningControl control,
                                                                            TrinityRadixSolverMetrics metrics) {
        if (control.cancellationRequested()) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes())));
        }
        if (control.deadlineExceeded()) {
            return TrinityRadixDiagnostics.timeout(metrics, "before_digit_objective", built.objective().name(), digit);
        }
        TrinityRadixLinearEncoder encoder = built.model();
        ExpressionsBasedModel objectiveModel = encoder.model().copy();
        // Every preceding digit is already proven optimal. A directional bound therefore forces the same value
        // mathematically, while keeping ojAlgo from eliminating the carry column through fixed-variable presolve.
        fixedDigits.forEach((fixedDigit, value) -> {
            Variable source = built.objective().digits().get(fixedDigit);
            Variable target = objectiveModel.getVariable(encoder.model().indexOf(source));
            if (built.minimize()) {
                target.upper(value);
            } else {
                target.lower(value);
            }
        });
        Variable objectiveDigit = built.objective().digits().get(digit);
        int objectiveIndex = encoder.model().indexOf(objectiveDigit);
        Variable solverDigit = objectiveModel.getVariable(objectiveIndex);
        int lowerBound;
        int upperBound;
        if (built.minimize()) {
            lowerBound = certifiedMinimumDigit(built, digit, fixedDigits);
            upperBound = decimalInteger(objectiveDigit.getUpperLimit()).intValueExact();
            solverDigit.lower(lowerBound);
        } else {
            lowerBound = decimalInteger(objectiveDigit.getLowerLimit()).intValueExact();
            upperBound = certifiedMaximumDigit(built, digit, fixedDigits);
            solverDigit.upper(upperBound);
        }
        boolean fixedDigit = lowerBound == upperBound;
        if (fixedDigit) {
            solverDigit.level(lowerBound);
        } else {
            solverDigit.weight(built.minimize() ? BigDecimal.ONE : BigDecimal.ONE.negate());
        }
        TrinityAlgorithmResult<Map<Variable, BigInteger>> optimized = solveProbeModel(
                built,
                objectiveModel,
                digit,
                lowerBound + ".." + upperBound,
                control,
                metrics,
                !fixedDigit);
        if (!optimized.successful()) {
            return optimized;
        }
        for (Map.Entry<Integer, Integer> fixed : fixedDigits.entrySet()) {
            Variable prefixDigit = built.objective().digits().get(fixed.getKey());
            if (selectedDigit(optimized.value(), prefixDigit) != fixed.getValue()) {
                return TrinityRadixDiagnostics.inexact(
                        "radix_objective_prefix",
                        fixed.getKey() + ":" + selectedDigit(optimized.value(), prefixDigit));
            }
        }
        int selected = selectedDigit(optimized.value(), objectiveDigit);
        return selected >= lowerBound && selected <= upperBound ? optimized :
                TrinityRadixDiagnostics.inexact("radix_digit_objective_bound", Integer.toString(selected));
    }

    private TrinityAlgorithmResult<Map<Variable, BigInteger>> solveProbeModel(
                                                                              TrinityRadixBuiltModel built,
                                                                              ExpressionsBasedModel probeModel,
                                                                              int digit,
                                                                              String bound,
                                                                              TrinityPlanningControl control,
                                                                              TrinityRadixSolverMetrics metrics,
                                                                              boolean requireOptimal) {
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
        if (requireOptimal && !result.getState().isOptimal()) {
            if (control.deadlineExceeded()) {
                return TrinityRadixDiagnostics.timeout(metrics, result.getState().name(), built.objective().name(), digit);
            }
            return TrinityRadixDiagnostics.inexact("radix_digit_objective_state", result.getState().name());
        }
        TrinityRadixLinearEncoder encoder = built.model();
        return this.resultDecoder.decode(
                probeModel,
                encoder.variables(),
                encoder.columnEquations(),
                result);
    }

    private static int selectedDigit(Map<Variable, BigInteger> values, Variable objectiveDigit) {
        BigInteger selected = values.get(objectiveDigit);
        if (selected == null) {
            throw new IllegalStateException("A Trinity radix digit probe did not decode its objective");
        }
        return selected.intValueExact();
    }

    private static int certifiedMinimumDigit(
                                             TrinityRadixBuiltModel built,
                                             int digit,
                                             Map<Integer, Integer> fixedDigits) {
        BigInteger residual = built.objectiveLowerBound().subtract(
                higherPrefix(built.objective(), digit, fixedDigits));
        BigInteger derived = residual.signum() <= 0 ? BigInteger.ZERO :
                residual.divide(radixPlace(digit));
        BigInteger currentLower = decimalInteger(built.objective().digits().get(digit).getLowerLimit());
        BigInteger candidate = derived.max(currentLower);
        if (candidate.compareTo(decimalInteger(built.objective().digits().get(digit).getUpperLimit())) > 0) {
            throw new TrinityRadixInfeasibleException("objective_minimum_digit");
        }
        return checkedDigit(candidate, "minimum");
    }

    private static int certifiedMaximumDigit(
                                             TrinityRadixBuiltModel built,
                                             int digit,
                                             Map<Integer, Integer> fixedDigits) {
        BigInteger residual = built.objectiveUpperBound().subtract(
                higherPrefix(built.objective(), digit, fixedDigits));
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

    private static BigInteger higherPrefix(
                                           TrinityRadixVariable objective,
                                           int digit,
                                           Map<Integer, Integer> fixedDigits) {
        BigInteger prefix = BigInteger.ZERO;
        for (int index = digit + 1; index < objective.digits().size(); index++) {
            Integer fixed = fixedDigits.get(index);
            if (fixed == null || fixed < 0 || fixed >= TrinityRadixDigits.BASE) {
                throw new IllegalStateException("Higher Trinity radix objective digits must already be fixed");
            }
            prefix = prefix.add(BigInteger.valueOf(fixed).multiply(radixPlace(index)));
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
        model.options.integer(INTEGER_STRATEGY);
        if (!control.deadlineConfigured()) {
            return;
        }
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
