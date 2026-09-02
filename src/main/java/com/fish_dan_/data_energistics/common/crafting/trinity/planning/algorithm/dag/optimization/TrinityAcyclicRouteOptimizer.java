package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Selects one exact aggregate firing vector when an acyclic request has competing or mixable routes.
 * <p>
 * Sequential lexicographic model: inventory units, firings, then stable variant identity.
 */
public final class TrinityAcyclicRouteOptimizer {

    /**
     * @return ojAlgo-backed optimizer with exact integer and conservation verification
     */
    public static TrinityAcyclicRouteOptimizer create() {
        return new TrinityAcyclicRouteOptimizer(
                TrinityIntegerResultVerifier.create(),
                TrinityExactConservationVerifier.create(),
                TrinityAcyclicRoutePruner.create());
    }

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String INSUFFICIENT_INPUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.insufficient_input";
    private static final String INEXACT_RESULT_KEY = "gui.data_energistics.trinity_planning.diagnostic.inexact_result";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    private final TrinityIntegerResultVerifier integerVerifier;
    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityAcyclicRoutePruner routePruner;

    TrinityAcyclicRouteOptimizer(TrinityIntegerResultVerifier integerVerifier,
                                 TrinityExactConservationVerifier conservationVerifier,
                                 TrinityAcyclicRoutePruner routePruner) {
        this.integerVerifier = integerVerifier;
        this.conservationVerifier = conservationVerifier;
        this.routePruner = routePruner;
    }

    /**
     * Solves the complete target-reachable acyclic region without expanding one state per requested item.
     *
     * @param topology        analyzed acyclic topology
     * @param variants        complete stable transition set
     * @param target          requested output
     * @param requestedAmount positive requested quantity
     * @param quantityMode    target inventory semantics
     * @param available       immutable inventory snapshot
     * @param maxSearchStates maximum sequential optimization passes
     * @param mode            complete optimisation or first-feasible fallback
     * @param control         cooperative cancellation and deadline
     * @return exact executable aggregate plan or a stable fallback diagnostic
     */
    public TrinityAlgorithmResult<TrinityAcyclicPlan> optimize(
                                                               TrinityCraftingTopology topology,
                                                               List<TrinityPatternVariant> variants,
                                                               AEKey target,
                                                               BigInteger requestedAmount,
                                                               CraftingQuantityMode quantityMode,
                                                               TrinityPlanningInventory available,
                                                               int maxSearchStates,
                                                               TrinityPlanningMode mode,
                                                               TrinityPlanningControl control) {
        if (requestedAmount.signum() <= 0 || maxSearchStates <= 0) {
            throw new IllegalArgumentException("A Trinity acyclic route optimization requires complete inputs");
        }
        return optimize(
                topology,
                variants,
                target,
                requestedAmount,
                quantityMode,
                available,
                Set.of(),
                maxSearchStates,
                mode,
                control);
    }

    /** Uses a quantity-free route hint only as an exactly revalidated incumbent. */
    public TrinityAlgorithmResult<TrinityAcyclicPlan> optimize(
                                                               TrinityCraftingTopology topology,
                                                               List<TrinityPatternVariant> variants,
                                                               AEKey target,
                                                               BigInteger requestedAmount,
                                                               CraftingQuantityMode quantityMode,
                                                               TrinityPlanningInventory available,
                                                               Set<TrinityPatternIdentity> routeHint,
                                                               int maxSearchStates,
                                                               TrinityPlanningMode mode,
                                                               TrinityPlanningControl control) {
        List<TrinityPatternVariant> reachable = this.routePruner.retainExecutableTargetRoutes(
                variants,
                target,
                available);
        if (reachable.isEmpty()) {
            return insufficient(target, requestedAmount);
        }
        TrinityPlanningInventory inventory = available;
        BigInteger requiredTargetNet = requiredTargetNet(target, requestedAmount, quantityMode, inventory);
        SearchBudget budget = new SearchBudget(maxSearchStates, control);
        Optional<UniformBindingFamily> uniformBindings = UniformBindingFamily.tryCreate(reachable, target);
        if (uniformBindings.isPresent()) {
            return optimizeUniformBindings(
                    topology,
                    uniformBindings.get(),
                    target,
                    requestedAmount,
                    requiredTargetNet,
                    quantityMode,
                    inventory,
                    budget,
                    control);
        }
        ModelRequest templateRequest = new ModelRequest(
                reachable,
                target,
                requestedAmount,
                requiredTargetNet,
                quantityMode,
                inventory,
                FeasibilityPass.INSTANCE);
        control.recordSolverModel();
        AcyclicModelTemplate modelTemplate = createModelTemplate(templateRequest);

        TrinityAcyclicPlan hintedIncumbent = null;
        if (!routeHint.isEmpty()) {
            TrinityAlgorithmResult<SolvedPass> hinted = solve(
                    new ModelRequest(
                            reachable,
                            target,
                            requestedAmount,
                            requiredTargetNet,
                            quantityMode,
                            inventory,
                            new HintFeasibilityPass(routeHint)),
                    budget,
                    modelTemplate,
                    control);
            if (hinted.successful()) {
                TrinityAlgorithmResult<TrinityAcyclicPlan> built = buildQualifiedPlan(
                        topology,
                        hinted.value().model(),
                        budget.used(),
                        TrinityPlanQuality.VERIFIED_FEASIBLE);
                if (built.successful()) {
                    hintedIncumbent = built.value();
                }
            } else if (hinted.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED) {
                return TrinityAlgorithmResult.failure(hinted.diagnostic());
            }
        }

        if (mode == TrinityPlanningMode.FIRST_FEASIBLE) {
            if (hintedIncumbent != null) {
                return TrinityAlgorithmResult.success(hintedIncumbent);
            }
            TrinityAlgorithmResult<SolvedPass> feasible = solve(
                    new ModelRequest(
                            reachable,
                            target,
                            requestedAmount,
                            requiredTargetNet,
                            quantityMode,
                            inventory,
                            FeasibilityPass.INSTANCE),
                    budget,
                    modelTemplate,
                    control);
            if (!feasible.successful()) {
                return TrinityAlgorithmResult.failure(feasible.diagnostic());
            }
            return buildQualifiedPlan(
                    topology,
                    feasible.value().model(),
                    budget.used(),
                    TrinityPlanQuality.VERIFIED_FEASIBLE);
        }

        TrinityAlgorithmResult<SolvedPass> externalResult = solve(
                new ModelRequest(
                        reachable,
                        target,
                        requestedAmount,
                        requiredTargetNet,
                        quantityMode,
                        inventory,
                        ExternalPass.INSTANCE),
                budget,
                modelTemplate,
                control);
        if (!externalResult.successful()) {
            return hintedIncumbent == null ?
                    TrinityAlgorithmResult.failure(externalResult.diagnostic()) :
                    recoverIncumbent(hintedIncumbent, externalResult.diagnostic());
        }
        TrinityAlgorithmResult<TrinityAcyclicPlan> externalPlan = buildQualifiedPlan(
                topology,
                externalResult.value().model(),
                budget.used(),
                TrinityPlanQuality.VERIFIED_FEASIBLE);
        if (!externalPlan.successful()) {
            return externalPlan;
        }
        TrinityAcyclicPlan incumbent = externalPlan.value();
        if (!externalResult.value().objectiveProved()) {
            return TrinityAlgorithmResult.success(incumbent);
        }
        BigInteger optimalExternal = sum(externalResult.value().model().reserves());

        TrinityAlgorithmResult<SolvedPass> firingResult = solve(
                new ModelRequest(
                        reachable,
                        target,
                        requestedAmount,
                        requiredTargetNet,
                        quantityMode,
                        inventory,
                        new FiringPass(optimalExternal)),
                budget,
                modelTemplate,
                control);
        if (!firingResult.successful()) {
            return recoverIncumbent(incumbent, firingResult.diagnostic());
        }
        TrinityAlgorithmResult<TrinityAcyclicPlan> firingPlan = buildQualifiedPlan(
                topology,
                firingResult.value().model(),
                budget.used(),
                TrinityPlanQuality.VERIFIED_FEASIBLE);
        if (firingPlan.successful()) {
            incumbent = firingPlan.value();
        }
        if (!firingResult.value().objectiveProved()) {
            return firingPlan.successful() ? firingPlan : TrinityAlgorithmResult.failure(firingPlan.diagnostic());
        }
        BigInteger optimalFirings = sum(firingResult.value().model().firings());
        SolvedModel selected = firingResult.value().model();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> fixedPrefix = new Object2ObjectLinkedOpenHashMap<>();
        Map<AEKey, BigInteger> sourceCapacity = sourceCapacity(reachable, inventory);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> fixedSourceConsumption = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger remainingFirings = optimalFirings;
        for (int index = 0; index < reachable.size() - 1; index++) {
            TrinityPatternVariant preferred = reachable.get(index);
            if (remainingFirings.signum() == 0) {
                break;
            }
            BigInteger preferredCount = selected.firings().getOrDefault(preferred, BigInteger.ZERO);
            BigInteger provenUpper = sourceUpperBound(
                    preferred,
                    remainingFirings,
                    sourceCapacity,
                    fixedSourceConsumption);
            if (!preferredCount.equals(provenUpper)) {
                TrinityAlgorithmResult<SolvedPass> identityResult = solve(
                        new ModelRequest(
                                reachable,
                                target,
                                requestedAmount,
                                requiredTargetNet,
                                quantityMode,
                                inventory,
                                new IdentityPass(
                                        optimalExternal,
                                        optimalFirings,
                                        Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(fixedPrefix)),
                                        preferred)),
                        budget,
                        modelTemplate,
                        control);
                if (!identityResult.successful()) {
                    return recoverIncumbent(incumbent, identityResult.diagnostic());
                }
                selected = identityResult.value().model();
                TrinityAlgorithmResult<TrinityAcyclicPlan> identityPlan = buildQualifiedPlan(
                        topology,
                        selected,
                        budget.used(),
                        TrinityPlanQuality.VERIFIED_FEASIBLE);
                if (identityPlan.successful()) {
                    incumbent = identityPlan.value();
                }
                if (!identityResult.value().objectiveProved()) {
                    return identityPlan.successful() ? identityPlan : TrinityAlgorithmResult.failure(identityPlan.diagnostic());
                }
                preferredCount = selected.firings().getOrDefault(preferred, BigInteger.ZERO);
            }
            fixedPrefix.put(preferred, preferredCount);
            remainingFirings = remainingFirings.subtract(preferredCount);
            mergeSourceConsumption(fixedSourceConsumption, preferred, preferredCount, sourceCapacity.keySet());
        }
        return buildQualifiedPlan(
                topology,
                selected,
                budget.used(),
                TrinityPlanQuality.PROVED_OPTIMAL);
    }

    /**
     * Selects one non-executable target route by allowing virtual reserve only for true external source keys.
     * The returned evidence is diagnostic-only: virtual reserve is separated from every actual inventory reserve and
     * must never be copied into an executable plan.
     *
     * @param variants        complete stable transition set
     * @param target          requested output
     * @param requestedAmount positive requested quantity
     * @param quantityMode    target inventory semantics
     * @param available       immutable inventory snapshot
     * @param maxSearchStates maximum sequential optimization passes
     * @param control         cooperative cancellation and deadline
     * @return exact shortage evidence, or a stable solver diagnostic
     */
    public TrinityAlgorithmResult<ShortageEvidence> diagnoseShortage(
                                                                     List<TrinityPatternVariant> variants,
                                                                     AEKey target,
                                                                     BigInteger requestedAmount,
                                                                     CraftingQuantityMode quantityMode,
                                                                     TrinityPlanningInventory available,
                                                                     int maxSearchStates,
                                                                     TrinityPlanningControl control) {
        if (requestedAmount.signum() <= 0 || maxSearchStates <= 0) {
            throw new IllegalArgumentException("A Trinity acyclic shortage diagnosis requires complete inputs");
        }
        List<TrinityPatternVariant> reachable = targetReachableVariants(variants, target);
        if (reachable.isEmpty()) {
            return insufficient(target, requestedAmount);
        }
        TrinityPlanningInventory inventory = available;
        Set<AEKey> sourceKeys = externalSourceKeys(reachable, inventory);
        if (sourceKeys.isEmpty()) {
            return insufficient(target, requestedAmount);
        }
        BigInteger requiredTargetNet = requiredTargetNet(target, requestedAmount, quantityMode, inventory);
        SearchBudget budget = new SearchBudget(maxSearchStates, control);

        TrinityAlgorithmResult<DiagnosticSolvedModel> missingResult = solveDiagnostic(
                new DiagnosticModelRequest(
                        reachable,
                        sourceKeys,
                        target,
                        requestedAmount,
                        requiredTargetNet,
                        quantityMode,
                        inventory),
                budget,
                control);
        if (!missingResult.successful()) {
            return TrinityAlgorithmResult.failure(missingResult.diagnostic());
        }
        BigInteger optimalMissing = sum(missingResult.value().missing());
        if (optimalMissing.signum() == 0) {
            return inexact("diagnostic_missing", "0");
        }
        DiagnosticSolvedModel selected = missingResult.value();

        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> requirements = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey sourceKey : sourceKeys) {
            BigInteger allocated = selected.actualReserves().getOrDefault(sourceKey, BigInteger.ZERO);
            BigInteger missing = selected.missing().getOrDefault(sourceKey, BigInteger.ZERO);
            BigInteger required = allocated.add(missing);
            if (required.signum() > 0) {
                requirements.put(sourceKey, new InputRequirement(required, allocated, missing));
            }
        }
        return TrinityAlgorithmResult.success(new ShortageEvidence(
                selected.firings(),
                selected.actualReserves(),
                Collections.unmodifiableMap(requirements),
                selected.netChange(),
                budget.used()));
    }

    /**
     * Solves the common AE2 substitution shape without starting one MIP per stable binding.
     *
     * <p>
     * Every accepted variant is the same physical pattern, produces the same target amount and consumes the same
     * quantity of exactly one interchangeable input. External-input and firing objectives are therefore constant;
     * allocating available inputs in stable variant order is the exact lexicographic optimum.
     * </p>
     */
    private TrinityAlgorithmResult<TrinityAcyclicPlan> optimizeUniformBindings(
                                                                               TrinityCraftingTopology topology,
                                                                               UniformBindingFamily family,
                                                                               AEKey target,
                                                                               BigInteger requestedAmount,
                                                                               BigInteger requiredTargetNet,
                                                                               CraftingQuantityMode quantityMode,
                                                                               TrinityPlanningInventory available,
                                                                               SearchBudget budget,
                                                                               TrinityPlanningControl control) {
        BigInteger requiredFirings = ceilDivide(requiredTargetNet, family.outputPerFiring());
        BigInteger remainingFirings = requiredFirings;
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> remainingInventory = new Object2ObjectLinkedOpenHashMap<>(available.finiteAmounts());
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> reserves = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger targetReserve = targetReserve(target, requestedAmount, quantityMode, available);
        if (targetReserve.signum() > 0) {
            reserves.put(target, targetReserve);
        }

        for (TrinityPatternVariant variant : family.variants()) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(budget.used())));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        Map.of("states", Integer.toString(budget.used())));
            }
            if (!budget.consume()) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of("states", Integer.toString(budget.used())));
            }

            Map.Entry<AEKey, BigInteger> input = variant.inputs().entrySet().iterator().next();
            boolean unlimited = available.unlimited(input.getKey());
            BigInteger availableInput = unlimited ?
                    family.inputPerFiring().multiply(remainingFirings) :
                    remainingInventory.getOrDefault(input.getKey(), BigInteger.ZERO);
            BigInteger selectedFirings = remainingFirings.min(availableInput.divide(family.inputPerFiring()));
            if (selectedFirings.signum() > 0) {
                BigInteger consumed = family.inputPerFiring().multiply(selectedFirings);
                firings.put(variant, selectedFirings);
                reserves.merge(input.getKey(), consumed, BigInteger::add);
                if (!unlimited) {
                    remainingInventory.put(input.getKey(), availableInput.subtract(consumed));
                }
                remainingFirings = remainingFirings.subtract(selectedFirings);
            }
            if (remainingFirings.signum() == 0) {
                break;
            }
        }
        if (remainingFirings.signum() > 0) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    INSUFFICIENT_INPUT_KEY,
                    Map.of(
                            "requiredFirings", requiredFirings.toString(),
                            "availableFirings", requiredFirings.subtract(remainingFirings).toString()));
        }

        ModelRequest verificationRequest = new ModelRequest(
                family.variants(),
                target,
                requestedAmount,
                requiredTargetNet,
                quantityMode,
                available,
                ExternalPass.INSTANCE);
        SolvedModel selected = new SolvedModel(
                Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(firings)),
                Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(reserves)),
                Map.of());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verify(verificationRequest, selected);
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        return buildPlan(
                topology,
                new SolvedModel(selected.firings(), selected.reserves(), exact.value()),
                budget.used());
    }

    private TrinityAlgorithmResult<SolvedPass> solve(
                                                     ModelRequest request,
                                                     SearchBudget budget,
                                                     AcyclicModelTemplate modelTemplate,
                                                     TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    TIMEOUT_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (!budget.consume()) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }

        ModelData data = modelTemplate.forPass(request.pass());
        configureDeadline(data.model(), control);
        long startedNanos = System.nanoTime();
        Optimisation.Result result = request.pass() instanceof IdentityPass ?
                data.model().maximise() :
                data.model().minimise();
        control.recordSolverPass(Math.max(0L, System.nanoTime() - startedNanos));

        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        boolean objectiveProved = result.getState().isOptimal();
        if (!objectiveProved && !result.getState().isFeasible()) {
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        Map.of("state", result.getState().name()));
            }
            if (result.getState() == Optimisation.State.INFEASIBLE) {
                return failure(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        INSUFFICIENT_INPUT_KEY,
                        Map.of("state", result.getState().name()));
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                    INEXACT_RESULT_KEY,
                    Map.of("state", result.getState().name()));
        }

        ObjectArrayList<BigDecimal> values = new ObjectArrayList<>(data.variables().size());
        for (Variable variable : data.variables()) {
            values.add(result.get(data.model().indexOf(variable)));
        }
        TrinityAlgorithmResult<List<BigInteger>> integers = this.integerVerifier.verify(
                values,
                data.model().options.integer().getIntegralityTolerance());
        if (!integers.successful()) {
            return TrinityAlgorithmResult.failure(integers.diagnostic());
        }
        if (integers.value().stream().anyMatch(value -> value.signum() < 0)) {
            return inexact("variable_lower", "negative");
        }
        SolvedModel solved = data.decode(integers.value());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verify(request, solved);
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        return TrinityAlgorithmResult.success(new SolvedPass(
                new SolvedModel(
                        solved.firings(),
                        solved.reserves(),
                        exact.value()),
                objectiveProved));
    }

    private TrinityAlgorithmResult<TrinityAcyclicPlan> buildQualifiedPlan(
                                                                          TrinityCraftingTopology topology,
                                                                          SolvedModel solved,
                                                                          int states,
                                                                          TrinityPlanQuality quality) {
        TrinityAlgorithmResult<TrinityAcyclicPlan> built = buildPlan(topology, solved, states);
        return built.successful() ?
                TrinityAlgorithmResult.success(built.value().withQuality(quality)) :
                built;
    }

    private static TrinityAlgorithmResult<TrinityAcyclicPlan> recoverIncumbent(
                                                                               TrinityAcyclicPlan incumbent,
                                                                               TrinityPlanningDiagnostic diagnostic) {
        TrinityPlanningDiagnosticCode code = diagnostic.code();
        return code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT ||
                code == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT ?
                        TrinityAlgorithmResult.success(incumbent) :
                        TrinityAlgorithmResult.failure(diagnostic);
    }

    private TrinityAlgorithmResult<DiagnosticSolvedModel> solveDiagnostic(
                                                                          DiagnosticModelRequest request,
                                                                          SearchBudget budget,
                                                                          TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    TIMEOUT_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (!budget.consume()) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }

        control.recordSolverModel();
        DiagnosticModelData data = createDiagnosticModel(request);
        configureDeadline(data.model(), control);
        long startedNanos = System.nanoTime();
        Optimisation.Result result = data.model().minimise();
        control.recordSolverPass(Math.max(0L, System.nanoTime() - startedNanos));
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (!result.getState().isOptimal()) {
            if (control.deadlineExceeded() || result.getState().isFeasible()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        Map.of("state", result.getState().name()));
            }
            if (result.getState() == Optimisation.State.INFEASIBLE) {
                return failure(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        INSUFFICIENT_INPUT_KEY,
                        Map.of("state", result.getState().name()));
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                    INEXACT_RESULT_KEY,
                    Map.of("state", result.getState().name()));
        }

        ObjectArrayList<BigDecimal> values = new ObjectArrayList<>(data.variables().size());
        for (Variable variable : data.variables()) {
            values.add(result.get(data.model().indexOf(variable)));
        }
        TrinityAlgorithmResult<List<BigInteger>> integers = this.integerVerifier.verify(
                values,
                data.model().options.integer().getIntegralityTolerance());
        if (!integers.successful()) {
            return TrinityAlgorithmResult.failure(integers.diagnostic());
        }
        if (integers.value().stream().anyMatch(value -> value.signum() < 0)) {
            return inexact("variable_lower", "negative");
        }
        DiagnosticSolvedModel solved = data.decode(integers.value());
        return verifyDiagnostic(request, solved);
    }

    private TrinityAlgorithmResult<DiagnosticSolvedModel> verifyDiagnostic(
                                                                           DiagnosticModelRequest request,
                                                                           DiagnosticSolvedModel solved) {
        BigInteger expectedTargetReserve = targetReserve(
                request.target(),
                request.requestedAmount(),
                request.quantityMode(),
                request.available());
        BigInteger actualTargetReserve = solved.actualReserves()
                .getOrDefault(request.target(), BigInteger.ZERO);
        if (!actualTargetReserve.equals(expectedTargetReserve)) {
            return inexact("target_reserve", actualTargetReserve + "!=" + expectedTargetReserve);
        }
        for (Map.Entry<AEKey, BigInteger> reserve : solved.actualReserves().entrySet()) {
            BigInteger upper = request.quantityMode() == CraftingQuantityMode.NET_NEW &&
                    reserve.getKey().equals(request.target()) ?
                            BigInteger.ZERO :
                            request.available().availableUpTo(reserve.getKey(), reserve.getValue());
            if (reserve.getValue().compareTo(upper) > 0) {
                return inexact("actual_reserve_upper", reserve.getKey() + ":" + reserve.getValue() + ">" + upper);
            }
        }
        for (Map.Entry<AEKey, BigInteger> missing : solved.missing().entrySet()) {
            if (!request.sourceKeys().contains(missing.getKey()) || missing.getValue().signum() <= 0) {
                return inexact("missing_domain", missing.getKey() + ":" + missing.getValue());
            }
        }

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> diagnosticReserves = new Object2ObjectLinkedOpenHashMap<>(solved.actualReserves());
        solved.missing().forEach((key, amount) -> diagnosticReserves.merge(key, amount, BigInteger::add));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> upperBounds = new Object2ObjectLinkedOpenHashMap<>();
        touchedKeys(request.variants(), request.target()).forEach(key -> upperBounds.put(
                key,
                request.sourceKeys().contains(key) ?
                        diagnosticReserves.getOrDefault(key, BigInteger.ZERO) :
                        request.quantityMode() == CraftingQuantityMode.NET_NEW && key.equals(request.target()) ?
                                BigInteger.ZERO :
                                request.available().availableUpTo(
                                        key,
                                        solved.actualReserves().getOrDefault(key, BigInteger.ZERO))));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                diagnosticReserves,
                upperBounds,
                Map.of(request.target(), request.requestedAmount()),
                Map.of(request.target(), request.requiredTargetNet()));
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        return TrinityAlgorithmResult.success(new DiagnosticSolvedModel(
                solved.firings(),
                solved.actualReserves(),
                solved.missing(),
                exact.value()));
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                                  ModelRequest request,
                                                                  SolvedModel solved) {
        BigInteger expectedTargetReserve = targetReserve(
                request.target(),
                request.requestedAmount(),
                request.quantityMode(),
                request.available());
        BigInteger actualTargetReserve = solved.reserves().getOrDefault(request.target(), BigInteger.ZERO);
        if (actualTargetReserve.compareTo(expectedTargetReserve) != 0) {
            return inexact("target_reserve", actualTargetReserve + "!=" + expectedTargetReserve);
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> upperBounds = new Object2ObjectLinkedOpenHashMap<>();
        touchedKeys(request.variants(), request.target()).forEach(key -> upperBounds.put(
                key,
                request.quantityMode() == CraftingQuantityMode.NET_NEW && key.equals(request.target()) ?
                        BigInteger.ZERO :
                        request.available().availableUpTo(
                                key,
                                solved.reserves().getOrDefault(key, BigInteger.ZERO))));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                solved.reserves(),
                upperBounds,
                Map.of(request.target(), request.requestedAmount()),
                Map.of(request.target(), request.requiredTargetNet()));
        if (!exact.successful()) {
            return exact;
        }

        BigInteger external = sum(solved.reserves());
        BigInteger firings = sum(solved.firings());
        if (request.pass() instanceof FiringPass pass &&
                external.compareTo(pass.fixedExternal()) != 0) {
            return inexact("external_level", external + "!=" + pass.fixedExternal());
        }
        if (request.pass() instanceof IdentityPass pass) {
            if (external.compareTo(pass.fixedExternal()) != 0) {
                return inexact("external_level", external + "!=" + pass.fixedExternal());
            }
            if (firings.compareTo(pass.fixedFirings()) != 0) {
                return inexact("firing_level", firings + "!=" + pass.fixedFirings());
            }
            for (Map.Entry<TrinityPatternVariant, BigInteger> fixed : pass.fixedPrefix().entrySet()) {
                BigInteger actual = solved.firings().getOrDefault(fixed.getKey(), BigInteger.ZERO);
                if (actual.compareTo(fixed.getValue()) != 0) {
                    return inexact("identity_level", actual + "!=" + fixed.getValue());
                }
            }
        }
        return exact;
    }

    private static void configureDeadline(ExpressionsBasedModel model, TrinityPlanningControl control) {
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

    private static AcyclicModelTemplate createModelTemplate(ModelRequest request) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, Variable> firingVariables = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            Variable variable = model.addVariable("firing_" + index)
                    .integer()
                    .lower(BigInteger.ZERO);
            firingVariables.put(request.variants().get(index), variable);
        }

        Object2ObjectLinkedOpenHashMap<AEKey, Variable> reserveVariables = new Object2ObjectLinkedOpenHashMap<>();
        int reserveIndex = 0;
        for (AEKey key : touchedKeys(request.variants(), request.target())) {
            BigInteger upper = request.available().finiteAmount(key);
            Variable variable = model.addVariable("reserve_" + reserveIndex++)
                    .integer()
                    .lower(BigInteger.ZERO);
            if (key.equals(request.target())) {
                BigInteger targetReserve = targetReserve(
                        request.target(),
                        request.requestedAmount(),
                        request.quantityMode(),
                        request.available());
                variable.level(targetReserve);
            } else if (!request.available().unlimited(key)) {
                variable.upper(upper);
            }
            reserveVariables.put(key, variable);
        }

        int conservationIndex = 0;
        for (Map.Entry<AEKey, Variable> reserve : reserveVariables.entrySet()) {
            AEKey key = reserve.getKey();
            Expression conservation = model.addExpression("conservation_" + conservationIndex++);
            firingVariables.forEach((variant, variable) -> {
                BigInteger coefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (coefficient.signum() != 0) {
                    conservation.set(variable, coefficient);
                }
            });
            conservation.set(reserve.getValue(), BigInteger.ONE);
            conservation.lower(key.equals(request.target()) ? request.requestedAmount() : BigInteger.ZERO);
        }

        Expression targetNet = model.addExpression("target_net");
        firingVariables.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(request.target(), BigInteger.ZERO);
            if (coefficient.signum() != 0) {
                targetNet.set(variable, coefficient);
            }
        });
        targetNet.lower(request.requiredTargetNet());

        Expression externalTotal = expression(model, "external_total", reserveVariables.values());
        Expression firingTotal = expression(model, "firing_total", firingVariables.values());
        Object2IntMap<TrinityPatternVariant> firingIndexes = new Object2IntLinkedOpenHashMap<>();
        firingIndexes.defaultReturnValue(-1);
        firingVariables.forEach((variant, variable) -> firingIndexes.put(variant, model.indexOf(variable)));
        Object2IntMap<AEKey> reserveIndexes = new Object2IntLinkedOpenHashMap<>();
        reserveIndexes.defaultReturnValue(-1);
        reserveVariables.forEach((key, variable) -> reserveIndexes.put(key, model.indexOf(variable)));
        return new AcyclicModelTemplate(
                model,
                model.countVariables(),
                Object2IntMaps.unmodifiable(firingIndexes),
                Object2IntMaps.unmodifiable(reserveIndexes));
    }

    private static DiagnosticModelData createDiagnosticModel(DiagnosticModelRequest request) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ObjectArrayList<Variable> variables = new ObjectArrayList<>();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, Variable> firingVariables = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            Variable variable = model.addVariable("firing_" + index)
                    .integer()
                    .lower(BigInteger.ZERO);
            firingVariables.put(request.variants().get(index), variable);
            variables.add(variable);
        }

        Object2ObjectLinkedOpenHashMap<AEKey, Variable> reserveVariables = new Object2ObjectLinkedOpenHashMap<>();
        int reserveIndex = 0;
        for (AEKey key : touchedKeys(request.variants(), request.target())) {
            Variable variable = model.addVariable("reserve_" + reserveIndex++)
                    .integer()
                    .lower(BigInteger.ZERO);
            if (key.equals(request.target())) {
                variable.level(targetReserve(
                        request.target(),
                        request.requestedAmount(),
                        request.quantityMode(),
                        request.available()));
            } else if (!request.available().unlimited(key)) {
                variable.upper(request.available().finiteAmount(key));
            }
            reserveVariables.put(key, variable);
            variables.add(variable);
        }

        Object2ObjectLinkedOpenHashMap<AEKey, Variable> missingVariables = new Object2ObjectLinkedOpenHashMap<>();
        int missingIndex = 0;
        for (AEKey sourceKey : request.sourceKeys()) {
            Variable variable = model.addVariable("missing_" + missingIndex++)
                    .integer()
                    .lower(BigInteger.ZERO);
            missingVariables.put(sourceKey, variable);
            variables.add(variable);
        }

        int conservationIndex = 0;
        for (Map.Entry<AEKey, Variable> reserve : reserveVariables.entrySet()) {
            AEKey key = reserve.getKey();
            Expression conservation = model.addExpression("conservation_" + conservationIndex++);
            firingVariables.forEach((variant, variable) -> {
                BigInteger coefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (coefficient.signum() != 0) {
                    conservation.set(variable, coefficient);
                }
            });
            conservation.set(reserve.getValue(), BigInteger.ONE);
            Variable missing = missingVariables.get(key);
            if (missing != null) {
                conservation.set(missing, BigInteger.ONE);
            }
            conservation.lower(key.equals(request.target()) ? request.requestedAmount() : BigInteger.ZERO);
        }

        Expression targetNet = model.addExpression("target_net");
        firingVariables.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(request.target(), BigInteger.ZERO);
            if (coefficient.signum() != 0) {
                targetNet.set(variable, coefficient);
            }
        });
        targetNet.lower(request.requiredTargetNet());

        expression(model, "missing_total", missingVariables.values()).weight(BigDecimal.ONE);
        return new DiagnosticModelData(
                model,
                List.copyOf(variables),
                firingVariables,
                reserveVariables,
                missingVariables);
    }

    private static Expression expression(ExpressionsBasedModel model,
                                         String name,
                                         Iterable<Variable> variables) {
        Expression expression = model.addExpression(name);
        for (Variable variable : variables) {
            expression.set(variable, BigInteger.ONE);
        }
        return expression;
    }

    private static TrinityAlgorithmResult<TrinityAcyclicPlan> buildPlan(
                                                                        TrinityCraftingTopology topology,
                                                                        SolvedModel solved,
                                                                        int states) {
        Int2IntMap positions = topologicalPositions(topology);
        ObjectArrayList<TrinityVariantFiring> executionOrder = new ObjectArrayList<>();
        solved.firings().entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<TrinityPatternVariant, BigInteger> entry) -> producerPosition(
                                topology,
                                positions,
                                entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> executionOrder.add(new TrinityVariantFiring(entry.getKey(), entry.getValue())));
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new Object2ObjectLinkedOpenHashMap<>();
        executionOrder.forEach(firing -> orderedFirings.put(firing.variant(), firing.count()));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> executable = verifyExecutionPrefix(
                executionOrder,
                solved.reserves());
        if (!executable.successful()) {
            return TrinityAlgorithmResult.failure(executable.diagnostic());
        }
        return TrinityAlgorithmResult.success(new TrinityAcyclicPlan(
                executionOrder,
                orderedFirings,
                solved.reserves(),
                solved.netChange(),
                states,
                TrinityPlanQuality.PROVED_OPTIMAL));
    }

    private static Set<AEKey> touchedKeys(List<TrinityPatternVariant> variants, AEKey target) {
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        keys.add(target);
        variants.forEach(variant -> {
            keys.addAll(variant.inputs().keySet());
            keys.addAll(variant.outputs().keySet());
        });
        return ObjectSets.unmodifiable(keys);
    }

    private static List<TrinityPatternVariant> targetReachableVariants(
                                                                       List<TrinityPatternVariant> variants,
                                                                       AEKey target) {
        ObjectArrayList<TrinityPatternVariant> ordered = new ObjectArrayList<>(variants);
        ordered.sort(Comparator.naturalOrder());
        Object2ObjectOpenHashMap<AEKey, ObjectArrayList<TrinityPatternVariant>> producersByOutput = new Object2ObjectOpenHashMap<>();
        for (TrinityPatternVariant variant : ordered) {
            variant.outputs().keySet().forEach(output -> producersByOutput
                    .computeIfAbsent(output, ignored -> new ObjectArrayList<>())
                    .add(variant));
        }

        ObjectArrayFIFOQueue<AEKey> pending = new ObjectArrayFIFOQueue<>();
        ObjectLinkedOpenHashSet<AEKey> visitedKeys = new ObjectLinkedOpenHashSet<>();
        ObjectLinkedOpenHashSet<TrinityPatternVariant> reachable = new ObjectLinkedOpenHashSet<>();
        pending.enqueue(target);
        while (!pending.isEmpty()) {
            AEKey required = pending.dequeue();
            if (!visitedKeys.add(required)) {
                continue;
            }
            List<TrinityPatternVariant> producers = producersByOutput.get(required);
            if (producers == null) {
                continue;
            }
            for (TrinityPatternVariant producer : producers) {
                if (reachable.add(producer)) {
                    producer.inputs().keySet().forEach(pending::enqueue);
                }
            }
        }
        ObjectArrayList<TrinityPatternVariant> result = new ObjectArrayList<>(reachable);
        result.sort(Comparator.naturalOrder());
        return ObjectLists.unmodifiable(result);
    }

    private static Set<AEKey> externalSourceKeys(
                                                 List<TrinityPatternVariant> variants,
                                                 TrinityPlanningInventory inventory) {
        ObjectLinkedOpenHashSet<AEKey> produced = new ObjectLinkedOpenHashSet<>();
        variants.forEach(variant -> produced.addAll(variant.outputs().keySet()));
        ObjectLinkedOpenHashSet<AEKey> sourceKeys = new ObjectLinkedOpenHashSet<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !produced.contains(key) && !inventory.unlimited(key))
                .forEach(sourceKeys::add));
        return ObjectSets.unmodifiable(sourceKeys);
    }

    private static Map<AEKey, BigInteger> sourceCapacity(
                                                         List<TrinityPatternVariant> variants,
                                                         TrinityPlanningInventory available) {
        ObjectLinkedOpenHashSet<AEKey> produced = new ObjectLinkedOpenHashSet<>();
        variants.forEach(variant -> produced.addAll(variant.outputs().keySet()));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> capacity = new Object2ObjectLinkedOpenHashMap<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !produced.contains(key) && !available.unlimited(key))
                .forEach(key -> capacity.putIfAbsent(key, available.finiteAmount(key))));
        return Collections.unmodifiableMap(capacity);
    }

    private static BigInteger sourceUpperBound(
                                               TrinityPatternVariant variant,
                                               BigInteger remainingFirings,
                                               Map<AEKey, BigInteger> sourceCapacity,
                                               Map<AEKey, BigInteger> fixedSourceConsumption) {
        BigInteger upper = remainingFirings;
        for (Map.Entry<AEKey, BigInteger> input : variant.inputs().entrySet()) {
            BigInteger capacity = sourceCapacity.get(input.getKey());
            if (capacity == null) {
                continue;
            }
            BigInteger remaining = capacity.subtract(
                    fixedSourceConsumption.getOrDefault(input.getKey(), BigInteger.ZERO));
            upper = upper.min(remaining.max(BigInteger.ZERO).divide(input.getValue()));
        }
        return upper;
    }

    private static void mergeSourceConsumption(
                                               Map<AEKey, BigInteger> consumption,
                                               TrinityPatternVariant variant,
                                               BigInteger count,
                                               Set<AEKey> sourceKeys) {
        if (count.signum() == 0) {
            return;
        }
        variant.inputs().forEach((key, amount) -> {
            if (sourceKeys.contains(key)) {
                consumption.merge(key, amount.multiply(count), BigInteger::add);
            }
        });
    }

    private static BigInteger requiredTargetNet(AEKey target,
                                                BigInteger requestedAmount,
                                                CraftingQuantityMode quantityMode,
                                                TrinityPlanningInventory available) {
        if (quantityMode == CraftingQuantityMode.NET_NEW) {
            return requestedAmount;
        }
        return requestedAmount
                .subtract(available.availableUpTo(target, requestedAmount))
                .max(BigInteger.ZERO)
                .max(BigInteger.ONE);
    }

    private static BigInteger targetReserve(AEKey target,
                                            BigInteger requestedAmount,
                                            CraftingQuantityMode quantityMode,
                                            TrinityPlanningInventory available) {
        return quantityMode == CraftingQuantityMode.NET_NEW ?
                BigInteger.ZERO :
                available.availableUpTo(target, requestedAmount);
    }

    private static TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExecutionPrefix(
                                                                                        List<TrinityVariantFiring> executionOrder,
                                                                                        Map<AEKey, BigInteger> reserves) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balance = new Object2ObjectLinkedOpenHashMap<>(reserves);
        for (TrinityVariantFiring firing : executionOrder) {
            for (Map.Entry<AEKey, BigInteger> input : firing.variant().inputs().entrySet()) {
                BigInteger required = input.getValue().multiply(firing.count());
                BigInteger present = balance.getOrDefault(input.getKey(), BigInteger.ZERO);
                if (present.compareTo(required) < 0) {
                    return failure(
                            TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                            NO_EXECUTABLE_ORDER_KEY,
                            Map.of(
                                    "key", input.getKey().toString(),
                                    "required", required.toString(),
                                    "available", present.toString()));
                }
                balance.put(input.getKey(), present.subtract(required));
            }
            firing.variant().outputs().forEach((key, amount) -> balance.merge(
                    key,
                    amount.multiply(firing.count()),
                    BigInteger::add));
        }
        balance.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return TrinityAlgorithmResult.success(Collections.unmodifiableMap(balance));
    }

    private static int producerPosition(TrinityCraftingTopology topology,
                                        Int2IntMap positions,
                                        TrinityPatternVariant variant) {
        int earliestOutput = Integer.MAX_VALUE;
        for (AEKey output : variant.outputs().keySet()) {
            int component = topology.componentByKey().getOrDefault(output, -1);
            if (component >= 0) {
                earliestOutput = Math.min(earliestOutput, positions.get(component));
            }
        }
        if (earliestOutput == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A Trinity acyclic firing output is absent from topology");
        }
        return earliestOutput;
    }

    private static Int2IntMap topologicalPositions(TrinityCraftingTopology topology) {
        Int2IntMap positions = new Int2IntOpenHashMap();
        positions.defaultReturnValue(-1);
        for (int index = 0; index < topology.topologicalOrder().size(); index++) {
            positions.put(topology.topologicalOrder().get(index).intValue(), index);
        }
        return positions;
    }

    private static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static <T> TrinityAlgorithmResult<T> insufficient(
                                                              AEKey key,
                                                              BigInteger required) {
        return failure(
                TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                INSUFFICIENT_INPUT_KEY,
                Map.of(
                        "key", key.toString(),
                        "required", required.toString(),
                        "available", "0"));
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                INEXACT_RESULT_KEY,
                Map.of("constraint", constraint, "value", value));
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

    /**
     * One exact external-source requirement selected by the relaxed diagnostic model.
     *
     * @param required  total source input required by the selected route
     * @param allocated actual captured inventory allocated to that requirement
     * @param missing   diagnostic-only virtual reserve
     */
    public record InputRequirement(BigInteger required, BigInteger allocated, BigInteger missing) {

        public InputRequirement {
            if (required.signum() <= 0 ||
                    allocated.signum() < 0 || missing.signum() < 0 || !required.equals(allocated.add(missing))) {
                throw new IllegalArgumentException(
                        "A Trinity shortage requirement must satisfy required = allocated + missing");
            }
        }
    }

    /**
     * Exact non-executable route evidence. Missing values are never included in {@link #actualReserves()}.
     *
     * @param firings           stable selected aggregate firing vector
     * @param actualReserves    actual captured inventory reserved by the diagnostic route
     * @param inputRequirements complete external-source requirements for the selected route
     * @param netChange         exact firing-only net change
     * @param statesVisited     sequential optimization passes consumed
     */
    public record ShortageEvidence(
                                   Map<TrinityPatternVariant, BigInteger> firings,
                                   Map<AEKey, BigInteger> actualReserves,
                                   Map<AEKey, InputRequirement> inputRequirements,
                                   Map<AEKey, BigInteger> netChange,
                                   int statesVisited) {

        public ShortageEvidence {
            if (firings.isEmpty() || inputRequirements.isEmpty() || statesVisited <= 0 ||
                    inputRequirements.values().stream().noneMatch(requirement -> requirement.missing().signum() > 0)) {
                throw new IllegalArgumentException("A Trinity shortage evidence requires one exact missing route");
            }
            firings = Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(firings));
            actualReserves = Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(actualReserves));
            inputRequirements = Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(inputRequirements));
            netChange = Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(netChange));
        }
    }

    private sealed interface ModelPass permits FeasibilityPass, HintFeasibilityPass, ExternalPass, FiringPass, IdentityPass {}

    private enum FeasibilityPass implements ModelPass {
        INSTANCE
    }

    private record HintFeasibilityPass(Set<TrinityPatternIdentity> selectedPatterns) implements ModelPass {}

    private enum ExternalPass implements ModelPass {
        INSTANCE
    }

    private record FiringPass(BigInteger fixedExternal) implements ModelPass {}

    private record IdentityPass(
                                BigInteger fixedExternal,
                                BigInteger fixedFirings,
                                Map<TrinityPatternVariant, BigInteger> fixedPrefix,
                                TrinityPatternVariant preferred)
            implements ModelPass {}

    private record ModelRequest(
                                List<TrinityPatternVariant> variants,
                                AEKey target,
                                BigInteger requestedAmount,
                                BigInteger requiredTargetNet,
                                CraftingQuantityMode quantityMode,
                                TrinityPlanningInventory available,
                                ModelPass pass) {}

    /**
     * Request-private immutable coefficient template copied for each lexicographic pass. The mutable ojAlgo copies are
     * never shared across requests or threads.
     */
    private record AcyclicModelTemplate(
                                        ExpressionsBasedModel baseModel,
                                        int variableCount,
                                        Object2IntMap<TrinityPatternVariant> firingIndexes,
                                        Object2IntMap<AEKey> reserveIndexes) {

        private ModelData forPass(ModelPass pass) {
            ExpressionsBasedModel model = this.baseModel.copy();
            ObjectArrayList<Variable> variables = new ObjectArrayList<>(this.variableCount);
            for (int index = 0; index < this.variableCount; index++) {
                variables.add(model.getVariable(index));
            }
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, Variable> firingVariables = new Object2ObjectLinkedOpenHashMap<>();
            Object2IntMaps.fastForEach(this.firingIndexes, entry -> firingVariables.put(entry.getKey(), model.getVariable(entry.getIntValue())));
            Object2ObjectLinkedOpenHashMap<AEKey, Variable> reserveVariables = new Object2ObjectLinkedOpenHashMap<>();
            Object2IntMaps.fastForEach(this.reserveIndexes, entry -> reserveVariables.put(entry.getKey(), model.getVariable(entry.getIntValue())));

            Expression externalTotal = model.getExpression("external_total");
            Expression firingTotal = model.getExpression("firing_total");
            if (pass instanceof FeasibilityPass) {
                // Zero objective: obtain any integer witness and verify it exactly after the solve.
            } else if (pass instanceof HintFeasibilityPass hintPass) {
                firingVariables.forEach((variant, variable) -> {
                    if (!hintPass.selectedPatterns().contains(variant.patternIdentity())) {
                        variable.upper(BigInteger.ZERO);
                    }
                });
            } else if (pass instanceof ExternalPass) {
                externalTotal.weight(BigDecimal.ONE);
            } else if (pass instanceof FiringPass firingPass) {
                externalTotal.level(firingPass.fixedExternal());
                firingTotal.weight(BigDecimal.ONE);
            } else if (pass instanceof IdentityPass identityPass) {
                externalTotal.level(identityPass.fixedExternal());
                firingTotal.level(identityPass.fixedFirings());
                identityPass.fixedPrefix()
                        .forEach((variant, value) -> firingVariables.get(variant).level(value));
                firingVariables.get(identityPass.preferred()).weight(BigDecimal.ONE);
            } else {
                throw new IllegalStateException("Unknown Trinity acyclic optimisation pass");
            }
            return new ModelData(
                    model,
                    List.copyOf(variables),
                    Collections.unmodifiableMap(firingVariables),
                    Collections.unmodifiableMap(reserveVariables));
        }
    }

    private record ModelData(
                             ExpressionsBasedModel model,
                             List<Variable> variables,
                             Map<TrinityPatternVariant, Variable> firingVariables,
                             Map<AEKey, Variable> reserveVariables) {

        private SolvedModel decode(List<BigInteger> values) {
            Object2ObjectLinkedOpenHashMap<Variable, BigInteger> byVariable = new Object2ObjectLinkedOpenHashMap<>();
            for (int index = 0; index < this.variables.size(); index++) {
                byVariable.put(this.variables.get(index), values.get(index));
            }
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>();
            this.firingVariables.forEach((variant, variable) -> {
                BigInteger count = byVariable.get(variable);
                if (count.signum() > 0) {
                    firings.put(variant, count);
                }
            });
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> reserves = new Object2ObjectLinkedOpenHashMap<>();
            this.reserveVariables.forEach((key, variable) -> {
                BigInteger amount = byVariable.get(variable);
                if (amount.signum() > 0) {
                    reserves.put(key, amount);
                }
            });
            return new SolvedModel(
                    Collections.unmodifiableMap(firings),
                    Collections.unmodifiableMap(reserves),
                    Map.of());
        }
    }

    private record SolvedModel(
                               Map<TrinityPatternVariant, BigInteger> firings,
                               Map<AEKey, BigInteger> reserves,
                               Map<AEKey, BigInteger> netChange) {}

    private record SolvedPass(SolvedModel model, boolean objectiveProved) {}

    private record DiagnosticModelRequest(
                                          List<TrinityPatternVariant> variants,
                                          Set<AEKey> sourceKeys,
                                          AEKey target,
                                          BigInteger requestedAmount,
                                          BigInteger requiredTargetNet,
                                          CraftingQuantityMode quantityMode,
                                          TrinityPlanningInventory available) {}

    private record DiagnosticModelData(
                                       ExpressionsBasedModel model,
                                       List<Variable> variables,
                                       Map<TrinityPatternVariant, Variable> firingVariables,
                                       Map<AEKey, Variable> reserveVariables,
                                       Map<AEKey, Variable> missingVariables) {

        private DiagnosticSolvedModel decode(List<BigInteger> values) {
            Object2ObjectLinkedOpenHashMap<Variable, BigInteger> byVariable = new Object2ObjectLinkedOpenHashMap<>();
            for (int index = 0; index < this.variables.size(); index++) {
                byVariable.put(this.variables.get(index), values.get(index));
            }
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = decodePositive(
                    this.firingVariables,
                    byVariable);
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> actualReserves = decodePositive(
                    this.reserveVariables,
                    byVariable);
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missing = decodePositive(
                    this.missingVariables,
                    byVariable);
            return new DiagnosticSolvedModel(
                    Collections.unmodifiableMap(firings),
                    Collections.unmodifiableMap(actualReserves),
                    Collections.unmodifiableMap(missing),
                    Map.of());
        }

        private static <K> Object2ObjectLinkedOpenHashMap<K, BigInteger> decodePositive(
                                                                                        Map<K, Variable> variables,
                                                                                        Map<Variable, BigInteger> values) {
            Object2ObjectLinkedOpenHashMap<K, BigInteger> decoded = new Object2ObjectLinkedOpenHashMap<>();
            variables.forEach((key, variable) -> {
                BigInteger amount = values.get(variable);
                if (amount.signum() > 0) {
                    decoded.put(key, amount);
                }
            });
            return decoded;
        }
    }

    private record DiagnosticSolvedModel(
                                         Map<TrinityPatternVariant, BigInteger> firings,
                                         Map<AEKey, BigInteger> actualReserves,
                                         Map<AEKey, BigInteger> missing,
                                         Map<AEKey, BigInteger> netChange) {}

    private record UniformBindingFamily(
                                        List<TrinityPatternVariant> variants,
                                        BigInteger inputPerFiring,
                                        BigInteger outputPerFiring) {

        private static Optional<UniformBindingFamily> tryCreate(
                                                                List<TrinityPatternVariant> variants,
                                                                AEKey target) {
            TrinityPatternVariant first = variants.getFirst();
            if (first.inputs().size() != 1 || first.outputs().size() != 1 ||
                    !first.outputs().containsKey(target) || first.inputs().containsKey(target)) {
                return Optional.empty();
            }
            BigInteger inputPerFiring = first.inputs().values().iterator().next();
            BigInteger outputPerFiring = first.outputs().get(target);
            for (TrinityPatternVariant variant : variants) {
                if (!variant.patternIdentity().equals(first.patternIdentity()) ||
                        variant.inputs().size() != 1 || variant.outputs().size() != 1 ||
                        variant.inputs().containsKey(target) ||
                        !variant.inputs().values().iterator().next().equals(inputPerFiring) ||
                        !outputPerFiring.equals(variant.outputs().get(target))) {
                    return Optional.empty();
                }
            }
            return Optional.of(new UniformBindingFamily(
                    List.copyOf(variants),
                    inputPerFiring,
                    outputPerFiring));
        }
    }

    private static final class SearchBudget {

        private final int limit;
        private final TrinityPlanningControl control;
        private int used;

        private SearchBudget(int limit, TrinityPlanningControl control) {
            this.limit = limit;
            this.control = control;
        }

        private boolean consume() {
            if (this.used >= this.limit) {
                return false;
            }
            this.used = Math.incrementExact(this.used);
            this.control.recordRouteStates(1);
            return true;
        }

        private int used() {
            return this.used;
        }
    }
}
