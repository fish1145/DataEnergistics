package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete non-executable proof that one cyclic component has an exact firing vector and prefix-safe compressed order.
 *
 * <p>
 * This type deliberately does not expose a conversion to {@link TrinityCycleSelection}; diagnostic inventory may
 * contain virtual missing inputs and must never enter graph assembly or an executable incumbent.
 * </p>
 */
public final class TrinityCycleDiagnosticEvidence {

    private final int componentIndex;
    private final TrinityCycleDemand demand;
    private final List<TrinityVariantFiring> prefixOrder;
    private final List<TrinityVariantFiring> localOrder;
    private final BigInteger repetitions;
    private final List<TrinityVariantFiring> suffixOrder;
    private final Map<AEKey, BigInteger> minimumSeed;
    private final Map<AEKey, BigInteger> initialInputs;
    private final Map<AEKey, BigInteger> netChange;
    private final int scheduleStates;
    private final long mipNanos;
    private final TrinityPlanQuality quality;

    /**
     * Freezes all proof data and repeats exact aggregate-net and final-balance validation.
     */
    private TrinityCycleDiagnosticEvidence(
                                           int componentIndex,
                                           TrinityCycleDemand demand,
                                           List<TrinityVariantFiring> prefixOrder,
                                           List<TrinityVariantFiring> localOrder,
                                           BigInteger repetitions,
                                           List<TrinityVariantFiring> suffixOrder,
                                           Map<AEKey, BigInteger> minimumSeed,
                                           Map<AEKey, BigInteger> initialInputs,
                                           Map<AEKey, BigInteger> netChange,
                                           int scheduleStates,
                                           long mipNanos,
                                           TrinityPlanQuality quality) {
        if (componentIndex < 0 || demand == null || prefixOrder == null || localOrder == null || localOrder.isEmpty() ||
                repetitions == null || repetitions.signum() <= 0 || suffixOrder == null || minimumSeed == null ||
                initialInputs == null || netChange == null || scheduleStates < 0 || mipNanos < 0L || quality == null) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle requires a complete schedule proof");
        }
        prefixOrder = List.copyOf(prefixOrder);
        localOrder = List.copyOf(localOrder);
        suffixOrder = List.copyOf(suffixOrder);
        minimumSeed = copyPositiveAmounts(minimumSeed, "minimum seed");
        initialInputs = copyPositiveAmounts(initialInputs, "initial input");
        netChange = copySignedAmounts(netChange);

        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        mergeNet(calculatedNet, prefixOrder, BigInteger.ONE);
        mergeNet(calculatedNet, localOrder, repetitions);
        mergeNet(calculatedNet, suffixOrder, BigInteger.ONE);
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle order must match its exact net change");
        }
        Map<AEKey, BigInteger> copiedInitialInputs = initialInputs;
        minimumSeed.forEach((key, amount) -> {
            if (copiedInitialInputs.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle input must include its minimum seed");
            }
        });
        LinkedHashMap<AEKey, BigInteger> finalBalances = new LinkedHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        if (finalBalances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle final balance cannot be negative");
        }
        this.componentIndex = componentIndex;
        this.demand = demand;
        this.prefixOrder = prefixOrder;
        this.localOrder = localOrder;
        this.repetitions = repetitions;
        this.suffixOrder = suffixOrder;
        this.minimumSeed = minimumSeed;
        this.initialInputs = initialInputs;
        this.netChange = netChange;
        this.scheduleStates = scheduleStates;
        this.mipNanos = mipNanos;
        this.quality = quality;
    }

    /**
     * Creates evidence only from the scalar planner's validated repeat schedule.
     */
    public static TrinityCycleDiagnosticEvidence fromDeterministicPlan(
                                                                       int componentIndex,
                                                                       TrinityCycleDemand demand,
                                                                       TrinityCyclePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("A deterministic Trinity diagnostic plan cannot be null");
        }
        return new TrinityCycleDiagnosticEvidence(
                componentIndex,
                demand,
                List.of(),
                plan.oneCycleOrder(),
                plan.repetitions(),
                List.of(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.schedule().statesVisited(),
                0L,
                TrinityPlanQuality.PROVED_OPTIMAL);
    }

    /**
     * Creates evidence only from the joint evaluator's validated compressed schedule.
     */
    public static TrinityCycleDiagnosticEvidence fromJointPlan(
                                                               int componentIndex,
                                                               TrinityCycleDemand demand,
                                                               TrinityJointCyclePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("A joint Trinity diagnostic plan cannot be null");
        }
        return new TrinityCycleDiagnosticEvidence(
                componentIndex,
                demand,
                List.of(),
                plan.schedule().batches(),
                BigInteger.ONE,
                List.of(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.searchStates(),
                plan.solverNanos(),
                plan.quality());
    }

    /**
     * Copies an already validated executable selection into a type that cannot be submitted for execution.
     */
    public static TrinityCycleDiagnosticEvidence fromSelection(
                                                               TrinityCycleSelection selection,
                                                               TrinityCycleDemand demand) {
        if (selection == null || demand == null) {
            throw new IllegalArgumentException("A Trinity cycle selection cannot be null");
        }
        return new TrinityCycleDiagnosticEvidence(
                selection.componentIndex(),
                demand,
                selection.prefixOrder(),
                selection.localOrder(),
                selection.repetitions(),
                selection.suffixOrder(),
                selection.minimumSeed(),
                selection.initialInputs(),
                selection.netChange(),
                selection.scheduleStates(),
                selection.mipNanos(),
                selection.quality());
    }

    public int componentIndex() {
        return this.componentIndex;
    }

    public TrinityCycleDemand demand() {
        return this.demand;
    }

    public List<TrinityVariantFiring> prefixOrder() {
        return this.prefixOrder;
    }

    public List<TrinityVariantFiring> localOrder() {
        return this.localOrder;
    }

    public BigInteger repetitions() {
        return this.repetitions;
    }

    public List<TrinityVariantFiring> suffixOrder() {
        return this.suffixOrder;
    }

    public Map<AEKey, BigInteger> minimumSeed() {
        return this.minimumSeed;
    }

    public Map<AEKey, BigInteger> initialInputs() {
        return this.initialInputs;
    }

    public Map<AEKey, BigInteger> netChange() {
        return this.netChange;
    }

    public int scheduleStates() {
        return this.scheduleStates;
    }

    public long mipNanos() {
        return this.mipNanos;
    }

    public TrinityPlanQuality quality() {
        return this.quality;
    }

    /**
     * Reconstructs every declared output produced by the validated prefix/repeat/suffix schedule.
     */
    public Map<AEKey, BigInteger> emittedItems() {
        LinkedHashMap<AEKey, BigInteger> emitted = new LinkedHashMap<>();
        mergeOutputs(emitted, this.prefixOrder, BigInteger.ONE);
        mergeOutputs(emitted, this.localOrder, this.repetitions);
        mergeOutputs(emitted, this.suffixOrder, BigInteger.ONE);
        return Collections.unmodifiableMap(emitted);
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

    private static void mergeOutputs(
                                     Map<AEKey, BigInteger> target,
                                     List<TrinityVariantFiring> order,
                                     BigInteger multiplier) {
        order.forEach(firing -> firing.variant().outputs().forEach(
                (key, amount) -> target.merge(
                        key,
                        amount.multiply(firing.count()).multiply(multiplier),
                        BigInteger::add)));
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(
                                                              Map<AEKey, BigInteger> source,
                                                              String role) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
