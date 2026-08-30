package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable cycle algorithm selection consumed by graph-stage assembly.
 *
 * @param componentIndex owning SCC index
 * @param prefixOrder    one-time residual batches executed before the complete cycle units
 * @param localOrder     compressed executable batches for one repeat unit
 * @param repetitions    positive repeat count applied by graph assembly
 * @param suffixOrder    one-time residual batches executed after every complete cycle unit
 * @param minimumSeed    exact prefix reserve exposed to graph assembly
 * @param initialInputs  exact initial inventory reserved for the selected cycle
 * @param netChange      exact signed aggregate cycle effect
 * @param exportableNet  settled positive outputs proved safe to expose outside the complete cycle block
 * @param scheduleStates bounded search states visited
 * @param mipNanos       deterministic opportunity time plus ojAlgo solver time
 * @param quality        exact proof strength retained by this cycle
 */
public record TrinityCycleSelection(
                                    int componentIndex,
                                    List<TrinityVariantFiring> prefixOrder,
                                    List<TrinityVariantFiring> localOrder,
                                    BigInteger repetitions,
                                    List<TrinityVariantFiring> suffixOrder,
                                    Map<AEKey, BigInteger> minimumSeed,
                                    Map<AEKey, BigInteger> initialInputs,
                                    Map<AEKey, BigInteger> netChange,
                                    Map<AEKey, BigInteger> exportableNet,
                                    int scheduleStates,
                                    long mipNanos,
                                    TrinityPlanQuality quality) {

    /**
     * Copies every retained collection so graph orchestration never observes solver-owned mutable state.
     */
    public TrinityCycleSelection {
        if (componentIndex < 0 || prefixOrder == null || localOrder == null || localOrder.isEmpty() ||
                suffixOrder == null || repetitions == null ||
                repetitions.signum() <= 0 || minimumSeed == null || initialInputs == null || netChange == null ||
                exportableNet == null || scheduleStates < 0 || mipNanos < 0L || quality == null) {
            throw new IllegalArgumentException("A Trinity cycle selection requires complete exact accounting");
        }
        prefixOrder = List.copyOf(prefixOrder);
        localOrder = List.copyOf(localOrder);
        suffixOrder = List.copyOf(suffixOrder);
        minimumSeed = copyAmounts(minimumSeed, false);
        initialInputs = copyAmounts(initialInputs, false);
        netChange = copyAmounts(netChange, true);
        exportableNet = copyAmounts(exportableNet, false);
        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        mergeNet(calculatedNet, prefixOrder, BigInteger.ONE);
        mergeNet(calculatedNet, localOrder, repetitions);
        mergeNet(calculatedNet, suffixOrder, BigInteger.ONE);
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity cycle selection order must match its exact net change");
        }
        for (Map.Entry<AEKey, BigInteger> export : exportableNet.entrySet()) {
            if (!netChange.getOrDefault(export.getKey(), BigInteger.ZERO).equals(export.getValue())) {
                throw new IllegalArgumentException("A Trinity cycle export must equal its settled positive net output");
            }
        }
    }

    /**
     * Compatibility constructor for cycle paths that already carry a complete optimality proof.
     */
    public TrinityCycleSelection(
                                 int componentIndex,
                                 List<TrinityVariantFiring> prefixOrder,
                                 List<TrinityVariantFiring> localOrder,
                                 BigInteger repetitions,
                                 List<TrinityVariantFiring> suffixOrder,
                                 Map<AEKey, BigInteger> minimumSeed,
                                 Map<AEKey, BigInteger> initialInputs,
                                 Map<AEKey, BigInteger> netChange,
                                 Map<AEKey, BigInteger> exportableNet,
                                 int scheduleStates,
                                 long mipNanos) {
        this(
                componentIndex,
                prefixOrder,
                localOrder,
                repetitions,
                suffixOrder,
                minimumSeed,
                initialInputs,
                netChange,
                exportableNet,
                scheduleStates,
                mipNanos,
                TrinityPlanQuality.PROVED_OPTIMAL);
    }

    private static void mergeNet(
                                 Map<AEKey, BigInteger> target,
                                 List<TrinityVariantFiring> order,
                                 BigInteger multiplier) {
        order.forEach(firing -> firing.variant().netChange().forEach(
                (key, amount) -> target.merge(
                        key,
                        amount.multiply(firing.count()).multiply(multiplier),
                        BigInteger::add)));
    }

    private static Map<AEKey, BigInteger> copyAmounts(Map<AEKey, BigInteger> source, boolean signed) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || (signed ? amount.signum() == 0 : amount.signum() <= 0)) {
                throw new IllegalArgumentException("A Trinity cycle selection amount violates its accounting role");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
