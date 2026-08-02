package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;

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
 * @param localOrder     compressed executable batches for one repeat unit
 * @param repetitions    positive repeat count applied by graph assembly
 * @param minimumSeed    exact prefix reserve exposed to graph assembly
 * @param initialInputs  exact initial inventory reserved for the selected cycle
 * @param netChange      exact signed aggregate cycle effect
 * @param scheduleStates bounded search states visited
 * @param mipNanos       deterministic opportunity time plus ojAlgo solver time
 */
public record TrinityCycleSelection(
                                    int componentIndex,
                                    List<TrinityVariantFiring> localOrder,
                                    BigInteger repetitions,
                                    Map<AEKey, BigInteger> minimumSeed,
                                    Map<AEKey, BigInteger> initialInputs,
                                    Map<AEKey, BigInteger> netChange,
                                    int scheduleStates,
                                    long mipNanos) {

    /**
     * Copies every retained collection so graph orchestration never observes solver-owned mutable state.
     */
    public TrinityCycleSelection {
        if (componentIndex < 0 || localOrder == null || localOrder.isEmpty() || repetitions == null ||
                repetitions.signum() <= 0 || minimumSeed == null || initialInputs == null || netChange == null ||
                scheduleStates < 0 || mipNanos < 0L) {
            throw new IllegalArgumentException("A Trinity cycle selection requires complete exact accounting");
        }
        localOrder = List.copyOf(localOrder);
        minimumSeed = copyAmounts(minimumSeed, false);
        initialInputs = copyAmounts(initialInputs, false);
        netChange = copyAmounts(netChange, true);
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
