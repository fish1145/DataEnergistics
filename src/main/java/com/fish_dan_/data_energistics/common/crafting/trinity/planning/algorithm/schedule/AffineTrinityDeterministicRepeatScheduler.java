package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Proves a fixed deterministic unit repeated an arbitrary BigInteger number of times by affine prefix bounds.
 */
final class AffineTrinityDeterministicRepeatScheduler implements TrinityDeterministicRepeatScheduler {

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    /**
     * The worst prefix of unit {@code j} differs from the first unit by {@code j * unitNet}. Therefore one exact
     * prefix scan plus the negative unit slope proves every repetition without iterating the requested quantity.
     */
    @Override
    public TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                                      List<TrinityVariantFiring> oneCycleOrder,
                                                                      BigInteger repetitions,
                                                                      Map<AEKey, BigInteger> initialBalances,
                                                                      int maxStates,
                                                                      TrinityPlanningControl control) {
        if (oneCycleOrder.isEmpty() || repetitions.signum() <= 0 || maxStates <= 0) {
            throw new IllegalArgumentException("A deterministic Trinity repeat schedule requires complete inputs");
        }
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", "0"));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of("reason", "timeout", "states", "0"));
        }

        List<TrinityVariantFiring> unit = oneCycleOrder;
        int statesVisited = Math.addExact(unit.size(), 1);
        if (statesVisited > maxStates) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of("limit", Integer.toString(maxStates), "states", Integer.toString(statesVisited)));
        }

        Map<AEKey, BigInteger> unitNet = unitNet(unit);
        Map<AEKey, BigInteger> required = TrinityCycleSeedRequirement.repeatedMinimumInputs(unit, repetitions);
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
        if (!hasInputs(initialBalances, required)) {
            return failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    NO_EXECUTABLE_ORDER_KEY,
                    Map.of("states", Integer.toString(statesVisited)));
        }

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = copyBalances(initialBalances);
        unitNet.forEach((key, amount) -> {
            BigInteger updated = finalBalances.getOrDefault(key, BigInteger.ZERO)
                    .add(amount.multiply(repetitions));
            if (updated.signum() < 0) {
                throw new IllegalStateException("An exact Trinity repeat proof produced a negative balance");
            }
            if (updated.signum() == 0) {
                finalBalances.remove(key);
            } else {
                finalBalances.put(key, updated);
            }
        });
        return TrinityAlgorithmResult.success(TrinityCompressedSchedule.repeated(
                List.of(),
                unit,
                repetitions,
                List.of(),
                finalBalances,
                statesVisited));
    }

    private static Map<AEKey, BigInteger> unitNet(List<TrinityVariantFiring> unit) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> netChange = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : unit) {
            firing.variant().netChange().forEach((key, amount) -> {
                BigInteger delta = amount.multiply(firing.count());
                netChange.merge(key, delta, BigInteger::add);
            });
        }
        netChange.values().removeIf(amount -> amount.signum() == 0);
        return netChange;
    }

    private static boolean hasInputs(Map<AEKey, BigInteger> balances, Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().allMatch(entry -> balances
                .getOrDefault(entry.getKey(), BigInteger.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copyBalances(
                                                                                  Map<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity deterministic repeat balances cannot be negative");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return copied;
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
}
