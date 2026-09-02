package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleSolveBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityShortageInputAllocation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixResultDecoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixBuiltModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixInfeasibleException;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixModelAssembler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixModelLimitException;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixModelPass;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixSolvedModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search.TrinityRadixDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search.TrinityRadixObjectiveSearch;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search.TrinityRadixSolverMetrics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

/**
 * Coordinates sequential exact radix objectives, request-derived finite model envelopes, and BigInteger verification.
 */
public final class TrinityRadixCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    private static final int MAX_FEASIBILITY_DOMAINS = 8;

    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityCycleObjectiveBounds exactBounds;
    private final TrinityRadixModelAssembler modelAssembler;
    private final TrinityRadixObjectiveSearch objectiveSearch;

    /**
     * Creates the exact large-number backend used by the precision-selecting feasibility boundary.
     */
    public TrinityRadixCycleFeasibilityModel(
                                             TrinityIntegerResultVerifier integerVerifier,
                                             TrinityExactConservationVerifier conservationVerifier) {
        TrinityRadixCodec codec = TrinityRadixCodec.create();
        this.conservationVerifier = conservationVerifier;
        this.exactBounds = TrinityCycleObjectiveBounds.create();
        this.modelAssembler = TrinityRadixModelAssembler.create(codec, this.exactBounds);
        this.objectiveSearch = TrinityRadixObjectiveSearch.create(
                codec,
                new TrinityRadixResultDecoder(integerVerifier));
    }

    @Override
    public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                         TrinityCycleFeasibilityRequest request,
                                                                         TrinityPlanningMode mode,
                                                                         TrinityPlanningControl control) {
        if (request.shortageDiagnostic()) {
            return solveShortage(request, control, this.exactBounds.compactFiringUpper(request));
        }
        TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> feasible = solveFirstFeasible(request, control);
        if (!feasible.successful() || mode == TrinityPlanningMode.FIRST_FEASIBLE) {
            return feasible;
        }
        return solveFullDomain(request, control, feasible.value());
    }

    /**
     * Finds any exact shortage candidate within request-local finite domains. The caller owns the remaining
     * solver budget; a failed finite domain does not prove that the original open system is infeasible.
     *
     * @param request            diagnostic request with a positive remaining state limit
     * @param control            shared cancellation and deadline boundary
     * @param initialFiringUpper first untried positive domain, including prior ordinary expansion
     * @return a verified candidate or a typed incomplete diagnostic, never an optimality claim
     */
    public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveShortage(
                                                                                 TrinityCycleFeasibilityRequest request, TrinityPlanningControl control, BigInteger initialFiringUpper) {
        TrinityRadixSolverMetrics metrics = new TrinityRadixSolverMetrics();
        TrinityCycleSolveBudget stateBudget = TrinityCycleSolveBudget.limited(request.shortageStateLimit());
        BigInteger logicalUpper = initialFiringUpper;
        try {
            for (int domain = 0; domain < MAX_FEASIBILITY_DOMAINS; domain++) {
                if (control.cancellationRequested()) {
                    return shortageFailure(TrinityRadixDiagnostics.failure(
                            TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                            "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                            Map.of("phase", "shortage_feasibility")).diagnostic(), stateBudget, metrics);
                }
                if (control.deadlineExceeded()) {
                    return shortageFailure(TrinityRadixDiagnostics.timeout(
                            metrics, "shortage_feasibility", "feasibility", -1).diagnostic(), stateBudget, metrics);
                }
                if (stateBudget.used() >= stateBudget.limit()) break;
                try {
                    control.recordSolverModel();
                    TrinityRadixModelPass pass = TrinityRadixModelPass.Feasibility.INSTANCE;
                    TrinityRadixBuiltModel built = this.modelAssembler.assemble(request, pass, logicalUpper);
                    TrinityAlgorithmResult<Map<Variable, BigInteger>> candidate = this.objectiveSearch.findFeasible(
                            built, control, metrics, stateBudget);
                    if (candidate.successful()) {
                        TrinityRadixSolvedModel solved = built.decode(candidate.value());
                        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved);
                        if (!exact.successful()) return shortageFailure(exact.diagnostic(), stateBudget, metrics);
                        TrinityShortageInputAllocation allocation = TrinityShortageInputAllocation.from(
                                request, solved.modelSeed(), solved.externalInputs());
                        return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                                solved.firings(), solved.modelSeed(), solved.externalInputs(),
                                metrics.passes(), metrics.nanos(), true, TrinityPlanQuality.VERIFIED_FEASIBLE,
                                allocation.actualInputs(), allocation.missingInputs(), stateBudget.used()));
                    }
                    if (candidate.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                        return shortageFailure(candidate.diagnostic(), stateBudget, metrics);
                    }
                } catch (TrinityRadixInfeasibleException finiteDomain) {
                    // A fixed/lower value can exceed this temporary domain before any solver call.
                    // Retain no negative conclusion about the open request; expand within the same deadline/budget.
                    Data_Energistics.LOGGER.debug("Trinity shortage domain {} cannot represent constraint {}",
                            logicalUpper, finiteDomain.getMessage());
                }
                logicalUpper = logicalUpper.shiftLeft(1);
            }
            return shortageFailure(TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.schedule_search_limit",
                    Map.of("phase", stateBudget.used() >= stateBudget.limit() ? "shortage_state_limit" : "shortage_domain",
                            "domainUpper", logicalUpper.toString()))
                    .diagnostic(), stateBudget, metrics);
        } catch (TrinityRadixModelLimitException exception) {
            return shortageFailure(TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.radix_model_limit",
                    exception.metadata()).diagnostic(), stateBudget, metrics);
        }
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> shortageFailure(
                                                                                           TrinityPlanningDiagnostic diagnostic,
                                                                                           TrinityCycleSolveBudget stateBudget,
                                                                                           TrinityRadixSolverMetrics metrics) {
        Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>(diagnostic.metadata());
        metadata.put("limit", Integer.toString(stateBudget.limit()));
        metadata.put("states", Integer.toString(stateBudget.used()));
        metadata.put("passes", Integer.toString(metrics.passes()));
        metadata.put("nanos", Long.toString(metrics.nanos()));
        metadata.putIfAbsent("phase", "shortage_feasibility");
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                diagnostic.code(),
                diagnostic.message(),
                metadata,
                diagnostic.detail()));
    }

    private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveFirstFeasible(
                                                                                       TrinityCycleFeasibilityRequest request,
                                                                                       TrinityPlanningControl control) {
        TrinityRadixSolverMetrics metrics = new TrinityRadixSolverMetrics();
        TrinityCycleSolveBudget stateBudget = TrinityCycleSolveBudget.limited(MAX_FEASIBILITY_DOMAINS);
        BigInteger logicalUpper = this.exactBounds.compactFiringUpper(request);
        BigInteger proofUpper = null;
        try {
            while (true) {
                TrinityRadixModelPass pass = TrinityRadixModelPass.External.INSTANCE;
                control.recordSolverModel();
                TrinityRadixBuiltModel built = this.modelAssembler.assemble(request, pass, logicalUpper);
                TrinityAlgorithmResult<Map<Variable, BigInteger>> witness = this.objectiveSearch.findFeasible(
                        built,
                        control,
                        metrics,
                        stateBudget);
                if (witness.successful()) {
                    TrinityRadixSolvedModel solved = built.decode(witness.value());
                    TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved);
                    if (!exact.successful()) {
                        return TrinityAlgorithmResult.failure(exact.diagnostic());
                    }
                    return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                            solved.firings(),
                            solved.modelSeed(),
                            solved.externalInputs(),
                            metrics.passes(),
                            metrics.nanos(),
                            true,
                            TrinityPlanQuality.VERIFIED_FEASIBLE));
                }
                if (witness.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return TrinityAlgorithmResult.failure(witness.diagnostic());
                }
                if (control.deadlineExceeded()) {
                    return TrinityRadixDiagnostics.timeout(
                            metrics,
                            "feasibility_domain",
                            built.objective().name(),
                            -1);
                }
                if (proofUpper == null) {
                    proofUpper = completeLogicalUpper(request, pass, control);
                }
                if (logicalUpper.compareTo(proofUpper) >= 0) {
                    return TrinityAlgorithmResult.failure(witness.diagnostic());
                }
                logicalUpper = expandLogicalUpper(logicalUpper, proofUpper);
            }
        } catch (TrinityRadixModelLimitException exception) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.radix_model_limit",
                    exception.metadata());
        } catch (TrinityRadixInfeasibleException exception) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution",
                    Map.of("constraint", exception.getMessage()));
        }
    }

    private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveFullDomain(
                                                                                    TrinityCycleFeasibilityRequest request,
                                                                                    TrinityPlanningControl control,
                                                                                    TrinityCycleFeasibilitySolution incumbent) {
        TrinityRadixSolverMetrics metrics = new TrinityRadixSolverMetrics();
        metrics.include(incumbent.solverPasses(), incumbent.solverNanos());
        BigInteger logicalUpper = completeLogicalUpper(
                request,
                TrinityRadixModelPass.External.INSTANCE,
                control);
        TrinityAlgorithmResult<TrinityRadixSolvedModel> external = optimize(
                request,
                TrinityRadixModelPass.External.INSTANCE,
                logicalUpper,
                control,
                metrics);
        if (!external.successful()) {
            if (recoverableStop(external.diagnostic())) {
                return TrinityAlgorithmResult.success(incumbent);
            }
            return TrinityAlgorithmResult.failure(external.diagnostic());
        }
        BigInteger optimalExternal = total(external.value().externalInputs());
        BigInteger seedLower = request.seedLowerBound();
        BigInteger firingLower = request.firingLowerBound();
        while (true) {
            TrinityAlgorithmResult<TrinityRadixSolvedModel> seed = optimize(
                    request,
                    new TrinityRadixModelPass.Seed(optimalExternal, seedLower),
                    logicalUpper,
                    control,
                    metrics);
            if (!seed.successful()) {
                if (recoverableStop(seed.diagnostic())) {
                    return feasibleSolution(external.value(), metrics);
                }
                return TrinityAlgorithmResult.failure(seed.diagnostic());
            }
            BigInteger optimalSeed = total(seed.value().modelSeed());
            TrinityAlgorithmResult<Optional<TrinityCycleFeasibilitySolution>> completed = completeSeedWitness(
                    request,
                    optimalExternal,
                    seed.value(),
                    firingLower,
                    control,
                    metrics);
            if (!completed.successful()) {
                if (recoverableStop(completed.diagnostic())) {
                    return feasibleSolution(seed.value(), metrics);
                }
                return TrinityAlgorithmResult.failure(completed.diagnostic());
            }
            if (completed.value().isPresent()) {
                return TrinityAlgorithmResult.success(completed.value().orElseThrow());
            }
            seedLower = optimalSeed.add(BigInteger.ONE);
            firingLower = BigInteger.ZERO;
        }
    }

    private TrinityAlgorithmResult<Optional<TrinityCycleFeasibilitySolution>> completeSeedWitness(
                                                                                                  TrinityCycleFeasibilityRequest request,
                                                                                                  BigInteger optimalExternal,
                                                                                                  TrinityRadixSolvedModel seed,
                                                                                                  BigInteger firingLower,
                                                                                                  TrinityPlanningControl control,
                                                                                                  TrinityRadixSolverMetrics metrics) {
        BigInteger optimalSeed = total(seed.modelSeed());
        TrinityRadixModelPass.Firing firingPass = new TrinityRadixModelPass.Firing(
                optimalExternal,
                optimalSeed,
                firingLower);
        BigInteger firingObjectiveLower = firingLower.max(
                this.exactBounds.conservationFiringLowerBound(request, optimalExternal, optimalSeed));
        BigInteger seedWitnessFirings = total(seed.firings());
        BigInteger firingDomainUpper = maximum(
                seedWitnessFirings,
                optimalSeed,
                optimalExternal).max(firingObjectiveLower);
        TrinityAlgorithmResult<TrinityRadixSolvedModel> firing = seedWitnessFirings.equals(firingObjectiveLower) ?
                TrinityAlgorithmResult.success(seed) :
                optimize(
                        request,
                        firingPass,
                        firingDomainUpper,
                        control,
                        metrics);
        if (!firing.successful()) {
            if (recoverableStop(firing.diagnostic())) {
                return TrinityAlgorithmResult.success(Optional.of(feasibleSolutionValue(seed, metrics)));
            }
            return firing.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION ?
                    TrinityAlgorithmResult.success(Optional.empty()) :
                    TrinityAlgorithmResult.failure(firing.diagnostic());
        }
        BigInteger optimalFirings = total(firing.value().firings());
        BigInteger identityDomainUpper = maximum(optimalFirings, optimalSeed, optimalExternal);
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> fixedFirings = new Object2ObjectLinkedOpenHashMap<>();
        TrinityRadixSolvedModel canonical = firing.value();
        for (TrinityPatternVariant variant : request.variants()) {
            TrinityFiringBounds bounds = request.firingBounds().get(variant);
            if (bounds.fixed()) {
                BigInteger fixedCount = canonical.firings().getOrDefault(variant, BigInteger.ZERO);
                if (!fixedCount.equals(bounds.lowerInclusive())) {
                    throw new IllegalStateException("An exact Trinity firing solution violated a fixed axis");
                }
                fixedFirings.put(variant, fixedCount);
                continue;
            }
            TrinityRadixModelPass.Identity identityPass = new TrinityRadixModelPass.Identity(
                    optimalExternal,
                    optimalSeed,
                    optimalFirings,
                    fixedFirings,
                    variant);
            BigInteger witnessCount = canonical.firings().getOrDefault(variant, BigInteger.ZERO);
            BigInteger identityUpper = bounds.upperOr(this.exactBounds.identityObjectiveUpperBound(
                    request,
                    optimalExternal,
                    optimalSeed,
                    optimalFirings,
                    fixedFirings,
                    variant));
            if (witnessCount.compareTo(identityUpper) > 0) {
                throw new IllegalStateException("A Trinity identity witness exceeded its proven upper bound");
            }
            if (witnessCount.equals(identityUpper)) {
                fixedFirings.put(variant, witnessCount);
                continue;
            }
            TrinityAlgorithmResult<TrinityRadixSolvedModel> identity = optimize(
                    request,
                    identityPass,
                    identityDomainUpper,
                    control,
                    metrics);
            if (!identity.successful()) {
                if (recoverableStop(identity.diagnostic())) {
                    return TrinityAlgorithmResult.success(Optional.of(feasibleSolutionValue(canonical, metrics)));
                }
                return TrinityAlgorithmResult.failure(identity.diagnostic());
            }
            canonical = identity.value();
            fixedFirings.put(variant, canonical.firings().getOrDefault(variant, BigInteger.ZERO));
        }
        return TrinityAlgorithmResult.success(Optional.of(new TrinityCycleFeasibilitySolution(
                canonical.firings(),
                canonical.modelSeed(),
                canonical.externalInputs(),
                metrics.passes(),
                metrics.nanos(),
                true)));
    }

    private BigInteger completeLogicalUpper(
                                            TrinityCycleFeasibilityRequest request,
                                            TrinityRadixModelPass pass,
                                            TrinityPlanningControl control) {
        Optional<BigInteger> ordinary = request.ordinaryLogicalUpperBound();
        if (ordinary.isPresent()) {
            return ordinary.orElseThrow();
        }
        BigInteger initialUpper = this.exactBounds.compactFiringUpper(request);
        control.recordSolverModel();
        TrinityRadixBuiltModel envelope = this.modelAssembler.assemble(request, pass, initialUpper);
        return initialUpper.max(envelope.model().proofUpperBound());
    }

    private static BigInteger expandLogicalUpper(BigInteger current, BigInteger proofUpper) {
        BigInteger expanded = current.compareTo(BigInteger.ONE) <= 0 ?
                BigInteger.TWO : current.multiply(current);
        return expanded.min(proofUpper);
    }

    private TrinityAlgorithmResult<TrinityRadixSolvedModel> optimize(
                                                                     TrinityCycleFeasibilityRequest request,
                                                                     TrinityRadixModelPass pass,
                                                                     BigInteger logicalUpper,
                                                                     TrinityPlanningControl control,
                                                                     TrinityRadixSolverMetrics metrics) {
        return optimize(
                request,
                pass,
                logicalUpper,
                control,
                metrics,
                TrinityCycleSolveBudget.unbounded());
    }

    private TrinityAlgorithmResult<TrinityRadixSolvedModel> optimize(
                                                                     TrinityCycleFeasibilityRequest request,
                                                                     TrinityRadixModelPass pass,
                                                                     BigInteger logicalUpper,
                                                                     TrinityPlanningControl control,
                                                                     TrinityRadixSolverMetrics metrics,
                                                                     TrinityCycleSolveBudget stateBudget) {
        try {
            control.recordSolverModel();
            TrinityRadixBuiltModel built = this.modelAssembler.assemble(request, pass, logicalUpper);
            TrinityAlgorithmResult<Map<Variable, BigInteger>> optimized = this.objectiveSearch.optimize(
                    built,
                    control,
                    metrics,
                    stateBudget);
            if (!optimized.successful()) {
                return TrinityAlgorithmResult.failure(optimized.diagnostic());
            }
            TrinityRadixSolvedModel solved = built.decode(optimized.value());
            TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved);
            if (!exact.successful()) {
                return TrinityAlgorithmResult.failure(exact.diagnostic());
            }
            return TrinityAlgorithmResult.success(solved);
        } catch (TrinityRadixModelLimitException exception) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.radix_model_limit",
                    exception.metadata());
        } catch (TrinityRadixInfeasibleException exception) {
            return TrinityRadixDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution",
                    Map.of("constraint", exception.getMessage()));
        }
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExact(
                                                                       TrinityCycleFeasibilityRequest request,
                                                                       TrinityRadixModelPass pass,
                                                                       TrinityRadixSolvedModel solved) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initialInputs = new Object2ObjectLinkedOpenHashMap<>(solved.externalInputs());
        solved.modelSeed().forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                initialInputs,
                request.shortageDiagnostic() ? Map.of() : this.exactBounds.finiteInputUpperBounds(request),
                request.demand().finalBalanceLowerBounds(),
                request.demand().requiredNetChangeLowerBounds());
        if (!exact.successful()) {
            return exact;
        }
        BigInteger externalTotal = total(solved.externalInputs());
        BigInteger seedTotal = total(solved.modelSeed());
        BigInteger firingTotal = total(solved.firings());
        if (request.fixedExternalTotal().filter(fixed -> !fixed.equals(externalTotal)).isPresent()) {
            return TrinityRadixDiagnostics.inexact("fixed_external", externalTotal.toString());
        }
        for (TrinityPatternVariant variant : request.variants()) {
            BigInteger count = solved.firings().getOrDefault(variant, BigInteger.ZERO);
            if (!request.firingBounds().get(variant).contains(count)) {
                return TrinityRadixDiagnostics.inexact(
                        "firing_domain",
                        variant.patternIdentity().publicationEncoding());
            }
        }
        if (externalTotal.compareTo(this.exactBounds.minimumFirstExternalInput(request)) < 0 ||
                seedTotal.compareTo(this.exactBounds.minimumFirstInternalInput(request)) < 0) {
            return TrinityRadixDiagnostics.inexact("objective_lower", externalTotal + "/" + seedTotal);
        }
        if (pass instanceof TrinityRadixModelPass.Seed seed &&
                (!externalTotal.equals(seed.fixedExternal()) || seedTotal.compareTo(seed.seedLowerBound()) < 0)) {
            return TrinityRadixDiagnostics.inexact("seed_level", externalTotal + "/" + seedTotal);
        }
        if (pass instanceof TrinityRadixModelPass.Firing firing &&
                (!externalTotal.equals(firing.fixedExternal()) || !seedTotal.equals(firing.fixedSeed()) ||
                        firingTotal.compareTo(firing.firingLowerBound()) < 0)) {
            return TrinityRadixDiagnostics.inexact(
                    "firing_level",
                    externalTotal + "/" + seedTotal + "/" + firingTotal);
        }
        if (pass instanceof TrinityRadixModelPass.Identity identity &&
                (!externalTotal.equals(identity.fixedExternal()) || !seedTotal.equals(identity.fixedSeed()) ||
                        !firingTotal.equals(identity.fixedFirings()) || identity.fixedCounts().entrySet().stream()
                                .anyMatch(entry -> !solved.firings()
                                        .getOrDefault(entry.getKey(), BigInteger.ZERO)
                                        .equals(entry.getValue())))) {
            return TrinityRadixDiagnostics.inexact(
                    "identity_level",
                    identity.variant().patternIdentity().publicationEncoding());
        }
        if (pass == TrinityRadixModelPass.Feasibility.INSTANCE &&
                (seedTotal.compareTo(request.seedLowerBound()) < 0 ||
                        firingTotal.compareTo(request.firingLowerBound().max(BigInteger.ONE)) < 0)) {
            return TrinityRadixDiagnostics.inexact("feasibility_lower", seedTotal + "/" + firingTotal);
        }

        return exact;
    }

    private static BigInteger total(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> feasibleSolution(
                                                                                            TrinityRadixSolvedModel solved,
                                                                                            TrinityRadixSolverMetrics metrics) {
        return TrinityAlgorithmResult.success(feasibleSolutionValue(solved, metrics));
    }

    private static TrinityCycleFeasibilitySolution feasibleSolutionValue(
                                                                         TrinityRadixSolvedModel solved,
                                                                         TrinityRadixSolverMetrics metrics) {
        return new TrinityCycleFeasibilitySolution(
                solved.firings(),
                solved.modelSeed(),
                solved.externalInputs(),
                metrics.passes(),
                metrics.nanos(),
                true,
                TrinityPlanQuality.VERIFIED_FEASIBLE);
    }

    private static boolean recoverableStop(TrinityPlanningDiagnostic diagnostic) {
        TrinityPlanningDiagnosticCode code = diagnostic.code();
        return code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT ||
                code == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT;
    }

    /**
     * Bounds every non-negative axis by totals from an already feasible solution at the preceding objective levels.
     * A better firing solution cannot exceed the incumbent firing total, and every seed or external axis is bounded by
     * its fixed total, so this tightening preserves the complete lexicographic optimum.
     */
    private static BigInteger maximum(BigInteger first, BigInteger second, BigInteger third) {
        return first.max(second).max(third);
    }
}
