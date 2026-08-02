package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups complete cycle rotations at exact affine balance breakpoints instead of searching firing-by-firing.
 */
final class TrinityDeterministicRepeatSchedulerImpl implements TrinityDeterministicRepeatScheduler {

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    @Override
    public TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                                      List<TrinityVariantFiring> oneCycleOrder,
                                                                      BigInteger repetitions,
                                                                      Map<AEKey, BigInteger> initialBalances,
                                                                      int maxStates,
                                                                      TrinityPlanningControl control) {
        if (oneCycleOrder == null || oneCycleOrder.isEmpty() || repetitions == null || repetitions.signum() <= 0 ||
                initialBalances == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException("A deterministic Trinity repeat schedule requires complete inputs");
        }
        List<TrinityVariantFiring> cycle = List.copyOf(oneCycleOrder);
        LinkedHashMap<AEKey, BigInteger> balances = copyBalances(initialBalances);
        ArrayList<TrinityVariantFiring> batches = new ArrayList<>();
        BigInteger remaining = repetitions;
        int statesVisited = 1;

        while (remaining.signum() > 0) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(statesVisited)));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of("reason", "timeout", "states", Integer.toString(statesVisited)));
            }
            if (statesVisited >= maxStates) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of(
                                "limit", Integer.toString(maxStates),
                                "states", Integer.toString(statesVisited)));
            }

            CycleBatch selected = selectLargestExecutableRotation(cycle, remaining, balances);
            if (selected.cycles().signum() <= 0) {
                return failure(
                        TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                        NO_EXECUTABLE_ORDER_KEY,
                        Map.of("states", Integer.toString(statesVisited)));
            }
            applyRotation(cycle, selected, balances, batches);
            remaining = remaining.subtract(selected.cycles());
            statesVisited = Math.addExact(statesVisited, 1);
        }
        return TrinityAlgorithmResult.success(new TrinityCompressedSchedule(
                List.copyOf(batches),
                positiveBalances(balances),
                statesVisited));
    }

    private static CycleBatch selectLargestExecutableRotation(
                                                              List<TrinityVariantFiring> cycle,
                                                              BigInteger remaining,
                                                              Map<AEKey, BigInteger> balances) {
        CycleBatch selected = new CycleBatch(0, BigInteger.ZERO);
        for (int rotation = 0; rotation < cycle.size(); rotation++) {
            BigInteger cycles = maximumExecutableCycles(cycle, rotation, remaining, balances);
            if (cycles.compareTo(selected.cycles()) > 0) {
                selected = new CycleBatch(rotation, cycles);
            }
        }
        return selected;
    }

    private static BigInteger maximumExecutableCycles(
                                                      List<TrinityVariantFiring> cycle,
                                                      int rotation,
                                                      BigInteger remaining,
                                                      Map<AEKey, BigInteger> balances) {
        BigInteger lower = BigInteger.ONE;
        BigInteger upper = remaining;
        LinkedHashMap<AEKey, BigInteger> prefixPerCycle = new LinkedHashMap<>();
        for (int offset = 0; offset < cycle.size(); offset++) {
            TrinityVariantFiring firing = cycle.get((rotation + offset) % cycle.size());
            for (Map.Entry<AEKey, BigInteger> input : firing.variant().inputs().entrySet()) {
                BigInteger delta = firing.variant().netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger constant = delta.signum() < 0 ? input.getValue().add(delta) : input.getValue();
                BigInteger requiredPerCycle = delta.signum() < 0 ?
                        delta.negate().multiply(firing.count()) :
                        BigInteger.ZERO;
                BigInteger prefix = prefixPerCycle.getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger slope = requiredPerCycle.subtract(prefix);
                BigInteger margin = balances.getOrDefault(input.getKey(), BigInteger.ZERO).subtract(constant);
                if (slope.signum() > 0) {
                    if (margin.signum() < 0) {
                        return BigInteger.ZERO;
                    }
                    upper = upper.min(margin.divide(slope));
                } else if (slope.signum() == 0) {
                    if (margin.signum() < 0) {
                        return BigInteger.ZERO;
                    }
                } else if (margin.signum() < 0) {
                    lower = lower.max(ceilDivide(margin.negate(), slope.negate()));
                }
                if (upper.compareTo(lower) < 0) {
                    return BigInteger.ZERO;
                }
            }
            firing.variant().netChange().forEach((key, amount) -> prefixPerCycle.merge(
                    key,
                    amount.multiply(firing.count()),
                    BigInteger::add));
        }
        return upper.compareTo(lower) >= 0 ? upper : BigInteger.ZERO;
    }

    private static void applyRotation(
                                      List<TrinityVariantFiring> cycle,
                                      CycleBatch selected,
                                      Map<AEKey, BigInteger> balances,
                                      List<TrinityVariantFiring> batches) {
        for (int offset = 0; offset < cycle.size(); offset++) {
            TrinityVariantFiring base = cycle.get((selected.rotation() + offset) % cycle.size());
            BigInteger count = base.count().multiply(selected.cycles());
            Map<AEKey, BigInteger> required = requiredAtStart(base.variant(), count);
            if (!hasInputs(balances, required)) {
                throw new IllegalStateException("An exact Trinity cycle batch violated its derived balance bound");
            }
            base.variant().netChange().forEach((key, amount) -> {
                BigInteger updated = balances.getOrDefault(key, BigInteger.ZERO).add(amount.multiply(count));
                if (updated.signum() < 0) {
                    throw new IllegalStateException("An exact Trinity cycle batch produced a negative balance");
                }
                if (updated.signum() == 0) {
                    balances.remove(key);
                } else {
                    balances.put(key, updated);
                }
            });
            appendBatch(batches, new TrinityVariantFiring(base.variant(), count));
        }
    }

    private static Map<AEKey, BigInteger> requiredAtStart(
                                                          TrinityPatternVariant variant,
                                                          BigInteger count) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        variant.inputs().forEach((key, input) -> {
            BigInteger net = variant.netChange().getOrDefault(key, BigInteger.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(count.subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static void appendBatch(List<TrinityVariantFiring> batches, TrinityVariantFiring added) {
        if (!batches.isEmpty()) {
            TrinityVariantFiring previous = batches.getLast();
            if (previous.variant().equals(added.variant())) {
                batches.set(
                        batches.size() - 1,
                        new TrinityVariantFiring(previous.variant(), previous.count().add(added.count())));
                return;
            }
        }
        batches.add(added);
    }

    private static boolean hasInputs(Map<AEKey, BigInteger> balances, Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().allMatch(entry -> balances
                .getOrDefault(entry.getKey(), BigInteger.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static LinkedHashMap<AEKey, BigInteger> copyBalances(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity deterministic repeat balances cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return copied;
    }

    private static Map<AEKey, BigInteger> positiveBalances(Map<AEKey, BigInteger> balances) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(balances));
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
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

    private record CycleBatch(int rotation, BigInteger cycles) {}
}
