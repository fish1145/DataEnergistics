package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.Collections;
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
        if (componentIndex < 0 || localOrder.isEmpty() || repetitions.signum() <= 0 || scheduleStates < 0 ||
                mipNanos < 0L) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle requires a complete schedule proof");
        }
        prefixOrder = Collections.unmodifiableList(prefixOrder);
        localOrder = Collections.unmodifiableList(localOrder);
        suffixOrder = Collections.unmodifiableList(suffixOrder);
        minimumSeed = validatePositiveAmounts(minimumSeed, "minimum seed");
        initialInputs = validatePositiveAmounts(initialInputs, "initial input");
        netChange = validateSignedAmounts(netChange);

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> calculatedNet = new Object2ObjectLinkedOpenHashMap<>();
        mergeNet(calculatedNet, prefixOrder, BigInteger.ONE);
        mergeNet(calculatedNet, localOrder, repetitions);
        mergeNet(calculatedNet, suffixOrder, BigInteger.ONE);
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle order must match its exact net change");
        }
        for (Map.Entry<AEKey, BigInteger> seed : minimumSeed.entrySet()) {
            if (initialInputs.getOrDefault(seed.getKey(), BigInteger.ZERO).compareTo(seed.getValue()) < 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle input must include its minimum seed");
            }
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = new Object2ObjectLinkedOpenHashMap<>(
                initialInputs);
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
                TrinityPlanQuality.VERIFIED_FEASIBLE);
    }

    /**
     * Creates evidence only from the joint evaluator's validated compressed schedule.
     */
    public static TrinityCycleDiagnosticEvidence fromJointPlan(
                                                               int componentIndex,
                                                               TrinityCycleDemand demand,
                                                               TrinityJointCyclePlan plan) {
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

    public List<TrinityVariantFiring> localOrder() {
        return this.localOrder;
    }

    /**
     * Returns immutable one-time prefix evidence; display-only callers must not multiply it by repetitions.
     */
    public List<TrinityVariantFiring> prefixOrder() {
        return this.prefixOrder;
    }

    /**
     * Returns immutable one-time suffix evidence, after all repeats; this does not expose an executable plan.
     */
    public List<TrinityVariantFiring> suffixOrder() {
        return this.suffixOrder;
    }

    public BigInteger repetitions() {
        return this.repetitions;
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
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> emitted = new Object2ObjectLinkedOpenHashMap<>();
        mergeOutputs(emitted, this.prefixOrder, BigInteger.ONE);
        mergeOutputs(emitted, this.localOrder, this.repetitions);
        mergeOutputs(emitted, this.suffixOrder, BigInteger.ONE);
        return Object2ObjectMaps.unmodifiable(emitted);
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

    private static Map<AEKey, BigInteger> validatePositiveAmounts(
                                                                  Map<AEKey, BigInteger> source,
                                                                  String role) {
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle " + role + " must be positive");
            }
        });
        return Collections.unmodifiableMap(source);
    }

    private static Map<AEKey, BigInteger> validateSignedAmounts(Map<AEKey, BigInteger> source) {
        source.forEach((key, amount) -> {
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle net amount must be non-zero");
            }
        });
        return Collections.unmodifiableMap(source);
    }
}
