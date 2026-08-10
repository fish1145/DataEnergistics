package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.equalization.ExactWaterFillingTowerEnergyEqualizer;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyAllocationLimiter;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizationPlan;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizationSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizer;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergySinkAllocation;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergySourceAllocation;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes one atomic-as-possible, two-phase FE equalization transaction for a primary-grid domain.
 */
public final class CompensatingTowerEnergyTransaction {

    /**
     * Maximum endpoint-level failures retained in one rate-limited grid diagnostic.
     */
    private static final int MAX_ISOLATION_DETAILS = 4;

    /**
     * Bounds repeated capability simulations when a storage exposes a coarser transfer quantum than one FE.
     */
    private static final int MAX_PREFLIGHT_PASSES = 64;

    /**
     * Exact planner kept separate from capability mutation.
     */
    private final TowerEnergyEqualizer equalizer;

    /**
     * Creates the production executor with the default exact planner.
     */
    public CompensatingTowerEnergyTransaction() {
        this(new ExactWaterFillingTowerEnergyEqualizer());
    }

    /**
     * Creates an executor with an explicit planner for direct logic testing.
     *
     * @param equalizer immutable-snapshot planner
     */
    public CompensatingTowerEnergyTransaction(TowerEnergyEqualizer equalizer) {
        this.equalizer = equalizer;
    }

    /**
     * Freezes, plans, simulates, and then executes one domain transaction.
     *
     * <p>
     * No real mutation occurs when the frozen topology is already balanced or when any preflight simulation cannot
     * satisfy the complete plan. Runtime short writes are compensated back to sources; unrecoverable FE is returned
     * as quarantined energy.
     * </p>
     *
     * @param endpoints stable ordered endpoint topology
     * @return immutable execution result
     */
    public TowerEnergyTransactionResult execute(List<TowerEnergyTransferEndpoint> endpoints) {
        List<TowerEnergyTransferEndpoint> orderedEndpoints = List.copyOf(endpoints);
        Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById = indexEndpoints(orderedEndpoints);
        ArrayList<TowerEnergyEndpointSnapshot> snapshots = new ArrayList<>(orderedEndpoints.size());
        ArrayList<String> isolationDetails = new ArrayList<>(MAX_ISOLATION_DETAILS);
        int isolatedEndpointCount = 0;
        for (TowerEnergyTransferEndpoint endpoint : orderedEndpoints) {
            try {
                snapshots.add(endpoint.freeze());
            } catch (RuntimeException exception) {
                isolatedEndpointCount++;
                if (isolationDetails.size() < MAX_ISOLATION_DETAILS) {
                    isolationDetails.add(endpoint.description() + ": " + conciseMessage(exception));
                }
            }
        }
        String isolationFailure = isolationFailure(isolatedEndpointCount, isolationDetails);

        TowerEnergyEqualizationPlan plan;
        try {
            plan = this.equalizer.plan(new TowerEnergyEqualizationSnapshot(snapshots));
        } catch (RuntimeException exception) {
            return failed(snapshots, 0, 0, 0, false,
                    combineFailures("PLAN_FAILED: " + conciseMessage(exception), isolationFailure));
        }
        if (plan.isEmpty()) {
            return new TowerEnergyTransactionResult(snapshots, 0, 0, 0, false, isolationFailure);
        }

        long requestedFe = saturatingLong(plan.totalAmount());
        PreflightResult preflight = preflight(plan, endpointsById);
        if (!preflight.failure().isEmpty()) {
            return failed(snapshots, requestedFe, 0, 0, false,
                    combineFailures(preflight.failure(), isolationFailure));
        }
        plan = preflight.plan();
        long plannedFe = saturatingLong(plan.totalAmount());
        if (plan.isEmpty()) {
            return new TowerEnergyTransactionResult(snapshots, 0, 0, 0, false, isolationFailure);
        }

        Set<TowerEnergyTransferEndpoint> mutatedEndpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<Extraction> extractions = new ArrayList<>(plan.sources().size());
        BigInteger extractedTotal = BigInteger.ZERO;
        for (TowerEnergySourceAllocation source : plan.sources()) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(source.endpoint());
            long extracted;
            try {
                extracted = endpoint.extract(source.amount());
            } catch (RuntimeException exception) {
                CompensationResult compensation = compensate(extractions, extractedTotal, mutatedEndpoints);
                publishMutations(mutatedEndpoints);
                return failed(
                        snapshots,
                        plannedFe,
                        0,
                        saturatingLong(compensation.quarantined()),
                        !mutatedEndpoints.isEmpty(),
                        combineFailures(
                                "SOURCE_MUTATION_FAILED: " + endpoint.description() + ": " + conciseMessage(exception),
                                isolationFailure));
            }
            if (extracted > 0) {
                extractions.add(new Extraction(endpoint, extracted));
                extractedTotal = extractedTotal.add(BigInteger.valueOf(extracted));
                mutatedEndpoints.add(endpoint);
            }
        }
        if (extractedTotal.signum() == 0) {
            publishMutations(mutatedEndpoints);
            return failed(snapshots, plannedFe, 0, 0, !mutatedEndpoints.isEmpty(),
                    combineFailures("SOURCE_NO_PROGRESS", isolationFailure));
        }

        List<TowerEnergySinkAllocation> executableSinks = TowerEnergyAllocationLimiter.limitSinks(
                plan.sinks(), extractedTotal);
        long executableFe = saturatingLong(extractedTotal);
        BigInteger insertedTotal = BigInteger.ZERO;
        for (TowerEnergySinkAllocation sink : executableSinks) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(sink.endpoint());
            long inserted;
            try {
                inserted = endpoint.insert(sink.amount());
            } catch (RuntimeException exception) {
                BigInteger undelivered = extractedTotal.subtract(insertedTotal);
                CompensationResult compensation = compensate(extractions, undelivered, mutatedEndpoints);
                publishMutations(mutatedEndpoints);
                return failed(
                        snapshots,
                        executableFe,
                        saturatingLong(insertedTotal),
                        saturatingLong(compensation.quarantined()),
                        !mutatedEndpoints.isEmpty(),
                        combineFailures(
                                "SINK_MUTATION_FAILED: " + endpoint.description() + ": " + conciseMessage(exception),
                                isolationFailure));
            }
            if (inserted > 0) {
                insertedTotal = insertedTotal.add(BigInteger.valueOf(inserted));
                mutatedEndpoints.add(endpoint);
            }
            if (inserted != sink.amount()) {
                BigInteger undelivered = extractedTotal.subtract(insertedTotal);
                CompensationResult compensation = compensate(extractions, undelivered, mutatedEndpoints);
                publishMutations(mutatedEndpoints);
                return failed(
                        snapshots,
                        executableFe,
                        saturatingLong(insertedTotal),
                        saturatingLong(compensation.quarantined()),
                        !mutatedEndpoints.isEmpty(),
                        combineFailures("SINK_SHORT_WRITE: " + endpoint.description(), isolationFailure));
            }
        }

        publishMutations(mutatedEndpoints);
        return new TowerEnergyTransactionResult(
                snapshots,
                executableFe,
                saturatingLong(insertedTotal),
                0,
                !mutatedEndpoints.isEmpty(),
                isolationFailure);
    }

    /**
     * Indexes stable identities and fails before any query when the topology contains a duplicate.
     */
    private static Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> indexEndpoints(
                                                                                          List<TowerEnergyTransferEndpoint> endpoints) {
        LinkedHashMap<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> result = new LinkedHashMap<>();
        for (TowerEnergyTransferEndpoint endpoint : endpoints) {
            TowerEnergyTransferEndpoint previous = result.putIfAbsent(endpoint.endpoint(), endpoint);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate tower energy endpoint " + endpoint.endpoint());
            }
        }
        return result;
    }

    /**
     * Reduces a plan to a conserved subplan that every selected route simulates exactly.
     */
    private static PreflightResult preflight(
                                             TowerEnergyEqualizationPlan plan,
                                             Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        try {
            List<TowerEnergySourceAllocation> sources = normalizeSources(plan.sources(), endpointsById);
            List<TowerEnergySinkAllocation> sinks = normalizeSinks(plan.sinks(), endpointsById);
            BigInteger sourceAmount = sumSources(sources);
            BigInteger sinkAmount = sumSinks(sinks);
            if (sourceAmount.equals(sinkAmount)) {
                return sourceAmount.signum() == 0 ? PreflightResult.success(TowerEnergyEqualizationPlan.empty()) : PreflightResult.success(new TowerEnergyEqualizationPlan(sources, sinks));
            }
            if (!hasExplicitQuantum(sources, sinks, endpointsById)) {
                return reconcileUnknownQuantums(sources, sinks, endpointsById);
            }
            return alignKnownQuantums(sources, sinks, endpointsById);
        } catch (RuntimeException exception) {
            return PreflightResult.failure("PREFLIGHT_FAILED: " + conciseMessage(exception));
        }
    }

    /**
     * Retains the bounded legacy reconciliation for capability implementations that expose no transfer-unit contract.
     */
    private static PreflightResult reconcileUnknownQuantums(
                                                            List<TowerEnergySourceAllocation> initialSources,
                                                            List<TowerEnergySinkAllocation> initialSinks,
                                                            Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        List<TowerEnergySourceAllocation> sources = initialSources;
        List<TowerEnergySinkAllocation> sinks = initialSinks;
        for (int pass = 0; pass < MAX_PREFLIGHT_PASSES; pass++) {
            BigInteger sourceAmount = sumSources(sources);
            BigInteger sinkAmount = sumSinks(sinks);
            int comparison = sourceAmount.compareTo(sinkAmount);
            if (comparison == 0) {
                if (sourceAmount.signum() == 0) {
                    return PreflightResult.success(TowerEnergyEqualizationPlan.empty());
                }
                return PreflightResult.success(new TowerEnergyEqualizationPlan(sources, sinks));
            }
            if (sourceAmount.signum() == 0 || sinkAmount.signum() == 0) {
                return PreflightResult.success(TowerEnergyEqualizationPlan.empty());
            }
            if (comparison > 0) {
                sources = normalizeSources(
                        TowerEnergyAllocationLimiter.limitSources(sources, sinkAmount), endpointsById);
            } else {
                sinks = normalizeSinks(
                        TowerEnergyAllocationLimiter.limitSinks(sinks, sourceAmount), endpointsById);
            }
        }
        return PreflightResult.failure("PREFLIGHT_DID_NOT_STABILIZE_AFTER_" + MAX_PREFLIGHT_PASSES + "_PASSES");
    }

    /**
     * Aligns explicitly quantized routes in a finite pass before any endpoint is mutated.
     *
     * <p>
     * Matching source and sink quantum groups are conserved first. Only unmatched residual groups are aligned to their
     * least common multiple, so exact FE tails that exist on both sides are not discarded while cross-quantum transfer
     * remains finite and deterministic.
     * </p>
     */
    private static PreflightResult alignKnownQuantums(
                                                      List<TowerEnergySourceAllocation> sources,
                                                      List<TowerEnergySinkAllocation> sinks,
                                                      Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        List<SourceQuantumGroup> sourceGroups = sourceQuantumGroups(sources, endpointsById);
        List<SinkQuantumGroup> sinkGroups = sinkQuantumGroups(sinks, endpointsById);
        QuantumGroupTargets targets = quantumGroupTargets(sourceGroups, sinkGroups);
        BigInteger transferAmount = sumGroupTargets(targets.sourceTargets());
        BigInteger sinkAmount = sumGroupTargets(targets.sinkTargets());
        if (!transferAmount.equals(sinkAmount)) {
            throw new TowerEnergyTransferException("Aligned quantum-group totals are not conserved");
        }
        if (transferAmount.signum() == 0) {
            return PreflightResult.success(TowerEnergyEqualizationPlan.empty());
        }

        List<TowerEnergySourceAllocation> alignedSources = allocateSourceGroups(
                sources, sourceGroups, targets.sourceTargets());
        List<TowerEnergySinkAllocation> alignedSinks = allocateSinkGroups(
                sinks, sinkGroups, targets.sinkTargets());
        verifyExecutable(alignedSources, alignedSinks, endpointsById);
        return PreflightResult.success(new TowerEnergyEqualizationPlan(alignedSources, alignedSinks));
    }

    /**
     * Normalizes every withdrawal to a request that its selected route can execute exactly.
     */
    private static List<TowerEnergySourceAllocation> normalizeSources(
                                                                      List<TowerEnergySourceAllocation> sources,
                                                                      Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        ArrayList<TowerEnergySourceAllocation> normalized = new ArrayList<>(sources.size());
        for (TowerEnergySourceAllocation source : sources) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(source.endpoint());
            long amount = normalizeAmount(source.amount(), endpoint, false);
            if (amount > 0) {
                normalized.add(new TowerEnergySourceAllocation(source.endpoint(), amount));
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * Normalizes every deposit to a request that its selected route can execute exactly.
     */
    private static List<TowerEnergySinkAllocation> normalizeSinks(
                                                                  List<TowerEnergySinkAllocation> sinks,
                                                                  Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        ArrayList<TowerEnergySinkAllocation> normalized = new ArrayList<>(sinks.size());
        for (TowerEnergySinkAllocation sink : sinks) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(sink.endpoint());
            long amount = normalizeAmount(sink.amount(), endpoint, true);
            if (amount > 0) {
                normalized.add(new TowerEnergySinkAllocation(sink.endpoint(), amount));
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * Validates every operation-specific quantum and reports whether the plan contains an explicit coarse route.
     */
    private static boolean hasExplicitQuantum(
                                              List<TowerEnergySourceAllocation> sources,
                                              List<TowerEnergySinkAllocation> sinks,
                                              Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        boolean explicit = false;
        for (TowerEnergySourceAllocation source : sources) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(source.endpoint());
            long quantum = validateQuantum(endpoint.extractionQuantum(), endpoint, "extraction");
            explicit |= quantum > 1;
        }
        for (TowerEnergySinkAllocation sink : sinks) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(sink.endpoint());
            long quantum = validateQuantum(endpoint.insertionQuantum(), endpoint, "insertion");
            explicit |= quantum > 1;
        }
        return explicit;
    }

    /**
     * Groups source allocations by their proven quantum before aggregate common-unit alignment.
     */
    private static List<SourceQuantumGroup> sourceQuantumGroups(
                                                                List<TowerEnergySourceAllocation> sources,
                                                                Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        LinkedHashMap<Long, ArrayList<TowerEnergySourceAllocation>> allocationsByQuantum = new LinkedHashMap<>();
        for (TowerEnergySourceAllocation source : sources) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(source.endpoint());
            long quantum = validateQuantum(endpoint.extractionQuantum(), endpoint, "extraction");
            validateAlignedAmount(source.amount(), quantum, endpoint, "extraction");
            allocationsByQuantum.computeIfAbsent(quantum, ignored -> new ArrayList<>()).add(source);
        }

        ArrayList<SourceQuantumGroup> groups = new ArrayList<>(allocationsByQuantum.size());
        for (Map.Entry<Long, ArrayList<TowerEnergySourceAllocation>> entry : allocationsByQuantum.entrySet()) {
            groups.add(new SourceQuantumGroup(
                    entry.getKey(), List.copyOf(entry.getValue()), sumSources(entry.getValue())));
        }
        return List.copyOf(groups);
    }

    /**
     * Groups sink allocations by their proven quantum before aggregate common-unit alignment.
     */
    private static List<SinkQuantumGroup> sinkQuantumGroups(
                                                            List<TowerEnergySinkAllocation> sinks,
                                                            Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        LinkedHashMap<Long, ArrayList<TowerEnergySinkAllocation>> allocationsByQuantum = new LinkedHashMap<>();
        for (TowerEnergySinkAllocation sink : sinks) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(sink.endpoint());
            long quantum = validateQuantum(endpoint.insertionQuantum(), endpoint, "insertion");
            validateAlignedAmount(sink.amount(), quantum, endpoint, "insertion");
            allocationsByQuantum.computeIfAbsent(quantum, ignored -> new ArrayList<>()).add(sink);
        }

        ArrayList<SinkQuantumGroup> groups = new ArrayList<>(allocationsByQuantum.size());
        for (Map.Entry<Long, ArrayList<TowerEnergySinkAllocation>> entry : allocationsByQuantum.entrySet()) {
            groups.add(new SinkQuantumGroup(
                    entry.getKey(), List.copyOf(entry.getValue()), sumSinks(entry.getValue())));
        }
        return List.copyOf(groups);
    }

    /**
     * Allocates the conserved source total across aligned quantum groups and restores original endpoint order.
     */
    private static List<TowerEnergySourceAllocation> allocateSourceGroups(
                                                                          List<TowerEnergySourceAllocation> original,
                                                                          List<SourceQuantumGroup> groups,
                                                                          Map<Long, BigInteger> targetsByQuantum) {
        LinkedHashMap<TowerEnergyEndpointId, Long> amountsByEndpoint = new LinkedHashMap<>();
        for (SourceQuantumGroup group : groups) {
            BigInteger groupAmount = targetsByQuantum.getOrDefault(group.quantum(), BigInteger.ZERO);
            for (TowerEnergySourceAllocation allocation : limitSourceGroup(group, groupAmount)) {
                amountsByEndpoint.put(allocation.endpoint(), allocation.amount());
            }
        }

        ArrayList<TowerEnergySourceAllocation> aligned = new ArrayList<>(amountsByEndpoint.size());
        for (TowerEnergySourceAllocation allocation : original) {
            Long amount = amountsByEndpoint.get(allocation.endpoint());
            if (amount != null) {
                aligned.add(new TowerEnergySourceAllocation(allocation.endpoint(), amount));
            }
        }
        return List.copyOf(aligned);
    }

    /**
     * Allocates the conserved sink total proportionally across aligned quantum groups and restores endpoint order.
     */
    private static List<TowerEnergySinkAllocation> allocateSinkGroups(
                                                                      List<TowerEnergySinkAllocation> original,
                                                                      List<SinkQuantumGroup> groups,
                                                                      Map<Long, BigInteger> targetsByQuantum) {
        LinkedHashMap<TowerEnergyEndpointId, Long> amountsByEndpoint = new LinkedHashMap<>();
        for (SinkQuantumGroup group : groups) {
            BigInteger groupAmount = targetsByQuantum.getOrDefault(group.quantum(), BigInteger.ZERO);
            for (TowerEnergySinkAllocation allocation : limitSinkGroup(group, groupAmount)) {
                amountsByEndpoint.put(allocation.endpoint(), allocation.amount());
            }
        }

        ArrayList<TowerEnergySinkAllocation> aligned = new ArrayList<>(amountsByEndpoint.size());
        for (TowerEnergySinkAllocation allocation : original) {
            Long amount = amountsByEndpoint.get(allocation.endpoint());
            if (amount != null) {
                aligned.add(new TowerEnergySinkAllocation(allocation.endpoint(), amount));
            }
        }
        return List.copyOf(aligned);
    }

    /**
     * Limits one source group in native quantum units so no endpoint receives a partial native unit.
     */
    private static List<TowerEnergySourceAllocation> limitSourceGroup(
                                                                      SourceQuantumGroup group,
                                                                      BigInteger groupAmount) {
        BigInteger quantum = BigInteger.valueOf(group.quantum());
        validateGroupAmount(groupAmount, group.amount(), quantum, "source");
        ArrayList<TowerEnergySourceAllocation> units = new ArrayList<>(group.allocations().size());
        for (TowerEnergySourceAllocation allocation : group.allocations()) {
            units.add(new TowerEnergySourceAllocation(
                    allocation.endpoint(), allocation.amount() / group.quantum()));
        }
        List<TowerEnergySourceAllocation> limited = TowerEnergyAllocationLimiter.limitSources(
                units, groupAmount.divide(quantum));
        ArrayList<TowerEnergySourceAllocation> result = new ArrayList<>(limited.size());
        for (TowerEnergySourceAllocation allocation : limited) {
            result.add(new TowerEnergySourceAllocation(
                    allocation.endpoint(), Math.multiplyExact(allocation.amount(), group.quantum())));
        }
        return List.copyOf(result);
    }

    /**
     * Limits one sink group in native quantum units while retaining largest-remainder fairness inside the group.
     */
    private static List<TowerEnergySinkAllocation> limitSinkGroup(
                                                                  SinkQuantumGroup group,
                                                                  BigInteger groupAmount) {
        BigInteger quantum = BigInteger.valueOf(group.quantum());
        validateGroupAmount(groupAmount, group.amount(), quantum, "sink");
        ArrayList<TowerEnergySinkAllocation> units = new ArrayList<>(group.allocations().size());
        for (TowerEnergySinkAllocation allocation : group.allocations()) {
            units.add(new TowerEnergySinkAllocation(
                    allocation.endpoint(), allocation.amount() / group.quantum()));
        }
        List<TowerEnergySinkAllocation> limited = TowerEnergyAllocationLimiter.limitSinks(
                units, groupAmount.divide(quantum));
        ArrayList<TowerEnergySinkAllocation> result = new ArrayList<>(limited.size());
        for (TowerEnergySinkAllocation allocation : limited) {
            result.add(new TowerEnergySinkAllocation(
                    allocation.endpoint(), Math.multiplyExact(allocation.amount(), group.quantum())));
        }
        return List.copyOf(result);
    }

    /**
     * Rejects an impossible group target before it can be split between individual endpoints.
     */
    private static void validateGroupAmount(
                                            BigInteger amount,
                                            BigInteger available,
                                            BigInteger quantum,
                                            String operation) {
        if (amount.signum() < 0 || amount.compareTo(available) > 0 || amount.remainder(quantum).signum() != 0) {
            throw new TowerEnergyTransferException("Invalid aligned " + operation + " quantum-group amount");
        }
    }

    /**
     * Matches equal-quantum groups first, then aligns only unmatched residual groups to a common cross-group unit.
     */
    private static QuantumGroupTargets quantumGroupTargets(
                                                           List<SourceQuantumGroup> sourceGroups,
                                                           List<SinkQuantumGroup> sinkGroups) {
        LinkedHashMap<Long, SinkQuantumGroup> sinksByQuantum = new LinkedHashMap<>();
        LinkedHashMap<Long, BigInteger> sourceTargets = zeroSourceTargets(sourceGroups);
        LinkedHashMap<Long, BigInteger> sinkTargets = zeroSinkTargets(sinkGroups);
        LinkedHashMap<Long, BigInteger> sourceResiduals = new LinkedHashMap<>();
        LinkedHashMap<Long, BigInteger> sinkResiduals = new LinkedHashMap<>();
        for (SinkQuantumGroup group : sinkGroups) {
            sinksByQuantum.put(group.quantum(), group);
        }

        for (SourceQuantumGroup sourceGroup : sourceGroups) {
            SinkQuantumGroup sinkGroup = sinksByQuantum.get(sourceGroup.quantum());
            BigInteger matched = sinkGroup == null ? BigInteger.ZERO : sourceGroup.amount().min(sinkGroup.amount());
            sourceTargets.put(sourceGroup.quantum(), matched);
            if (sinkGroup != null) {
                sinkTargets.put(sinkGroup.quantum(), matched);
                putPositive(sinkResiduals, sinkGroup.quantum(), sinkGroup.amount().subtract(matched));
            }
            putPositive(sourceResiduals, sourceGroup.quantum(), sourceGroup.amount().subtract(matched));
        }
        for (SinkQuantumGroup sinkGroup : sinkGroups) {
            if (!sourceTargets.containsKey(sinkGroup.quantum())) {
                putPositive(sinkResiduals, sinkGroup.quantum(), sinkGroup.amount());
            }
        }
        if (sourceResiduals.isEmpty() || sinkResiduals.isEmpty()) {
            return new QuantumGroupTargets(sourceTargets, sinkTargets);
        }

        BigInteger crossQuantum = residualCommonQuantum(sourceResiduals, sinkResiduals);
        LinkedHashMap<Long, BigInteger> sourceCrossCapacities = crossCapacities(sourceResiduals, crossQuantum);
        LinkedHashMap<Long, BigInteger> sinkCrossCapacities = crossCapacities(sinkResiduals, crossQuantum);
        BigInteger crossAmount = sumGroupTargets(sourceCrossCapacities)
                .min(sumGroupTargets(sinkCrossCapacities));
        if (crossAmount.signum() == 0) {
            return new QuantumGroupTargets(sourceTargets, sinkTargets);
        }

        allocateSourceCross(
                sourceGroups, sourceCrossCapacities, crossAmount, sourceTargets);
        Map<Long, BigInteger> sinkCrossTargets = apportionSinkCross(
                sinkGroups, sinkCrossCapacities, crossAmount, crossQuantum);
        for (Map.Entry<Long, BigInteger> entry : sinkCrossTargets.entrySet()) {
            sinkTargets.merge(entry.getKey(), entry.getValue(), BigInteger::add);
        }
        return new QuantumGroupTargets(sourceTargets, sinkTargets);
    }

    /**
     * Allocates common cross-group chunks to source groups in their stable priority order.
     */
    private static void allocateSourceCross(
                                            List<SourceQuantumGroup> groups,
                                            Map<Long, BigInteger> capacities,
                                            BigInteger crossAmount,
                                            Map<Long, BigInteger> targets) {
        BigInteger remaining = crossAmount;
        for (SourceQuantumGroup group : groups) {
            if (remaining.signum() == 0) {
                break;
            }
            BigInteger assigned = capacities.getOrDefault(group.quantum(), BigInteger.ZERO).min(remaining);
            targets.merge(group.quantum(), assigned, BigInteger::add);
            remaining = remaining.subtract(assigned);
        }
        if (remaining.signum() != 0) {
            throw new TowerEnergyTransferException("Aligned source quantum groups could not provide the cross total");
        }
    }

    /**
     * Applies largest-remainder apportionment to sink residual groups in common cross-quantum units.
     */
    private static Map<Long, BigInteger> apportionSinkCross(
                                                            List<SinkQuantumGroup> groups,
                                                            Map<Long, BigInteger> capacities,
                                                            BigInteger crossAmount,
                                                            BigInteger crossQuantum) {
        ArrayList<SinkCrossGroup> eligible = new ArrayList<>();
        for (SinkQuantumGroup group : groups) {
            BigInteger capacity = capacities.getOrDefault(group.quantum(), BigInteger.ZERO);
            if (capacity.signum() > 0) {
                eligible.add(new SinkCrossGroup(group.quantum(), capacity));
            }
        }

        BigInteger transferUnits = crossAmount.divide(crossQuantum);
        BigInteger availableUnits = sumGroupTargets(capacities).divide(crossQuantum);
        ArrayList<BigInteger> amounts = new ArrayList<>(eligible.size());
        ArrayList<GroupShare> shares = new ArrayList<>(eligible.size());
        BigInteger floorTotal = BigInteger.ZERO;
        for (int index = 0; index < eligible.size(); index++) {
            BigInteger groupUnits = eligible.get(index).capacity().divide(crossQuantum);
            BigInteger[] quotientAndRemainder = transferUnits.multiply(groupUnits)
                    .divideAndRemainder(availableUnits);
            amounts.add(quotientAndRemainder[0]);
            floorTotal = floorTotal.add(quotientAndRemainder[0]);
            shares.add(new GroupShare(index, quotientAndRemainder[1]));
        }

        int leftover = transferUnits.subtract(floorTotal).intValueExact();
        shares.sort(Comparator.comparing(GroupShare::remainder).reversed()
                .thenComparingInt(GroupShare::order));
        for (int index = 0; index < leftover; index++) {
            int groupIndex = shares.get(index).order();
            amounts.set(groupIndex, amounts.get(groupIndex).add(BigInteger.ONE));
        }

        LinkedHashMap<Long, BigInteger> targets = new LinkedHashMap<>();
        for (int index = 0; index < eligible.size(); index++) {
            targets.put(eligible.get(index).quantum(), amounts.get(index).multiply(crossQuantum));
        }
        return targets;
    }

    /**
     * Floors each residual quantum-group aggregate to the shared cross-group unit.
     */
    private static LinkedHashMap<Long, BigInteger> crossCapacities(
                                                                   Map<Long, BigInteger> residuals,
                                                                   BigInteger crossQuantum) {
        LinkedHashMap<Long, BigInteger> capacities = new LinkedHashMap<>();
        for (Map.Entry<Long, BigInteger> entry : residuals.entrySet()) {
            BigInteger capacity = roundDown(entry.getValue(), crossQuantum);
            if (capacity.signum() > 0) {
                capacities.put(entry.getKey(), capacity);
            }
        }
        return capacities;
    }

    /**
     * Computes the least common multiple of every unmatched residual operation quantum.
     */
    private static BigInteger residualCommonQuantum(
                                                    Map<Long, BigInteger> sourceResiduals,
                                                    Map<Long, BigInteger> sinkResiduals) {
        BigInteger common = BigInteger.ONE;
        for (long quantum : sourceResiduals.keySet()) {
            common = leastCommonMultiple(common, quantum);
        }
        for (long quantum : sinkResiduals.keySet()) {
            common = leastCommonMultiple(common, quantum);
        }
        return common;
    }

    private static LinkedHashMap<Long, BigInteger> zeroSourceTargets(List<SourceQuantumGroup> groups) {
        LinkedHashMap<Long, BigInteger> targets = new LinkedHashMap<>();
        for (SourceQuantumGroup group : groups) {
            targets.put(group.quantum(), BigInteger.ZERO);
        }
        return targets;
    }

    private static LinkedHashMap<Long, BigInteger> zeroSinkTargets(List<SinkQuantumGroup> groups) {
        LinkedHashMap<Long, BigInteger> targets = new LinkedHashMap<>();
        for (SinkQuantumGroup group : groups) {
            targets.put(group.quantum(), BigInteger.ZERO);
        }
        return targets;
    }

    private static void putPositive(Map<Long, BigInteger> amounts, long quantum, BigInteger amount) {
        if (amount.signum() > 0) {
            amounts.put(quantum, amount);
        }
    }

    /**
     * Re-simulates the final aligned plan once and rejects a violated quantum contract before mutation.
     */
    private static void verifyExecutable(
                                         List<TowerEnergySourceAllocation> sources,
                                         List<TowerEnergySinkAllocation> sinks,
                                         Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        for (TowerEnergySourceAllocation source : sources) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(source.endpoint());
            if (endpoint.simulateExtraction(source.amount()) != source.amount()) {
                throw new TowerEnergyTransferException(
                        "Energy extraction quantum changed for " + endpoint.description());
            }
        }
        for (TowerEnergySinkAllocation sink : sinks) {
            TowerEnergyTransferEndpoint endpoint = endpointsById.get(sink.endpoint());
            if (endpoint.simulateInsertion(sink.amount()) != sink.amount()) {
                throw new TowerEnergyTransferException(
                        "Energy insertion quantum changed for " + endpoint.description());
            }
        }
    }

    /**
     * Adds exact quantum-group targets or capacities without aggregate overflow.
     */
    private static BigInteger sumGroupTargets(Map<Long, BigInteger> amounts) {
        BigInteger total = BigInteger.ZERO;
        for (BigInteger amount : amounts.values()) {
            total = total.add(amount);
        }
        return total;
    }

    /**
     * Validates an integration-provided operation quantum at the transaction boundary.
     */
    private static long validateQuantum(
                                        long quantum,
                                        TowerEnergyTransferEndpoint endpoint,
                                        String operation) {
        if (quantum <= 0) {
            throw new TowerEnergyTransferException(
                    "Energy " + operation + " quantum must be positive for " + endpoint.description());
        }
        return quantum;
    }

    /**
     * Ensures a normalized allocation honors its declared native operation quantum.
     */
    private static void validateAlignedAmount(
                                              long amount,
                                              long quantum,
                                              TowerEnergyTransferEndpoint endpoint,
                                              String operation) {
        if (amount % quantum != 0) {
            throw new TowerEnergyTransferException(
                    "Energy " + operation + " amount " + amount + " is not aligned to quantum " + quantum + " for " + endpoint.description());
        }
    }

    /**
     * Extends one exact least common multiple without primitive overflow.
     */
    private static BigInteger leastCommonMultiple(BigInteger current, long quantum) {
        BigInteger next = BigInteger.valueOf(quantum);
        return current.divide(current.gcd(next)).multiply(next);
    }

    /**
     * Floors one non-negative aggregate to a complete common transfer unit.
     */
    private static BigInteger roundDown(BigInteger amount, BigInteger quantum) {
        return amount.subtract(amount.remainder(quantum));
    }

    /**
     * Re-simulates a coarsely converted FE amount until requesting the returned value is stable.
     */
    private static long normalizeAmount(
                                        long requested,
                                        TowerEnergyTransferEndpoint endpoint,
                                        boolean inserting) {
        long amount = requested;
        for (int pass = 0; pass < MAX_PREFLIGHT_PASSES; pass++) {
            long simulated = inserting ? endpoint.simulateInsertion(amount) : endpoint.simulateExtraction(amount);
            if (simulated == amount) {
                return amount;
            }
            amount = simulated;
            if (amount == 0) {
                return 0;
            }
        }
        throw new TowerEnergyTransferException(
                "Energy route simulation did not stabilize for " + endpoint.description());
    }

    /**
     * Adds normalized source requests without aggregate overflow.
     */
    private static BigInteger sumSources(List<TowerEnergySourceAllocation> sources) {
        BigInteger amount = BigInteger.ZERO;
        for (TowerEnergySourceAllocation source : sources) {
            amount = amount.add(BigInteger.valueOf(source.amount()));
        }
        return amount;
    }

    /**
     * Adds normalized sink requests without aggregate overflow.
     */
    private static BigInteger sumSinks(List<TowerEnergySinkAllocation> sinks) {
        BigInteger amount = BigInteger.ZERO;
        for (TowerEnergySinkAllocation sink : sinks) {
            amount = amount.add(BigInteger.valueOf(sink.amount()));
        }
        return amount;
    }

    /**
     * Restores the undelivered transfer buffer to original sources in reverse extraction order.
     */
    private static CompensationResult compensate(
                                                 List<Extraction> extractions,
                                                 BigInteger amount,
                                                 Set<TowerEnergyTransferEndpoint> mutatedEndpoints) {
        BigInteger remaining = amount.max(BigInteger.ZERO);
        BigInteger quarantined = BigInteger.ZERO;
        for (int index = extractions.size() - 1; index >= 0 && remaining.signum() > 0; index--) {
            Extraction extraction = extractions.get(index);
            long requested = BigInteger.valueOf(extraction.amount()).min(remaining).longValueExact();
            long restored = 0;
            try {
                restored = extraction.endpoint().compensateExtraction(requested);
            } catch (RuntimeException exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
            }
            if (restored > 0) {
                mutatedEndpoints.add(extraction.endpoint());
            }
            quarantined = quarantined.add(BigInteger.valueOf(requested - restored));
            remaining = remaining.subtract(BigInteger.valueOf(requested));
        }
        quarantined = quarantined.add(remaining);
        return new CompensationResult(quarantined);
    }

    /**
     * Publishes storage callbacks after all mutations and compensation attempts finish.
     */
    private static void publishMutations(Set<TowerEnergyTransferEndpoint> endpoints) {
        for (TowerEnergyTransferEndpoint endpoint : endpoints) {
            try {
                endpoint.publishMutation();
            } catch (RuntimeException exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
            }
        }
    }

    /**
     * Creates a failure result without repeating validation boilerplate.
     */
    private static TowerEnergyTransactionResult failed(
                                                       List<TowerEnergyEndpointSnapshot> snapshots,
                                                       long plannedFe,
                                                       long insertedFe,
                                                       long quarantinedFe,
                                                       boolean mutated,
                                                       String failure) {
        return new TowerEnergyTransactionResult(
                snapshots, plannedFe, insertedFe, quarantinedFe, mutated, failure);
    }

    /**
     * Builds a bounded diagnostic for endpoints excluded before planning.
     */
    private static String isolationFailure(int isolatedEndpointCount, List<String> details) {
        if (isolatedEndpointCount == 0) {
            return "";
        }
        String failure = "ENDPOINTS_ISOLATED[" + isolatedEndpointCount + "]: " + String.join(" | ", details);
        int omitted = isolatedEndpointCount - details.size();
        return omitted == 0 ? failure : failure + " | ... " + omitted + " more";
    }

    /**
     * Retains a primary transaction failure together with any preceding endpoint isolation.
     */
    private static String combineFailures(String primary, String isolation) {
        return isolation.isEmpty() ? primary : primary + " | " + isolation;
    }

    /**
     * Converts exact aggregate diagnostics to the protocol's saturated long width.
     */
    private static long saturatingLong(BigInteger value) {
        return value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    /**
     * Avoids an empty third-party exception message in the user-facing diagnostic.
     */
    private static String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * Holds source allocations that share one proven native transfer quantum.
     */
    private record SourceQuantumGroup(
                                      long quantum,
                                      List<TowerEnergySourceAllocation> allocations,
                                      BigInteger amount) {}

    /**
     * Holds sink allocations that share one proven native transfer quantum.
     */
    private record SinkQuantumGroup(
                                    long quantum,
                                    List<TowerEnergySinkAllocation> allocations,
                                    BigInteger amount) {}

    /**
     * Holds conserved source and sink targets keyed by their native operation quantum.
     */
    private record QuantumGroupTargets(
                                       Map<Long, BigInteger> sourceTargets,
                                       Map<Long, BigInteger> sinkTargets) {

        private QuantumGroupTargets {
            sourceTargets = Map.copyOf(sourceTargets);
            sinkTargets = Map.copyOf(sinkTargets);
        }
    }

    /**
     * Holds one sink residual group's capacity in the shared cross-group quantum.
     */
    private record SinkCrossGroup(long quantum, BigInteger capacity) {}

    /**
     * Retains the exact fractional share used to apportion common units between sink groups.
     */
    private record GroupShare(int order, BigInteger remainder) {}

    /**
     * Associates one completed source mutation with its compensation limit.
     */
    private record Extraction(TowerEnergyTransferEndpoint endpoint, long amount) {}

    /**
     * Retains the exact FE amount that could not be restored.
     */
    private record CompensationResult(BigInteger quarantined) {}

    /**
     * Carries either an executable conserved plan or a fail-fast preflight diagnostic.
     */
    private record PreflightResult(TowerEnergyEqualizationPlan plan, String failure) {

        private static PreflightResult success(TowerEnergyEqualizationPlan plan) {
            return new PreflightResult(plan, "");
        }

        private static PreflightResult failure(String failure) {
            return new PreflightResult(TowerEnergyEqualizationPlan.empty(), failure);
        }
    }
}
