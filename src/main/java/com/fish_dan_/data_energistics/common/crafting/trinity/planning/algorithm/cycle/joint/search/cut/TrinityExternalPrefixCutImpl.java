package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.TrinityFiringBox;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Saturating dependency closure. It deliberately overestimates available external resources, so only negative
 * reachability conclusions become cuts.
 */
final class TrinityExternalPrefixCutImpl implements TrinityExternalPrefixCut {

    @Override
    public Optional<TrinityExternalPrefixPartition> partition(
                                                              TrinityFiringBox box,
                                                              Set<AEKey> internalKeys,
                                                              BigInteger cap) {
        if (box == null || internalKeys == null || internalKeys.isEmpty() || cap == null || cap.signum() < 0) {
            throw new IllegalArgumentException("A Trinity external-prefix cut request is incomplete");
        }
        Map<AEKey, BigInteger> thresholds = externalInputThresholds(box, internalKeys);
        LinkedHashMap<AEKey, BigInteger> optimisticAmounts = new LinkedHashMap<>();
        thresholds.keySet().forEach(key -> optimisticAmounts.put(key, cap));
        LinkedHashSet<TrinityPatternVariant> reachable = new LinkedHashSet<>();

        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < box.variants().size(); index++) {
                TrinityPatternVariant variant = box.variants().get(index);
                if (box.bounds().get(index).upperInclusive().signum() == 0 || reachable.contains(variant) ||
                        !canTrigger(variant, internalKeys, optimisticAmounts)) {
                    continue;
                }
                reachable.add(variant);
                variant.outputs().forEach((key, amount) -> {
                    if (!internalKeys.contains(key) && amount.signum() > 0) {
                        BigInteger saturated = thresholds.getOrDefault(key, amount).max(amount);
                        optimisticAmounts.merge(key, saturated, BigInteger::max);
                    }
                });
                changed = true;
            }
        } while (changed);

        ArrayList<Integer> unreachableAxes = new ArrayList<>();
        for (int index = 0; index < box.variants().size(); index++) {
            if (box.bounds().get(index).upperInclusive().signum() > 0 &&
                    !reachable.contains(box.variants().get(index))) {
                unreachableAxes.add(index);
            }
        }
        return unreachableAxes.isEmpty() ? Optional.empty() : Optional.of(partition(box, unreachableAxes));
    }

    private static TrinityExternalPrefixPartition partition(
                                                            TrinityFiringBox box,
                                                            List<Integer> unreachableAxes) {
        ArrayList<TrinityFiringBounds> remaining = new ArrayList<>(box.bounds());
        ArrayList<TrinityFiringBox> aboveCap = new ArrayList<>();
        boolean zeroBranch = true;
        for (int index : unreachableAxes) {
            TrinityFiringBounds parent = remaining.get(index);
            ArrayList<TrinityFiringBounds> positive = new ArrayList<>(remaining);
            positive.set(index, new TrinityFiringBounds(
                    parent.lowerInclusive().max(BigInteger.ONE),
                    parent.upperInclusive()));
            aboveCap.add(new TrinityFiringBox(box.variants(), positive));
            if (parent.lowerInclusive().signum() > 0) {
                zeroBranch = false;
                break;
            }
            remaining.set(index, TrinityFiringBounds.fixed(BigInteger.ZERO));
        }
        Optional<TrinityFiringBox> withinCap = zeroBranch ?
                Optional.of(new TrinityFiringBox(box.variants(), remaining)) : Optional.empty();
        return new TrinityExternalPrefixPartition(withinCap, aboveCap);
    }

    private static Map<AEKey, BigInteger> externalInputThresholds(
                                                                  TrinityFiringBox box,
                                                                  Set<AEKey> internalKeys) {
        LinkedHashMap<AEKey, BigInteger> thresholds = new LinkedHashMap<>();
        for (int index = 0; index < box.variants().size(); index++) {
            if (box.bounds().get(index).upperInclusive().signum() == 0) {
                continue;
            }
            box.variants().get(index).inputs().forEach((key, amount) -> {
                if (!internalKeys.contains(key)) {
                    thresholds.merge(key, amount, BigInteger::max);
                }
            });
        }
        return thresholds;
    }

    private static boolean canTrigger(
                                      TrinityPatternVariant variant,
                                      Set<AEKey> internalKeys,
                                      Map<AEKey, BigInteger> optimisticAmounts) {
        return variant.inputs().entrySet().stream()
                .filter(entry -> !internalKeys.contains(entry.getKey()))
                .allMatch(entry -> optimisticAmounts
                        .getOrDefault(entry.getKey(), BigInteger.ZERO)
                        .compareTo(entry.getValue()) >= 0);
    }
}
