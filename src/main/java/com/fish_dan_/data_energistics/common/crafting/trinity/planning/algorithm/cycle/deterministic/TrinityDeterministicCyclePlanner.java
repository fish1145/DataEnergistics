package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Solves a stable deterministic cycle by closed-form net effect and maximum prefix deficit.
 * <p>
 * Exact block-prefix implementation for self multiplication and deterministic multi-step multiplication.
 */
public final class TrinityDeterministicCyclePlanner {

    /**
     * @return planner using the exact compressed scheduler
     */
    public static TrinityDeterministicCyclePlanner create() {
        return new TrinityDeterministicCyclePlanner(TrinityDeterministicRepeatScheduler.create());
    }

    private final TrinityDeterministicRepeatScheduler scheduler;

    TrinityDeterministicCyclePlanner(TrinityDeterministicRepeatScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * @param oneCycleOrder     ordered compact firings in one complete production cycle
     * @param target            requested productive key
     * @param requestedAmount   positive requested delivery
     * @param quantityMode      net-new or final-total semantics
     * @param available         non-negative immutable inventory snapshot
     * @param maxScheduleStates compressed scheduling state bound
     * @param control           cancellation and deadline boundary
     * @return exact compact cycle or stable rejection
     */
    public TrinityAlgorithmResult<TrinityCyclePlan> plan(
                                                         List<TrinityVariantFiring> oneCycleOrder,
                                                         AEKey target,
                                                         BigInteger requestedAmount,
                                                         CraftingQuantityMode quantityMode,
                                                         Map<AEKey, BigInteger> available,
                                                         int maxScheduleStates,
                                                         TrinityPlanningControl control) {
        if (oneCycleOrder == null || oneCycleOrder.isEmpty() || target == null || quantityMode == null ||
                available == null || control == null || requestedAmount == null || requestedAmount.signum() <= 0 ||
                maxScheduleStates <= 0) {
            throw new IllegalArgumentException("A Trinity deterministic cycle request is incomplete");
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        CycleBalance oneCycle = cycleBalance(oneCycleOrder);
        BigInteger targetEffect = oneCycle.netChange().getOrDefault(target, BigInteger.ZERO);
        if (targetEffect.signum() <= 0) {
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.no_productive_cycle"),
                    Map.of("target_effect", targetEffect.toString())));
        }

        BigInteger requiredNet = quantityMode == CraftingQuantityMode.NET_NEW ?
                requestedAmount :
                requestedAmount.subtract(inventory.getOrDefault(target, BigInteger.ZERO)).max(BigInteger.ZERO);
        BigInteger repetitions = ceilDivide(requiredNet, targetEffect);
        if (quantityMode == CraftingQuantityMode.FINAL_TOTAL) {
            repetitions = repetitions.max(BigInteger.ONE);
        }
        Map<AEKey, BigInteger> minimumSeed = repeatedMinimumSeed(oneCycle, repetitions);
        Map<AEKey, BigInteger> netChange = multiply(oneCycle.netChange(), repetitions);
        boolean authoritativeShortage = isSingleTransitionSelfCycle(oneCycleOrder, target);
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(minimumSeed);
        if (quantityMode == CraftingQuantityMode.FINAL_TOTAL) {
            BigInteger targetContribution = requestedAmount
                    .subtract(netChange.getOrDefault(target, BigInteger.ZERO))
                    .max(BigInteger.ZERO);
            if (targetContribution.signum() > 0) {
                initialInputs.merge(target, targetContribution, BigInteger::max);
            }
        }
        for (Map.Entry<AEKey, BigInteger> input : initialInputs.entrySet()) {
            BigInteger availableAmount = inventory.getOrDefault(input.getKey(), BigInteger.ZERO);
            if (availableAmount.compareTo(input.getValue()) < 0) {
                return insufficientInput(
                        input.getKey(),
                        target,
                        input.getValue(),
                        availableAmount,
                        minimumSeed,
                        netChange,
                        authoritativeShortage);
            }
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> aggregateFirings = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : oneCycleOrder) {
            aggregateFirings.merge(
                    firing.variant(),
                    firing.count().multiply(repetitions),
                    BigInteger::add);
        }
        TrinityAlgorithmResult<TrinityCompressedSchedule> schedule = this.scheduler.schedule(
                oneCycleOrder,
                repetitions,
                initialInputs,
                maxScheduleStates,
                control);
        if (!schedule.successful()) {
            return TrinityAlgorithmResult.failure(schedule.diagnostic());
        }
        return TrinityAlgorithmResult.success(new TrinityCyclePlan(
                oneCycleOrder,
                repetitions,
                aggregateFirings,
                minimumSeed,
                initialInputs,
                netChange,
                schedule.value()));
    }

    private static CycleBalance cycleBalance(List<TrinityVariantFiring> order) {
        LinkedHashMap<AEKey, BigInteger> balance = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> seed = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : order) {
            if (firing == null) {
                throw new IllegalArgumentException("A Trinity deterministic cycle cannot contain a null firing");
            }
            TrinityPatternVariant variant = firing.variant();
            BigInteger count = firing.count();
            for (Map.Entry<AEKey, BigInteger> input : variant.inputs().entrySet()) {
                BigInteger delta = variant.netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger requiredBeforeBlock = input.getValue();
                if (delta.signum() < 0) {
                    requiredBeforeBlock = requiredBeforeBlock.add(
                            delta.negate().multiply(count.subtract(BigInteger.ONE)));
                }
                BigInteger deficit = requiredBeforeBlock.subtract(
                        balance.getOrDefault(input.getKey(), BigInteger.ZERO));
                if (deficit.signum() > 0) {
                    seed.merge(input.getKey(), deficit, BigInteger::max);
                }
            }
            variant.netChange().forEach((key, amount) -> balance.merge(key, amount.multiply(count), BigInteger::add));
        }
        balance.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return new CycleBalance(
                Collections.unmodifiableMap(seed),
                Collections.unmodifiableMap(balance));
    }

    private static Map<AEKey, BigInteger> repeatedMinimumSeed(CycleBalance oneCycle,
                                                              BigInteger repetitions) {
        LinkedHashMap<AEKey, BigInteger> seed = new LinkedHashMap<>(oneCycle.minimumSeed());
        oneCycle.netChange().forEach((key, effect) -> {
            if (effect.signum() < 0) {
                BigInteger repeatedDeficit = effect.negate().multiply(repetitions.subtract(BigInteger.ONE));
                seed.merge(key, repeatedDeficit, BigInteger::add);
            }
        });
        seed.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(seed);
    }

    private static Map<AEKey, BigInteger> multiply(Map<AEKey, BigInteger> amounts,
                                                   BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> multiplied = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger result = amount.multiply(multiplier);
            if (result.signum() != 0) {
                multiplied.put(key, result);
            }
        });
        return Collections.unmodifiableMap(multiplied);
    }

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity cycle inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() == 0) {
            return BigInteger.ZERO;
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static boolean isSingleTransitionSelfCycle(
                                                       List<TrinityVariantFiring> oneCycleOrder,
                                                       AEKey target) {
        if (oneCycleOrder.size() != 1) {
            return false;
        }
        TrinityVariantFiring firing = oneCycleOrder.getFirst();
        return firing.count().equals(BigInteger.ONE) &&
                firing.variant().inputs().containsKey(target) &&
                firing.variant().outputs().containsKey(target);
    }

    private static <T> TrinityAlgorithmResult<T> insufficientInput(
                                                                   AEKey key,
                                                                   AEKey target,
                                                                   BigInteger required,
                                                                   BigInteger available,
                                                                   Map<AEKey, BigInteger> minimumSeed,
                                                                   Map<AEKey, BigInteger> netChange,
                                                                   boolean authoritativeShortage) {
        BigInteger missing = required.subtract(available);
        BigInteger netConsumed = netChange.getOrDefault(key, BigInteger.ZERO).negate().max(BigInteger.ZERO);
        InputRole role;
        if (key.equals(target) && available.compareTo(minimumSeed.getOrDefault(key, BigInteger.ZERO)) < 0) {
            role = InputRole.TARGET_CYCLE_SEED;
        } else if (netConsumed.signum() > 0) {
            role = InputRole.NET_CONSUMED_EXTERNAL_INPUT;
        } else {
            role = InputRole.CYCLE_WORKING_SEED;
        }
        Component message = Component.translatable(
                role.translationKey,
                key.getDisplayName(),
                required.toString(),
                available.toString(),
                missing.toString());
        Map<String, String> metadata = Map.of(
                "key", key.toString(),
                "input_role", role.metadataValue,
                "required", required.toString(),
                "available", available.toString(),
                "missing", missing.toString(),
                "net_consumed", netConsumed.toString());
        TrinityPlanningDiagnostic diagnostic = authoritativeShortage ?
                new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        message,
                        metadata,
                        new TrinityPlanningDiagnostic.InputShortage(key, required, available, missing)) :
                new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        message,
                        metadata);
        return TrinityAlgorithmResult.failure(diagnostic);
    }

    private enum InputRole {

        TARGET_CYCLE_SEED(
                "target_cycle_seed",
                "gui.data_energistics.trinity_planning.missing_target_cycle_seed"),
        NET_CONSUMED_EXTERNAL_INPUT(
                "net_consumed_external_input",
                "gui.data_energistics.trinity_planning.missing_external_input"),
        CYCLE_WORKING_SEED(
                "cycle_working_seed",
                "gui.data_energistics.trinity_planning.missing_cycle_working_seed");

        private final String metadataValue;
        private final String translationKey;

        InputRole(String metadataValue, String translationKey) {
            this.metadataValue = metadataValue;
            this.translationKey = translationKey;
        }
    }

    private record CycleBalance(
                                Map<AEKey, BigInteger> minimumSeed,
                                Map<AEKey, BigInteger> netChange) {}
}
