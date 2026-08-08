package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyAllocationLimiter;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizationPlan;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizationSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizer;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEqualizerImpl;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergySinkAllocation;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergySourceAllocation;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default two-phase transaction executor using exact proportional water filling.
 */
public final class TowerEnergyTransactionImpl implements TowerEnergyTransaction {

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
    public TowerEnergyTransactionImpl() {
        this(new TowerEnergyEqualizerImpl());
    }

    /**
     * Creates an executor with an explicit planner for direct logic testing.
     *
     * @param equalizer immutable-snapshot planner
     */
    public TowerEnergyTransactionImpl(TowerEnergyEqualizer equalizer) {
        this.equalizer = equalizer;
    }

    @Override
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
     * Reduces a plan to the largest common amount that every selected route simulates exactly.
     */
    private static PreflightResult preflight(
                                             TowerEnergyEqualizationPlan plan,
                                             Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        try {
            List<TowerEnergySourceAllocation> sources = normalizeSources(plan.sources(), endpointsById);
            List<TowerEnergySinkAllocation> sinks = normalizeSinks(plan.sinks(), endpointsById);
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
        } catch (RuntimeException exception) {
            return PreflightResult.failure("PREFLIGHT_FAILED: " + conciseMessage(exception));
        }
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
