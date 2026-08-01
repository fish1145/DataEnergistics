package com.fish_dan_.data_energistics.blockentity.tower.network;

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

    /** Exact planner kept separate from capability mutation. */
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
        try {
            for (TowerEnergyTransferEndpoint endpoint : orderedEndpoints) {
                snapshots.add(endpoint.freeze());
            }
        } catch (RuntimeException exception) {
            return failed(snapshots, 0, 0, 0, false,
                    "FREEZE_FAILED: " + conciseMessage(exception));
        }

        TowerEnergyEqualizationPlan plan;
        try {
            plan = this.equalizer.plan(new TowerEnergyEqualizationSnapshot(snapshots));
        } catch (RuntimeException exception) {
            return failed(snapshots, 0, 0, 0, false,
                    "PLAN_FAILED: " + conciseMessage(exception));
        }
        long plannedFe = saturatingLong(plan.totalAmount());
        if (plan.isEmpty()) {
            return new TowerEnergyTransactionResult(snapshots, 0, 0, 0, false, "");
        }

        String preflightFailure = preflight(plan, endpointsById);
        if (!preflightFailure.isEmpty()) {
            return failed(snapshots, plannedFe, 0, 0, false, preflightFailure);
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
                        "SOURCE_MUTATION_FAILED: " + endpoint.description() + ": " + conciseMessage(exception));
            }
            if (extracted > 0) {
                extractions.add(new Extraction(endpoint, extracted));
                extractedTotal = extractedTotal.add(BigInteger.valueOf(extracted));
                mutatedEndpoints.add(endpoint);
            }
            if (extracted != source.amount()) {
                CompensationResult compensation = compensate(extractions, extractedTotal, mutatedEndpoints);
                publishMutations(mutatedEndpoints);
                return failed(
                        snapshots,
                        plannedFe,
                        0,
                        saturatingLong(compensation.quarantined()),
                        !mutatedEndpoints.isEmpty(),
                        "SOURCE_SHORT_WRITE: " + endpoint.description());
            }
        }

        BigInteger insertedTotal = BigInteger.ZERO;
        for (TowerEnergySinkAllocation sink : plan.sinks()) {
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
                        plannedFe,
                        saturatingLong(insertedTotal),
                        saturatingLong(compensation.quarantined()),
                        !mutatedEndpoints.isEmpty(),
                        "SINK_MUTATION_FAILED: " + endpoint.description() + ": " + conciseMessage(exception));
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
                        plannedFe,
                        saturatingLong(insertedTotal),
                        saturatingLong(compensation.quarantined()),
                        !mutatedEndpoints.isEmpty(),
                        "SINK_SHORT_WRITE: " + endpoint.description());
            }
        }

        publishMutations(mutatedEndpoints);
        return new TowerEnergyTransactionResult(
                snapshots,
                plannedFe,
                saturatingLong(insertedTotal),
                0,
                !mutatedEndpoints.isEmpty(),
                "");
    }

    /** Indexes stable identities and fails before any query when the topology contains a duplicate. */
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

    /** Simulates every source and sink before the first real mutation. */
    private static String preflight(
                                    TowerEnergyEqualizationPlan plan,
                                    Map<TowerEnergyEndpointId, TowerEnergyTransferEndpoint> endpointsById) {
        try {
            for (TowerEnergySourceAllocation source : plan.sources()) {
                TowerEnergyTransferEndpoint endpoint = endpointsById.get(source.endpoint());
                if (endpoint.simulateExtraction(source.amount()) != source.amount()) {
                    return "SOURCE_PREFLIGHT_REJECTED: " + endpoint.description();
                }
            }
            for (TowerEnergySinkAllocation sink : plan.sinks()) {
                TowerEnergyTransferEndpoint endpoint = endpointsById.get(sink.endpoint());
                if (endpoint.simulateInsertion(sink.amount()) != sink.amount()) {
                    return "SINK_PREFLIGHT_REJECTED: " + endpoint.description();
                }
            }
            return "";
        } catch (RuntimeException exception) {
            return "PREFLIGHT_FAILED: " + conciseMessage(exception);
        }
    }

    /** Restores the undelivered transfer buffer to original sources in reverse extraction order. */
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

    /** Publishes storage callbacks after all mutations and compensation attempts finish. */
    private static void publishMutations(Set<TowerEnergyTransferEndpoint> endpoints) {
        for (TowerEnergyTransferEndpoint endpoint : endpoints) {
            try {
                endpoint.publishMutation();
            } catch (RuntimeException exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
            }
        }
    }

    /** Creates a failure result without repeating validation boilerplate. */
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

    /** Converts exact aggregate diagnostics to the protocol's saturated long width. */
    private static long saturatingLong(BigInteger value) {
        return value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    /** Avoids an empty third-party exception message in the user-facing diagnostic. */
    private static String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /** Associates one completed source mutation with its compensation limit. */
    private record Extraction(TowerEnergyTransferEndpoint endpoint, long amount) {}

    /** Retains the exact FE amount that could not be restored. */
    private record CompensationResult(BigInteger quarantined) {}
}
