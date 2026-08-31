package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Exact executable ordering found without expanding one state per logical firing.
 *
 * @param batches          ordered proof outline containing one copy of an optional repeat unit
 * @param repeatStartIndex inclusive repeat-unit start in {@code batches}, or zero for a flat schedule
 * @param repeatEndIndex   exclusive repeat-unit end in {@code batches}, or zero for a flat schedule
 * @param repeatCount      exact repeat count, or zero for a flat schedule
 * @param finalBalances    exact non-negative balance after the complete expanded schedule
 * @param statesVisited    compressed proof states explored
 */
public record TrinityCompressedSchedule(
                                        List<TrinityVariantFiring> batches,
                                        int repeatStartIndex,
                                        int repeatEndIndex,
                                        BigInteger repeatCount,
                                        Map<AEKey, BigInteger> finalBalances,
                                        int statesVisited) {

    /** Creates a flat schedule whose outline is also its complete execution order. */
    public TrinityCompressedSchedule(
                                     List<TrinityVariantFiring> batches,
                                     Map<AEKey, BigInteger> finalBalances,
                                     int statesVisited) {
        this(batches, 0, 0, BigInteger.ZERO, finalBalances, statesVisited);
    }

    /** Creates an exact nested repeat without expanding its BigInteger count. */
    public static TrinityCompressedSchedule repeated(
                                                     List<TrinityVariantFiring> prefix,
                                                     List<TrinityVariantFiring> repeatUnit,
                                                     BigInteger repeatCount,
                                                     List<TrinityVariantFiring> suffix,
                                                     Map<AEKey, BigInteger> finalBalances,
                                                     int statesVisited) {
        if (repeatUnit.isEmpty() || repeatCount.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity repeat schedule requires a positive unit");
        }
        ObjectArrayList<TrinityVariantFiring> outline = new ObjectArrayList<>(
                Math.addExact(Math.addExact(prefix.size(), repeatUnit.size()), suffix.size()));
        outline.addAll(prefix);
        int repeatStartIndex = outline.size();
        outline.addAll(repeatUnit);
        int repeatEndIndex = outline.size();
        outline.addAll(suffix);
        return new TrinityCompressedSchedule(
                outline,
                repeatStartIndex,
                repeatEndIndex,
                repeatCount,
                finalBalances,
                statesVisited);
    }

    /** Freezes the compact proof and verifies its exact repeat coordinates. */
    public TrinityCompressedSchedule {
        if (statesVisited < 0 || repeatStartIndex < 0 || repeatEndIndex < repeatStartIndex ||
                repeatEndIndex > batches.size() || repeatCount.signum() < 0) {
            throw new IllegalArgumentException("A Trinity compressed schedule requires complete accounting");
        }
        boolean repeated = repeatCount.signum() > 0;
        if (repeated != (repeatEndIndex > repeatStartIndex) ||
                !repeated && repeatStartIndex != 0) {
            throw new IllegalArgumentException("A Trinity compressed schedule has an invalid repeat range");
        }
        batches = List.copyOf(batches);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copiedBalances = new Object2ObjectLinkedOpenHashMap<>();
        finalBalances.forEach((key, amount) -> {
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity final schedule balance cannot be negative");
            }
            if (amount.signum() > 0) {
                copiedBalances.put(key, amount);
            }
        });
        finalBalances = Object2ObjectMaps.unmodifiable(copiedBalances);
    }

    /** @return whether this proof contains a nested repeat block */
    public boolean hasRepeatBlock() {
        return this.repeatCount.signum() > 0;
    }

    /** @return one-time batches before the repeat unit */
    public List<TrinityVariantFiring> prefixBatches() {
        return this.batches.subList(0, this.repeatStartIndex);
    }

    /** @return the single executable unit represented by {@link #repeatCount()} repetitions */
    public List<TrinityVariantFiring> repeatUnit() {
        return this.batches.subList(this.repeatStartIndex, this.repeatEndIndex);
    }

    /** @return one-time batches after every repeat */
    public List<TrinityVariantFiring> suffixBatches() {
        return this.batches.subList(this.repeatEndIndex, this.batches.size());
    }

    /** Preserves the exact proof while replacing its aggregate accounting state count. */
    public TrinityCompressedSchedule withStatesVisited(int statesVisited) {
        return new TrinityCompressedSchedule(
                this.batches,
                this.repeatStartIndex,
                this.repeatEndIndex,
                this.repeatCount,
                this.finalBalances,
                statesVisited);
    }

    /** @return aggregate firing vector represented by the nested proof */
    public Map<TrinityPatternVariant, BigInteger> aggregateFirings() {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregate = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < this.batches.size(); index++) {
            TrinityVariantFiring batch = this.batches.get(index);
            BigInteger multiplier = index >= this.repeatStartIndex && index < this.repeatEndIndex ?
                    this.repeatCount : BigInteger.ONE;
            aggregate.merge(batch.variant(), batch.count().multiply(multiplier), BigInteger::add);
        }
        return Object2ObjectMaps.unmodifiable(aggregate);
    }
}
