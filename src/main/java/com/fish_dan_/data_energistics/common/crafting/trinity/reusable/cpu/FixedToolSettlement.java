package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.FixedToolIdentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Checks returned real states against delivered units and completed uses, without synthesizing state-chain items. */
final class FixedToolSettlement {

    private final ReusableInputRule rule;
    private final Map<AEItemKey, BigInteger> delivered = new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BigInteger> operationsBySlot = new Int2ObjectOpenHashMap<>();
    private BigInteger consumedUses = BigInteger.ZERO;

    FixedToolSettlement(ReusableInputRule rule) {
        this.rule = rule;
    }

    void deliver(AEItemKey key, long amount) {
        if (!FixedToolIdentity.matches(rule, key)) throw new IllegalStateException("Delivered tool violates its fixed-wear contract");
        delivered.merge(key, BigInteger.valueOf(amount), BigInteger::add);
    }

    void completed(int slot, BigInteger held, long operations) {
        var count = BigInteger.valueOf(operations);
        operationsBySlot.merge(slot, count, BigInteger::add);
        consumedUses = consumedUses.add(held.multiply(count));
    }

    BigInteger verify(Map<AEKey, BigInteger> remaining) {
        var returned = new Object2ObjectOpenHashMap<AEItemKey, BigInteger>();
        var iterator = remaining.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() instanceof AEItemKey item && FixedToolIdentity.matches(rule, item)) {
                returned.put(item, entry.getValue());
                iterator.remove();
            }
        }
        BigInteger before = capacity(delivered);
        BigInteger after = capacity(returned);
        if (!before.subtract(after).equals(consumedUses)) {
            throw new IllegalStateException("Returned tool durability does not match completed operations");
        }
        var initial = groups(delivered);
        var actual = groups(returned);
        BigInteger maximumOperations = BigInteger.ZERO;
        for (BigInteger count : operationsBySlot.values()) maximumOperations = maximumOperations.max(count);
        BigInteger exhausted = BigInteger.ZERO;
        for (var entry : initial.int2ObjectEntrySet()) {
            var expected = entry.getValue();
            var observed = actual.computeIfAbsent(entry.getIntKey(), ignored -> new Long2ObjectAVLTreeMap<>());
            actual.remove(entry.getIntKey());
            BigInteger missing = count(expected).subtract(count(observed));
            if (missing.signum() < 0) throw new IllegalStateException("Executor returned additional tool units");
            if (missing.signum() > 0) observed.put(0L, missing);
            checkMonotone(expected, observed, maximumOperations);
            exhausted = exhausted.add(missing);
        }
        if (!actual.isEmpty()) throw new IllegalStateException("Executor changed undeclared tool damage coordinates");
        return exhausted;
    }

    private BigInteger capacity(Map<AEItemKey, BigInteger> tools) {
        BigInteger result = BigInteger.ZERO;
        for (var entry : tools.entrySet()) result = result.add(entry.getValue().multiply(BigInteger.valueOf(rule.guaranteedUses(entry.getKey()))));
        return result;
    }

    private Int2ObjectOpenHashMap<Long2ObjectAVLTreeMap<BigInteger>> groups(Map<AEItemKey, BigInteger> tools) {
        var result = new Int2ObjectOpenHashMap<Long2ObjectAVLTreeMap<BigInteger>>();
        tools.forEach((key, count) -> result.computeIfAbsent(key.getReadOnlyStack().getDamageValue() % rule.damagePerUse(),
                ignored -> new Long2ObjectAVLTreeMap<>()).merge(rule.guaranteedUses(key), count, BigInteger::add));
        return result;
    }

    private static BigInteger count(Long2ObjectAVLTreeMap<BigInteger> amounts) {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger amount : amounts.values()) result = result.add(amount);
        return result;
    }

    private static void checkMonotone(Long2ObjectAVLTreeMap<BigInteger> before, Long2ObjectAVLTreeMap<BigInteger> after,
                                      BigInteger maximumOperations) {
        var initial = buckets(before);
        var actual = buckets(after);
        int left = 0, right = 0;
        BigInteger leftCount = initial.getFirst().count();
        BigInteger rightCount = actual.getFirst().count();
        while (left < initial.size() && right < actual.size()) {
            long delta = initial.get(left).uses() - actual.get(right).uses();
            if (delta < 0 || BigInteger.valueOf(delta).compareTo(maximumOperations) > 0) {
                throw new IllegalStateException("Executor healed a tool or reused one unit multiple times per operation");
            }
            BigInteger matched = leftCount.min(rightCount);
            leftCount = leftCount.subtract(matched);
            rightCount = rightCount.subtract(matched);
            if (leftCount.signum() == 0 && ++left < initial.size()) leftCount = initial.get(left).count();
            if (rightCount.signum() == 0 && ++right < actual.size()) rightCount = actual.get(right).count();
        }
    }

    private static List<Bucket> buckets(Long2ObjectAVLTreeMap<BigInteger> source) {
        var result = new ObjectArrayList<Bucket>();
        source.long2ObjectEntrySet().forEach(entry -> result.add(new Bucket(entry.getLongKey(), entry.getValue())));
        return result;
    }

    private record Bucket(long uses, BigInteger count) {}
}
