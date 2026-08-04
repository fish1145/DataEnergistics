package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.macro;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact primitive cycle treated as one settled planning unit instead of expanding the player request into firings.
 *
 * @param unitOrder       executable firing order for exactly one complete cycle
 * @param repetitions     number of complete cycle units
 * @param unitMinimumSeed internal prefix reserve needed by one unit
 * @param unitNetChange   exact signed effect of one complete unit
 * @param exportableNet   positive settled outputs visible to downstream components
 */
public record TrinityCycleMacro(
                                List<TrinityVariantFiring> unitOrder,
                                BigInteger repetitions,
                                Map<AEKey, BigInteger> unitMinimumSeed,
                                Map<AEKey, BigInteger> unitNetChange,
                                Map<AEKey, BigInteger> exportableNet) {

    /**
     * Freezes the proof and verifies that downstream exports are exactly the positive net effect of a complete unit.
     */
    public TrinityCycleMacro {
        if (unitOrder == null || unitOrder.isEmpty() || repetitions == null || repetitions.signum() <= 0 ||
                unitMinimumSeed == null || unitNetChange == null || unitNetChange.isEmpty() || exportableNet == null) {
            throw new IllegalArgumentException("A Trinity cycle macro requires a complete positive repeat proof");
        }
        unitOrder = compactOrder(unitOrder);
        unitMinimumSeed = copyAmounts(unitMinimumSeed, false);
        unitNetChange = copyAmounts(unitNetChange, true);
        exportableNet = copyAmounts(exportableNet, false);
        if (!calculateUnitNet(unitOrder).equals(unitNetChange)) {
            throw new IllegalArgumentException("A Trinity cycle macro unit order must match its exact net effect");
        }
        if (!positiveAmounts(unitNetChange).equals(exportableNet)) {
            throw new IllegalArgumentException("A Trinity cycle macro may export only settled positive net outputs");
        }
    }

    /**
     * @return exact aggregate firing vector without expanding the repeat count
     */
    public Map<TrinityPatternVariant, BigInteger> aggregateFirings() {
        LinkedHashMap<TrinityPatternVariant, BigInteger> aggregate = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : this.unitOrder) {
            aggregate.merge(
                    firing.variant(),
                    firing.count().multiply(this.repetitions),
                    BigInteger::add);
        }
        return Collections.unmodifiableMap(aggregate);
    }

    /**
     * @return exact aggregate net effect of every repetition
     */
    public Map<AEKey, BigInteger> aggregateNetChange() {
        LinkedHashMap<AEKey, BigInteger> aggregate = new LinkedHashMap<>();
        this.unitNetChange.forEach((key, amount) -> aggregate.put(key, amount.multiply(this.repetitions)));
        return Collections.unmodifiableMap(aggregate);
    }

    private static List<TrinityVariantFiring> compactOrder(List<TrinityVariantFiring> order) {
        ArrayList<TrinityVariantFiring> compacted = new ArrayList<>(order.size());
        for (TrinityVariantFiring firing : order) {
            if (!compacted.isEmpty() && compacted.getLast().variant().equals(firing.variant())) {
                TrinityVariantFiring previous = compacted.removeLast();
                compacted.add(new TrinityVariantFiring(
                        previous.variant(),
                        previous.count().add(firing.count())));
            } else {
                compacted.add(firing);
            }
        }
        return List.copyOf(compacted);
    }

    private static Map<AEKey, BigInteger> calculateUnitNet(List<TrinityVariantFiring> order) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : order) {
            firing.variant().netChange().forEach((key, amount) -> net.merge(key, amount.multiply(firing.count()), BigInteger::add));
        }
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(net);
    }

    private static Map<AEKey, BigInteger> positiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> positive = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                positive.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(positive);
    }

    private static Map<AEKey, BigInteger> copyAmounts(Map<AEKey, BigInteger> source, boolean signed) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || (signed ? amount.signum() == 0 : amount.signum() <= 0)) {
                throw new IllegalArgumentException("A Trinity cycle macro amount violates its accounting role");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
