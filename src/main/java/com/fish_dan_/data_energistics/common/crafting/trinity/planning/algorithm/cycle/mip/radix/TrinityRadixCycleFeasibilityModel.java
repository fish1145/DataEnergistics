package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixDigits;
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
import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Coordinates sequential base-2^15 objectives, overflow proof, and exact conservation verification.
 */
public final class TrinityRadixCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger SINGLE_DIGIT_LOGICAL_UPPER = BigInteger.valueOf(TrinityRadixDigits.BASE - 1L);

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
        if (integerVerifier == null || conservationVerifier == null) {
            throw new IllegalArgumentException("A Trinity radix model requires exact result verifiers");
        }
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
        if (request == null || mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity radix solve requires a request and control");
        }
        if (mode == TrinityPlanningMode.FIRST_FEASIBLE) {
            return solveFirstFeasible(request, control);
        }
        TrinityAlgorithmResult<Optional<TrinityCycleFeasibilitySolution>> bounded = solveWithCertifiedSmallDomain(request, control);
        if (!bounded.successful()) {
            return TrinityAlgorithmResult.failure(bounded.diagnostic());
        }
        if (bounded.value().isPresent()) {
            return TrinityAlgorithmResult.success(bounded.value().orElseThrow());
        }
        return solveFullDomain(request, control);
    }

    private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveFirstFeasible(
                                                                                       TrinityCycleFeasibilityRequest request,
                                                                                       TrinityPlanningControl control) {
        TrinityRadixSolverMetrics metrics = new TrinityRadixSolverMetrics();
        BigInteger logicalUpper = request.ordinaryLogicalUpperBound()
                .map(upper -> upper.min(LONG_MAX))
                .orElse(LONG_MAX);
        try {
            TrinityRadixModelPass pass = TrinityRadixModelPass.External.INSTANCE;
            control.recordSolverModel();
            TrinityRadixBuiltModel built = this.modelAssembler.assemble(request, pass, logicalUpper);
            TrinityAlgorithmResult<Map<Variable, BigInteger>> witness = this.objectiveSearch.findFeasible(
                    built,
                    control,
                    metrics);
            if (!witness.successful()) {
                return TrinityAlgorithmResult.failure(witness.diagnostic());
            }
            TrinityRadixSolvedModel solved = built.decode(witness.value());
            TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved, true);
            if (!exact.successful()) {
                return TrinityAlgorithmResult.failure(exact.diagnostic());
            }
            if (solved.firings().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong) ||
                    solved.modelSeed().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong) ||
                    solved.externalInputs().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong)) {
                return TrinityRadixDiagnostics.inexact("radix_representable_bound", "axis_exceeds_long");
            }
            return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                    solved.firings(),
                    solved.modelSeed(),
                    solved.externalInputs(),
                    metrics.passes(),
                    metrics.nanos(),
                    true,
                    TrinityPlanQuality.VERIFIED_FEASIBLE));
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
                                                                                    TrinityPlanningControl control) {
        TrinityRadixSolverMetrics metrics = new TrinityRadixSolverMetrics();
        BigInteger representableUpper = request.ordinaryLogicalUpperBound()
                .map(upper -> upper.min(LONG_MAX))
                .orElse(LONG_MAX);
        boolean allowOverflowProof = representableUpper.equals(LONG_MAX);
        TrinityAlgorithmResult<TrinityRadixSolvedModel> external = optimize(
                request,
                TrinityRadixModelPass.External.INSTANCE,
                representableUpper,
                allowOverflowProof,
                control,
                metrics);
        if (!external.successful()) {
            return TrinityAlgorithmResult.failure(external.diagnostic());
        }
        BigInteger optimalExternal = total(external.value().externalInputs());
        BigInteger seedLower = request.seedLowerBound();
        BigInteger firingLower = request.firingLowerBound();
        while (true) {
            TrinityAlgorithmResult<TrinityRadixSolvedModel> seed = optimize(
                    request,
                    new TrinityRadixModelPass.Seed(optimalExternal, seedLower),
                    representableUpper,
                    allowOverflowProof,
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

    /**
     * A finite initial domain is globally safe only when it reaches both mathematical lower bounds. The later firing
     * witness then bounds every firing axis, so the remaining objectives retain their complete exact domains.
     */
    private TrinityAlgorithmResult<Optional<TrinityCycleFeasibilitySolution>> solveWithCertifiedSmallDomain(
                                                                                                            TrinityCycleFeasibilityRequest request,
                                                                                                            TrinityPlanningControl control) {
        BigInteger logicalUpper = smallDomainUpper(request);
        if (logicalUpper.compareTo(SINGLE_DIGIT_LOGICAL_UPPER) > 0) {
            return TrinityAlgorithmResult.success(Optional.empty());
        }
        TrinityRadixSolverMetrics metrics = new TrinityRadixSolverMetrics();
        TrinityAlgorithmResult<TrinityRadixSolvedModel> external = optimize(
                request,
                TrinityRadixModelPass.External.INSTANCE,
                logicalUpper,
                false,
                control,
                metrics);
        if (!external.successful()) {
            return external.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION ?
                    TrinityAlgorithmResult.success(Optional.empty()) :
                    TrinityAlgorithmResult.failure(external.diagnostic());
        }
        BigInteger optimalExternal = total(external.value().externalInputs());
        BigInteger requiredExternal = request.fixedExternalTotal()
                .orElse(this.exactBounds.minimumFirstExternalInput(request));
        if (!optimalExternal.equals(requiredExternal)) {
            return TrinityAlgorithmResult.success(Optional.empty());
        }
        BigInteger seedLower = request.seedLowerBound();
        TrinityAlgorithmResult<TrinityRadixSolvedModel> seed = optimize(
                request,
                new TrinityRadixModelPass.Seed(optimalExternal, seedLower),
                logicalUpper,
                false,
                control,
                metrics);
        if (!seed.successful()) {
            if (recoverableStop(seed.diagnostic())) {
                return TrinityAlgorithmResult.success(Optional.of(feasibleSolutionValue(
                        external.value(),
                        metrics)));
            }
            return seed.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION ?
                    TrinityAlgorithmResult.success(Optional.empty()) :
                    TrinityAlgorithmResult.failure(seed.diagnostic());
        }
        BigInteger requiredSeed = this.exactBounds.minimumFirstInternalInput(request).max(seedLower);
        if (!total(seed.value().modelSeed()).equals(requiredSeed)) {
            return TrinityAlgorithmResult.success(Optional.empty());
        }
        return completeSeedWitness(
                request,
                optimalExternal,
                seed.value(),
                request.firingLowerBound(),
                control,
                metrics);
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
                        false,
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
        LinkedHashMap<TrinityPatternVariant, BigInteger> fixedFirings = new LinkedHashMap<>();
        TrinityRadixSolvedModel canonical = firing.value();
        for (TrinityPatternVariant variant : request.variants()) {
            TrinityFiringBounds bounds = request.firingBounds().get(variant);
            if (bounds.lowerInclusive().equals(bounds.upperInclusive())) {
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
            BigInteger identityUpper = this.exactBounds.identityObjectiveUpperBound(
                    request,
                    optimalExternal,
                    optimalSeed,
                    optimalFirings,
                    fixedFirings,
                    variant)
                    .min(bounds.upperInclusive());
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
                    false,
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

    private BigInteger smallDomainUpper(TrinityCycleFeasibilityRequest request) {
        BigInteger upper = BigInteger.ONE
                .max(request.seedLowerBound())
                .max(request.firingLowerBound())
                .max(this.exactBounds.minimumFirstExternalInput(request))
                .max(this.exactBounds.minimumFirstInternalInput(request));
        if (request.fixedExternalTotal().isPresent()) {
            upper = upper.max(request.fixedExternalTotal().orElseThrow());
        }
        for (BigInteger amount : request.demand().finalBalanceLowerBounds().values()) {
            upper = upper.max(amount);
        }
        for (BigInteger amount : request.demand().requiredNetChangeLowerBounds().values()) {
            upper = upper.max(amount);
        }
        for (TrinityFiringBounds bounds : request.firingBounds().values()) {
            upper = upper.max(bounds.lowerInclusive());
        }
        for (TrinityPatternVariant variant : request.variants()) {
            for (BigInteger amount : variant.inputs().values()) {
                upper = upper.max(amount);
            }
            for (BigInteger amount : variant.outputs().values()) {
                upper = upper.max(amount);
            }
        }
        return upper.multiply(BigInteger.valueOf(request.variants().size()));
    }

    private TrinityAlgorithmResult<TrinityRadixSolvedModel> optimize(
                                                                     TrinityCycleFeasibilityRequest request,
                                                                     TrinityRadixModelPass pass,
                                                                     BigInteger logicalUpper,
                                                                     boolean allowOverflowProof,
                                                                     TrinityPlanningControl control,
                                                                     TrinityRadixSolverMetrics metrics) {
        try {
            control.recordSolverModel();
            TrinityRadixBuiltModel built = this.modelAssembler.assemble(request, pass, logicalUpper);
            TrinityAlgorithmResult<Map<Variable, BigInteger>> optimized = this.objectiveSearch.optimize(
                    built,
                    control,
                    metrics);
            if (!optimized.successful()) {
                if (optimized.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return TrinityAlgorithmResult.failure(optimized.diagnostic());
                }
                if (!allowOverflowProof) {
                    return TrinityAlgorithmResult.failure(optimized.diagnostic());
                }
                return probeOverflow(request, pass, built, control, metrics, optimized.diagnostic());
            }
            TrinityRadixSolvedModel solved = built.decode(optimized.value());
            TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved, true);
            if (!exact.successful()) {
                return TrinityAlgorithmResult.failure(exact.diagnostic());
            }
            if (solved.firings().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong) ||
                    solved.modelSeed().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong) ||
                    solved.externalInputs().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong)) {
                return TrinityRadixDiagnostics.inexact("radix_representable_bound", "axis_exceeds_long");
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

    private TrinityAlgorithmResult<TrinityRadixSolvedModel> probeOverflow(
                                                                          TrinityCycleFeasibilityRequest request,
                                                                          TrinityRadixModelPass pass,
                                                                          TrinityRadixBuiltModel representableModel,
                                                                          TrinityPlanningControl control,
                                                                          TrinityRadixSolverMetrics metrics,
                                                                          TrinityPlanningDiagnostic infeasible) {
        BigInteger proofUpper = representableModel.model().proofUpperBound();
        control.recordSolverModel();
        TrinityRadixBuiltModel proofModel = this.modelAssembler.assemble(request, pass, proofUpper);
        TrinityAlgorithmResult<Map<Variable, BigInteger>> proof = this.objectiveSearch.findFeasible(
                proofModel,
                control,
                metrics);
        if (!proof.successful()) {
            return proof.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION ?
                    TrinityAlgorithmResult.failure(infeasible) :
                    TrinityAlgorithmResult.failure(proof.diagnostic());
        }
        TrinityRadixSolvedModel solved = proofModel.decode(proof.value());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(
                request,
                pass,
                solved,
                !request.fullLongFiringDomain());
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        if (!solved.firings().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong) &&
                !solved.modelSeed().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong) &&
                !solved.externalInputs().values().stream().anyMatch(TrinityRadixCycleFeasibilityModel::outsideLong)) {
            return TrinityRadixDiagnostics.inexact("radix_long_domain", "proof_found_representable_witness");
        }
        return TrinityRadixDiagnostics.failure(
                TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                "gui.data_energistics.trinity_planning.mip.arithmetic_overflow",
                Map.of(
                        "pass", pass.getClass().getSimpleName(),
                        "proofUpper", proofUpper.toString()));
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExact(
                                                                       TrinityCycleFeasibilityRequest request,
                                                                       TrinityRadixModelPass pass,
                                                                       TrinityRadixSolvedModel solved,
                                                                       boolean enforceFiringBounds) {
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(solved.externalInputs());
        solved.modelSeed().forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                initialInputs,
                this.exactBounds.finiteInputUpperBounds(request),
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
        if (enforceFiringBounds) {
            for (TrinityPatternVariant variant : request.variants()) {
                BigInteger count = solved.firings().getOrDefault(variant, BigInteger.ZERO);
                if (!request.firingBounds().get(variant).contains(count)) {
                    return TrinityRadixDiagnostics.inexact(
                            "firing_domain",
                            variant.patternIdentity().publicationEncoding());
                }
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
        return exact;
    }

    private static boolean outsideLong(BigInteger value) {
        return value.compareTo(LONG_MAX) > 0;
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
