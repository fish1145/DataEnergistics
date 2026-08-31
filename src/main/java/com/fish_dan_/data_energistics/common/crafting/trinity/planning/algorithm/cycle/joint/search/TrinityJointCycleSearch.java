package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut.TrinityExternalPrefixCut;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut.TrinityExternalPrefixPartition;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation.TrinityJointCandidateEvaluation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation.TrinityJointCandidateEvaluator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySession;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticOutcome;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Proves the global executable cycle objective with exact firing boxes and compressed schedule checks.
 * <p>
 * Best-first exact box search. Each box independently supplies optimistic conservation levels, while candidate
 * evaluation supplies the true external and prefix-seed objectives required for sound pruning.
 */
public final class TrinityJointCycleSearch {

    /**
     * @return exact bounded branch-and-bound search
     */
    public static TrinityJointCycleSearch create() {
        return new TrinityJointCycleSearch(
                TrinityCycleFeasibilityModel.create(),
                TrinityJointCandidateEvaluator.create(),
                TrinityExternalPrefixCut.create());
    }

    /**
     * Decodes the mandatory compressed-state count from a scheduler diagnostic.
     */
    public static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A Trinity schedule diagnostic is required");
        }
        String encodedStates = diagnostic.metadata().get("states");
        if (encodedStates == null) {
            throw new IllegalStateException("A Trinity schedule diagnostic must report visited states");
        }
        try {
            int states = Integer.parseInt(encodedStates);
            if (states < 0) {
                throw new IllegalStateException("Trinity schedule diagnostic states cannot be negative");
            }
            return states;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Trinity schedule diagnostic states must be an integer", exception);
        }
    }

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String MIP_TIMEOUT_KEY = "gui.data_energistics.trinity_planning.mip.timeout";
    private static final String NO_ORDER_KEY = "gui.data_energistics.trinity_planning.mip.no_executable_order";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.mip.schedule_search_limit";
    private static final String INSUFFICIENT_INPUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.insufficient_input";

    private final TrinityCycleFeasibilityModel feasibilityModel;
    private final TrinityJointCandidateEvaluator candidateEvaluator;
    private final TrinityExternalPrefixCut externalPrefixCut;

    TrinityJointCycleSearch(
                            TrinityCycleFeasibilityModel feasibilityModel,
                            TrinityJointCandidateEvaluator candidateEvaluator,
                            TrinityExternalPrefixCut externalPrefixCut) {
        if (feasibilityModel == null || candidateEvaluator == null || externalPrefixCut == null) {
            throw new IllegalArgumentException("A Trinity joint search requires feasibility, evaluation and cuts");
        }
        this.feasibilityModel = feasibilityModel;
        this.candidateEvaluator = candidateEvaluator;
        this.externalPrefixCut = externalPrefixCut;
    }

    /**
     * Searches every firing box that can improve the executable incumbent until optimality is proved or a shared
     * bound terminates the search.
     */
    public TrinityAlgorithmResult<TrinityJointCyclePlan> search(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> available,
                                                                Set<AEKey> producibleInputs,
                                                                int maxSearchStates,
                                                                TrinityPlanningMode mode,
                                                                TrinityPlanningControl control) {
        if (component == null || !component.cyclic() || component.cycleVariants().isEmpty() || demand == null ||
                available == null || producibleInputs == null || maxSearchStates <= 0 || mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity joint search request is incomplete");
        }
        return search(
                component,
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                mode,
                control,
                TrinityMipCoefficientTemplate.create(
                        component.cycleVariants(),
                        new ObjectArrayList<>(component.keys())));
    }

    /** Searches with a semantic coefficient template supplied by the compiled component cache. */
    public TrinityAlgorithmResult<TrinityJointCyclePlan> search(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> available,
                                                                Set<AEKey> producibleInputs,
                                                                int maxSearchStates,
                                                                TrinityPlanningMode mode,
                                                                TrinityPlanningControl control,
                                                                TrinityMipCoefficientTemplate coefficientTemplate) {
        return new SearchSession(
                component.index(),
                coefficientTemplate.variants(),
                coefficientTemplate.internalKeys(),
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                mode,
                control,
                coefficientTemplate).search();
    }

    /**
     * Compatibility entry point that retains complete optimisation.
     */
    public TrinityAlgorithmResult<TrinityJointCyclePlan> search(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> available,
                                                                Set<AEKey> producibleInputs,
                                                                int maxSearchStates,
                                                                TrinityPlanningControl control) {
        return search(
                component,
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                TrinityPlanningMode.OPTIMAL,
                control);
    }

    private final class SearchSession {

        private final int componentIndex;
        private final List<TrinityPatternVariant> variants;
        private final Set<AEKey> internalKeys;
        private final TrinityCycleDemand demand;
        private final Map<AEKey, BigInteger> available;
        private final Set<AEKey> producibleInputs;
        private final SearchBudget budget;
        private final TrinityPlanningMode mode;
        private final TrinityPlanningControl control;
        private final TrinityMipCoefficientTemplate coefficientTemplate;
        private final TrinityCycleFeasibilitySession feasibilitySession;
        private final SolverMetrics metrics = new SolverMetrics();
        private final Set<FeasibilityKey> infeasibleBoxes = new LinkedHashSet<>();
        private @Nullable TrinityJointCyclePlan incumbent;
        private @Nullable TrinityLexicographicObjective incumbentObjective;
        private long sequence;

        private SearchSession(
                              int componentIndex,
                              List<TrinityPatternVariant> variants,
                              Set<AEKey> internalKeys,
                              TrinityCycleDemand demand,
                              Map<AEKey, BigInteger> available,
                              Set<AEKey> producibleInputs,
                              int maxSearchStates,
                              TrinityPlanningMode mode,
                              TrinityPlanningControl control,
                              TrinityMipCoefficientTemplate coefficientTemplate) {
            if (componentIndex < 0) {
                throw new IllegalArgumentException("A Trinity joint search component index cannot be negative");
            }
            this.componentIndex = componentIndex;
            this.variants = variants;
            this.internalKeys = internalKeys;
            this.demand = demand;
            this.available = available;
            this.producibleInputs = producibleInputs;
            this.budget = new SearchBudget(maxSearchStates, control);
            this.mode = mode;
            this.control = control;
            this.coefficientTemplate = coefficientTemplate;
            this.feasibilitySession = feasibilityModel.openSession(request(TrinityFiringBox.full(this.variants)));
        }

        private TrinityAlgorithmResult<TrinityJointCyclePlan> search() {
            TrinityFiringBox rootBox = TrinityFiringBox.full(this.variants);
            if (!this.budget.consume(1)) {
                return searchLimit();
            }
            TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> rootSolved = this.feasibilitySession.solve(
                    request(rootBox),
                    this.mode,
                    this.control);
            if (!rootSolved.successful()) {
                if (rootSolved.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION &&
                        this.incumbent == null) {
                    return diagnoseRootShortage(rootBox, rootSolved.diagnostic());
                }
                return failed(rootSolved.diagnostic());
            }
            this.metrics.add(rootSolved.value());

            PriorityQueue<SearchNode> pending = new PriorityQueue<>(Comparator
                    .comparing(SearchNode::lowerBound)
                    .thenComparingLong(SearchNode::sequence));
            pending.add(node(rootBox, rootSolved.value(), Optional.empty()));
            while (!pending.isEmpty()) {
                TrinityAlgorithmResult<TrinityJointCyclePlan> interrupted = interruption();
                if (interrupted != null) {
                    return interrupted;
                }
                SearchNode current = pending.remove();
                if (!current.lowerBound().canImprove(this.incumbentObjective)) {
                    continue;
                }
                TrinityAlgorithmResult<Boolean> leadingCut = applyExternalCut(current, pending);
                if (!leadingCut.successful()) {
                    if (recoverableStop(leadingCut.diagnostic()) && this.incumbent != null) {
                        return completedIncumbent();
                    }
                    return failed(leadingCut.diagnostic());
                }
                if (leadingCut.value()) {
                    continue;
                }
                if (this.budget.remaining() <= 0) {
                    return completedOrSearchLimit();
                }

                TrinityAlgorithmResult<TrinityJointCandidateEvaluation> evaluated = candidateEvaluator.evaluate(
                        this.variants,
                        this.internalKeys,
                        this.demand,
                        this.available,
                        this.producibleInputs,
                        current.solution(),
                        this.budget.remaining(),
                        this.metrics.passes,
                        this.metrics.nanos,
                        this.control);
                if (evaluated.successful()) {
                    TrinityJointCandidateEvaluation candidate = evaluated.value();
                    if (!this.budget.consume(candidate.statesVisited())) {
                        return completedOrSearchLimit();
                    }
                    if (this.incumbentObjective == null ||
                            candidate.objective().compareTo(this.incumbentObjective) < 0) {
                        this.incumbent = candidate.plan();
                        this.incumbentObjective = candidate.objective();
                    }
                    if (this.mode == TrinityPlanningMode.FIRST_FEASIBLE ||
                            current.solution().quality() == TrinityPlanQuality.VERIFIED_FEASIBLE) {
                        return completedIncumbent();
                    }
                    TrinityAlgorithmResult<Boolean> candidateCut = applyExternalCut(current, pending);
                    if (!candidateCut.successful()) {
                        if (recoverableStop(candidateCut.diagnostic()) && this.incumbent != null) {
                            return completedIncumbent();
                        }
                        return failed(candidateCut.diagnostic());
                    }
                    if (candidateCut.value()) {
                        continue;
                    }
                    if (current.lowerBound().provenBy(candidate.objective())) {
                        continue;
                    }
                } else if (evaluated.diagnostic().code() == TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER) {
                    if (!this.budget.consume(TrinityJointCycleSearch.diagnosticStates(evaluated.diagnostic()))) {
                        return completedOrSearchLimit();
                    }
                } else if (evaluated.diagnostic().code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT) {
                    this.budget.consume(TrinityJointCycleSearch.diagnosticStates(evaluated.diagnostic()));
                    return completedOrSearchLimit();
                } else {
                    return failed(evaluated.diagnostic());
                }

                for (TrinityFiringBox child : current.box().excluding(current.solution().firings())) {
                    TrinityAlgorithmResult<Optional<SearchNode>> childSolved = solveChild(
                            child,
                            current.fixedExternalLevel());
                    if (!childSolved.successful()) {
                        TrinityPlanningDiagnosticCode code = childSolved.diagnostic().code();
                        if ((code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT ||
                                code == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT) && this.incumbent != null) {
                            return completedIncumbent();
                        }
                        return failed(childSolved.diagnostic());
                    }
                    childSolved.value()
                            .filter(next -> next.lowerBound().canImprove(this.incumbentObjective))
                            .ifPresent(pending::add);
                }
            }
            if (this.incumbent == null) {
                return failure(
                        TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                        NO_ORDER_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            return TrinityAlgorithmResult.success(withFinalMetrics(
                    this.incumbent,
                    this.budget.used,
                    this.metrics,
                    TrinityPlanQuality.PROVED_OPTIMAL));
        }

        private TrinityAlgorithmResult<Boolean> applyExternalCut(
                                                                 SearchNode current,
                                                                 PriorityQueue<SearchNode> pending) {
            if (this.incumbentObjective == null || this.incumbentObjective.externalInput().signum() == 0 ||
                    current.lowerBound().externalInput()
                            .compareTo(this.incumbentObjective.externalInput()) >= 0) {
                return TrinityAlgorithmResult.success(false);
            }
            BigInteger cap = this.incumbentObjective.externalInput().subtract(BigInteger.ONE);
            Optional<TrinityExternalPrefixPartition> partition = externalPrefixCut.partition(
                    current.box(),
                    this.internalKeys,
                    cap);
            if (partition.isEmpty()) {
                return TrinityAlgorithmResult.success(false);
            }

            TrinityExternalPrefixPartition cut = partition.orElseThrow();
            if (cut.withinCap().isPresent()) {
                TrinityAlgorithmResult<Optional<SearchNode>> within = solveChild(
                        cut.withinCap().orElseThrow(),
                        Optional.empty());
                if (!within.successful()) {
                    return TrinityAlgorithmResult.failure(within.diagnostic());
                }
                within.value()
                        .filter(next -> next.lowerBound().canImprove(this.incumbentObjective))
                        .ifPresent(pending::add);
            }
            for (TrinityFiringBox aboveCap : cut.aboveCap()) {
                TrinityAlgorithmResult<Optional<SearchNode>> above = solveChild(
                        aboveCap,
                        Optional.of(this.incumbentObjective.externalInput()));
                if (!above.successful()) {
                    return TrinityAlgorithmResult.failure(above.diagnostic());
                }
                above.value()
                        .filter(next -> next.lowerBound().canImprove(this.incumbentObjective))
                        .ifPresent(pending::add);
            }
            return TrinityAlgorithmResult.success(true);
        }

        private TrinityAlgorithmResult<Optional<SearchNode>> solveChild(
                                                                        TrinityFiringBox box,
                                                                        Optional<BigInteger> fixedExternalLevel) {
            FeasibilityKey key = new FeasibilityKey(box, fixedExternalLevel);
            if (this.infeasibleBoxes.contains(key)) {
                return TrinityAlgorithmResult.success(Optional.empty());
            }
            TrinityAlgorithmResult<TrinityJointCyclePlan> interrupted = interruption();
            if (interrupted != null) {
                if (interrupted.successful()) {
                    return failure(
                            TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                            MIP_TIMEOUT_KEY,
                            Map.of("states", Integer.toString(this.budget.used)));
                }
                return TrinityAlgorithmResult.failure(interrupted.diagnostic());
            }
            if (!this.budget.consume(1)) {
                return searchLimit();
            }
            TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solved = this.feasibilitySession.solve(
                    request(box, fixedExternalLevel),
                    this.mode,
                    this.control);
            if (!solved.successful()) {
                if (solved.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    this.infeasibleBoxes.add(key);
                    return TrinityAlgorithmResult.success(Optional.empty());
                }
                return failed(solved.diagnostic());
            }
            this.metrics.add(solved.value());
            return TrinityAlgorithmResult.success(Optional.of(node(box, solved.value(), fixedExternalLevel)));
        }

        private TrinityCycleFeasibilityRequest request(TrinityFiringBox box) {
            return request(box, Optional.empty());
        }

        private TrinityCycleFeasibilityRequest request(
                                                       TrinityFiringBox box,
                                                       Optional<BigInteger> fixedExternalLevel) {
            return new TrinityCycleFeasibilityRequest(
                    this.variants,
                    this.internalKeys,
                    this.demand,
                    this.available,
                    this.producibleInputs,
                    box.asMap(),
                    fixedExternalLevel,
                    BigInteger.ZERO,
                    box.totalLowerBound(),
                    false,
                    0,
                    this.coefficientTemplate);
        }

        private SearchNode node(
                                TrinityFiringBox box,
                                TrinityCycleFeasibilitySolution solution,
                                Optional<BigInteger> fixedExternalLevel) {
            return new SearchNode(
                    box,
                    solution,
                    TrinityJointSearchLowerBound.from(this.variants, solution),
                    fixedExternalLevel,
                    this.sequence++);
        }

        private @Nullable TrinityAlgorithmResult<TrinityJointCyclePlan> interruption() {
            if (this.control.cancellationRequested()) {
                return failed(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            if (this.control.deadlineExceeded()) {
                if (this.incumbent != null) {
                    return completedIncumbent();
                }
                return failed(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        MIP_TIMEOUT_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            return null;
        }

        private <T> TrinityAlgorithmResult<T> searchLimit() {
            return failed(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of(
                            "limit", Integer.toString(this.budget.limit),
                            "states", Integer.toString(this.budget.used)));
        }

        private TrinityAlgorithmResult<TrinityJointCyclePlan> completedOrSearchLimit() {
            return this.incumbent == null ? searchLimit() : completedIncumbent();
        }

        private TrinityAlgorithmResult<TrinityJointCyclePlan> completedIncumbent() {
            return TrinityAlgorithmResult.success(withFinalMetrics(
                    this.incumbent,
                    this.budget.used,
                    this.metrics,
                    TrinityPlanQuality.VERIFIED_FEASIBLE));
        }

        private boolean recoverableStop(TrinityPlanningDiagnostic diagnostic) {
            TrinityPlanningDiagnosticCode code = diagnostic.code();
            return code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT ||
                    code == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT;
        }

        private <T> TrinityAlgorithmResult<T> failed(
                                                     TrinityPlanningDiagnosticCode code,
                                                     String translationKey,
                                                     Map<String, String> metadata) {
            return failed(new TrinityPlanningDiagnostic(
                    code,
                    Component.translatable(translationKey),
                    metadata));
        }

        private <T> TrinityAlgorithmResult<T> failed(TrinityPlanningDiagnostic diagnostic) {
            if (this.incumbent == null || diagnostic.inputShortage().isPresent()) {
                return TrinityAlgorithmResult.failure(diagnostic);
            }
            return TrinityAlgorithmResult.failure(diagnostic.withDetail(incumbentProgress()));
        }

        private TrinityAlgorithmResult<TrinityJointCyclePlan> diagnoseRootShortage(
                                                                                   TrinityFiringBox rootBox,
                                                                                   TrinityPlanningDiagnostic rootFailure) {
            if (this.control.cancellationRequested()) {
                return failed(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            if (this.control.deadlineExceeded()) {
                return failed(withShortageStop(rootFailure, "timeout", 0, null));
            }
            int remainingStates = this.budget.remaining();
            if (remainingStates <= 0) {
                return failed(withShortageStop(rootFailure, "state_limit", 0, null));
            }
            TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> diagnosed = feasibilityModel.solve(
                    request(rootBox).forShortageDiagnosis(remainingStates),
                    TrinityPlanningMode.OPTIMAL,
                    this.control);
            int diagnosisStates = diagnosed.successful() ?
                    diagnosed.value().diagnosticStates() : diagnosisStates(diagnosed.diagnostic());
            if (diagnosisStates > 0 && !this.budget.consume(diagnosisStates)) {
                throw new IllegalStateException("A bounded Trinity shortage diagnosis exceeded its reserved states");
            }
            if (this.control.cancellationRequested() ||
                    (!diagnosed.successful() && diagnosed.diagnostic().code() ==
                            TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED)) {
                return failed(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            if (!diagnosed.successful()) {
                return failed(withShortageStop(
                        rootFailure,
                        shortageStop(diagnosed.diagnostic()),
                        diagnosisStates,
                        diagnosed.diagnostic()));
            }
            TrinityCycleFeasibilitySolution solution = diagnosed.value();
            if (solution.quality() != TrinityPlanQuality.PROVED_OPTIMAL) {
                return failed(withShortageStop(rootFailure, "unproved", diagnosisStates, null));
            }
            if (this.control.cancellationRequested()) {
                return failed(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            if (solution.missingInputs().isEmpty()) {
                return failed(withShortageStop(rootFailure, "missing_zero", diagnosisStates, null));
            }
            if (this.control.cancellationRequested()) {
                return failed(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(this.budget.used)));
            }
            TrinityCycleDiagnosticOutcome outcome = null;
            String proofStop = null;
            TrinityPlanningDiagnostic proofFailure = null;
            if (this.control.deadlineExceeded()) {
                proofStop = "timeout";
            } else if (this.budget.remaining() <= 0) {
                proofStop = "state_limit";
            } else {
                LinkedHashMap<AEKey, BigInteger> diagnosticAvailable = new LinkedHashMap<>(this.available);
                solution.missingInputs().forEach(
                        (key, amount) -> diagnosticAvailable.merge(key, amount, BigInteger::add));
                TrinityAlgorithmResult<TrinityJointCandidateEvaluation> evaluated = candidateEvaluator.evaluate(
                        this.variants,
                        this.internalKeys,
                        this.demand,
                        Collections.unmodifiableMap(diagnosticAvailable),
                        this.producibleInputs,
                        solution,
                        this.budget.remaining(),
                        solution.solverPasses(),
                        solution.solverNanos(),
                        this.control);
                if (evaluated.successful()) {
                    TrinityJointCandidateEvaluation evaluation = evaluated.value();
                    if (!this.budget.consume(evaluation.statesVisited())) {
                        throw new IllegalStateException(
                                "A bounded Trinity diagnostic schedule exceeded its reserved states");
                    }
                    if (this.control.cancellationRequested()) {
                        return failed(
                                TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                                CANCELLED_KEY,
                                Map.of("states", Integer.toString(this.budget.used)));
                    }
                    TrinityJointCyclePlan plan = evaluation.plan();
                    TrinityCycleDiagnosticEvidence evidence = TrinityCycleDiagnosticEvidence.fromJointPlan(
                            this.componentIndex,
                            this.demand,
                            plan);
                    TrinityCycleDiagnosticOutcome scheduledOutcome = TrinityCycleDiagnosticOutcome.create(
                            evidence,
                            this.available,
                            this.producibleInputs);
                    if (scheduledOutcome.inputRequirements().isEmpty()) {
                        proofStop = "scheduled_missing_zero";
                    } else {
                        outcome = scheduledOutcome;
                    }
                } else {
                    proofFailure = evaluated.diagnostic();
                    int scheduleStates = diagnosisStates(proofFailure);
                    if (scheduleStates > 0 && !this.budget.consume(scheduleStates)) {
                        throw new IllegalStateException(
                                "A bounded Trinity diagnostic schedule exceeded its reserved states");
                    }
                    if (this.control.cancellationRequested() ||
                            proofFailure.code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED) {
                        return failed(
                                TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                                CANCELLED_KEY,
                                Map.of("states", Integer.toString(this.budget.used)));
                    }
                    proofStop = shortageStop(proofFailure);
                }
            }
            return TrinityAlgorithmResult.failure(shortageDiagnostic(
                    solution,
                    outcome,
                    proofStop,
                    proofFailure));
        }

        private TrinityPlanningDiagnostic shortageDiagnostic(
                                                             TrinityCycleFeasibilitySolution solution,
                                                             @Nullable TrinityCycleDiagnosticOutcome outcome,
                                                             @Nullable String proofStop,
                                                             @Nullable TrinityPlanningDiagnostic proofFailure) {
            Map<AEKey, BigInteger> requiredInputs = solution.requiredInputs();
            LinkedHashMap<AEKey, InputRequirement> baseRequirements = new LinkedHashMap<>();
            solution.missingInputs().forEach((key, missing) -> {
                BigInteger required = requiredInputs.getOrDefault(key, BigInteger.ZERO);
                BigInteger available = this.available.getOrDefault(key, BigInteger.ZERO);
                baseRequirements.put(key, new InputRequirement(required, available, missing));
            });
            LinkedHashMap<AEKey, InputRequirement> requirements;
            TrinityPlanningDiagnostic.PartialPlan materials;
            if (outcome == null) {
                LinkedHashMap<AEKey, BigInteger> emitted = new LinkedHashMap<>();
                solution.firings().forEach((variant, count) -> variant.outputs().forEach(
                        (key, amount) -> emitted.merge(key, amount.multiply(count), BigInteger::add)));
                materials = new TrinityPlanningDiagnostic.PartialPlan(
                        solution.actualInputs(),
                        emitted,
                        solution.missingInputs(),
                        baseRequirements);
                requirements = baseRequirements;
            } else {
                materials = outcome.materials();
                requirements = new LinkedHashMap<>(materials.inputRequirements());
            }
            Map.Entry<AEKey, InputRequirement> first = requirements.entrySet().iterator().next();
            LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
            metadata.put("available", first.getValue().available().toString());
            metadata.put("key", first.getKey().toString());
            metadata.put("missing", first.getValue().missing().toString());
            metadata.put("required", first.getValue().required().toString());
            metadata.put("shortageDiagnosisModel", solution.radix() ? "radix" : "ordinary");
            metadata.put("shortageDiagnosisStates", Integer.toString(solution.diagnosticStates()));
            metadata.put("shortageMipNanos", Long.toString(solution.solverNanos()));
            metadata.put("shortageSolverPasses", Integer.toString(solution.solverPasses()));
            metadata.put("shortageKinds", Integer.toString(requirements.size()));
            metadata.put("diagnosticProvedCycles", outcome == null ? "0" : "1");
            if (outcome != null) {
                metadata.put(
                        "diagnosticCycleProofStates",
                        Integer.toString(outcome.evidence().scheduleStates()));
            }
            if (proofStop != null) {
                metadata.put("diagnosticCycleProofStop", proofStop);
            }
            if (proofFailure != null) {
                proofFailure.metadata().forEach((key, value) -> metadata.put("cycleProof." + key, value));
            }
            TrinityPlanningDiagnostic.Detail detail = outcome == null ?
                    materials :
                    new TrinityPlanningDiagnostic.CompositeEvidence(
                            materials,
                            List.of(outcome.evidence()));
            return new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    Component.translatable(INSUFFICIENT_INPUT_KEY),
                    metadata,
                    detail);
        }

        private TrinityPlanningDiagnostic withShortageStop(
                                                           TrinityPlanningDiagnostic rootFailure,
                                                           String stop,
                                                           int diagnosisStates,
                                                           @Nullable TrinityPlanningDiagnostic diagnosisFailure) {
            LinkedHashMap<String, String> metadata = new LinkedHashMap<>(rootFailure.metadata());
            metadata.put("shortageDiagnosisStates", Integer.toString(diagnosisStates));
            metadata.put("shortageDiagnosisStop", stop);
            if (diagnosisFailure != null) {
                diagnosisFailure.metadata().forEach((key, value) -> metadata.put("shortage." + key, value));
            }
            return new TrinityPlanningDiagnostic(
                    rootFailure.code(),
                    rootFailure.message(),
                    metadata,
                    rootFailure.detail());
        }

        private static int diagnosisStates(TrinityPlanningDiagnostic diagnostic) {
            String encoded = diagnostic.metadata().get("states");
            if (encoded == null) {
                return 0;
            }
            try {
                int states = Integer.parseInt(encoded);
                if (states < 0) {
                    throw new IllegalStateException("Trinity shortage diagnostic states cannot be negative");
                }
                return states;
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Trinity shortage diagnostic states must be an integer", exception);
            }
        }

        private static String shortageStop(TrinityPlanningDiagnostic diagnostic) {
            return switch (diagnostic.code()) {
                case ORDER_SEARCH_LIMIT -> "state_limit";
                case MIP_TIMEOUT -> "timeout";
                case MIP_NO_INTEGER_SOLUTION -> "relaxed_infeasible";
                default -> diagnostic.code().name();
            };
        }

        private TrinityPlanningDiagnostic.PartialPlan incumbentProgress() {
            LinkedHashMap<AEKey, BigInteger> used = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> missing = new LinkedHashMap<>();
            this.incumbent.initialInputs().forEach((key, required) -> {
                BigInteger stored = required.min(this.available.getOrDefault(key, BigInteger.ZERO));
                if (stored.signum() > 0) {
                    used.put(key, stored);
                }
                BigInteger shortage = required.subtract(stored);
                if (shortage.signum() > 0) {
                    missing.put(key, shortage);
                }
            });

            LinkedHashMap<AEKey, BigInteger> emitted = new LinkedHashMap<>();
            this.incumbent.firings().forEach((variant, count) -> variant.outputs().forEach(
                    (key, amount) -> emitted.merge(key, amount.multiply(count), BigInteger::add)));
            return new TrinityPlanningDiagnostic.PartialPlan(used, emitted, missing);
        }
    }

    private static TrinityJointCyclePlan withFinalMetrics(
                                                          TrinityJointCyclePlan plan,
                                                          int searchStates,
                                                          SolverMetrics metrics,
                                                          TrinityPlanQuality quality) {
        return new TrinityJointCyclePlan(
                plan.firings(),
                plan.externalInputs(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.schedule(),
                searchStates,
                metrics.passes,
                metrics.nanos,
                quality);
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String translationKey,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    private record SearchNode(
                              TrinityFiringBox box,
                              TrinityCycleFeasibilitySolution solution,
                              TrinityJointSearchLowerBound lowerBound,
                              Optional<BigInteger> fixedExternalLevel,
                              long sequence) {}

    private record FeasibilityKey(
                                  TrinityFiringBox box,
                                  Optional<BigInteger> fixedExternalLevel) {}

    private static final class SearchBudget {

        private final int limit;
        private final TrinityPlanningControl control;
        private int used;

        private SearchBudget(int limit, TrinityPlanningControl control) {
            this.limit = limit;
            this.control = control;
        }

        private boolean consume(int states) {
            if (states < 0) {
                throw new IllegalArgumentException("Trinity search states cannot be negative");
            }
            if (states > this.limit - this.used) {
                this.control.recordJointStates(this.limit - this.used);
                this.used = this.limit;
                return false;
            }
            this.used = Math.addExact(this.used, states);
            this.control.recordJointStates(states);
            return true;
        }

        private int remaining() {
            return this.limit - this.used;
        }
    }

    private static final class SolverMetrics {

        private int passes;
        private long nanos;

        private void add(TrinityCycleFeasibilitySolution solution) {
            this.passes = Math.addExact(this.passes, solution.solverPasses());
            this.nanos = Math.addExact(this.nanos, solution.solverNanos());
        }
    }
}
