package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable schema-independent view of every durable Trinity execution field.
 *
 * <p>
 * The live scheduler converts to and from this model while NBT details remain isolated in the
 * persistence package.
 *
 * @param catalogRevision    graph catalog revision used by the plan
 * @param quantityMode       requested delivery interpretation
 * @param targetKey          final requested key
 * @param targetAmount       exact requested amount
 * @param status             durable execution status
 * @param failureReason      diagnostic retained only by failed executions
 * @param generation         plan generation used to reject stale work
 * @param stages             durable stage definitions and cursors
 * @param stageOrder         stable topological execution order
 * @param repeatBlocks       durable compressed cycle cursors
 * @param seedReserve        initial cycle seed owned by the CPU
 * @param completionSealed   whether output has entered the isolated completion buffer
 * @param completionBuffer   undelivered output currently isolated from cycle inputs
 * @param actualFinalOutputs actual item variants isolated from working inventory
 * @param deliveryRemaining  total target amount still owed to the requester
 * @param borrowingEntries   ownership-preserving dynamic borrowing history
 * @param savedAtTick        non-negative server tick used to convert retry deadlines across a restart
 * @param budgetRetryAt      next tick after a physical budget exhaustion, or {@code -1}
 */
public record TrinityExecutionSnapshot(
                                       long catalogRevision,
                                       CraftingQuantityMode quantityMode,
                                       AEKey targetKey,
                                       long targetAmount,
                                       TrinityPlanExecution.Status status,
                                       String failureReason,
                                       long generation,
                                       List<Stage> stages,
                                       List<Integer> stageOrder,
                                       List<RepeatBlock> repeatBlocks,
                                       Map<AEKey, BigInteger> seedReserve,
                                       boolean completionSealed,
                                       long completionBuffer,
                                       Object2LongMap<AEKey> actualFinalOutputs,
                                       long deliveryRemaining,
                                       Map<AEKey, TrinityBorrowingLedger.Balances> borrowingEntries,
                                       long savedAtTick,
                                       long budgetRetryAt) {

    /**
     * Copies ordered collections so persistence cannot mutate the live scheduler.
     */
    public TrinityExecutionSnapshot {
        stages = List.copyOf(stages);
        stageOrder = List.copyOf(stageOrder);
        repeatBlocks = List.copyOf(repeatBlocks);
        seedReserve = immutableBigAmounts(seedReserve, false, "seed reserve");
        actualFinalOutputs = immutableLongAmounts(actualFinalOutputs, "actual final output");
        borrowingEntries = immutableMap(borrowingEntries);
        if (savedAtTick < 0L) {
            throw new IllegalArgumentException("A Trinity execution save tick cannot be negative");
        }
    }

    /**
     * Persisted wait modes for an individual stage.
     */
    public enum WaitKind {

        NONE,
        INPUT,
        DYNAMIC_INPUT,
        PROVIDER;

        /**
         * @return whether the wait owns a timed retry entry
         */
        public boolean retrying() {
            return this == DYNAMIC_INPUT || this == PROVIDER;
        }
    }

    /**
     * Immutable firing signature and remaining cursor.
     *
     * @param patternIdentity stable definition and publication identity
     * @param primaryOutput   output used to resolve a concrete pattern
     * @param variantOrdinal  bound input variant
     * @param plannedCount    logical firings per plan unit or cycle wave
     * @param outputs         exact pattern-declared outputs per logical firing, including the primary output
     * @param remainingCount  logical firings left in the active unit or wave
     * @param initialized     whether remaining work has been initialized
     */
    public record Firing(
                         TrinityPatternIdentity patternIdentity,
                         AEKey primaryOutput,
                         int variantOrdinal,
                         BigInteger plannedCount,
                         Map<AEKey, BigInteger> outputs,
                         BigInteger remainingCount,
                         boolean initialized) {

        /**
         * Rejects cursors that could create work absent from the plan.
         */
        public Firing {
            outputs = immutableBigAmounts(outputs, false, "firing output");
            if (!outputs.containsKey(primaryOutput)) {
                throw new IllegalArgumentException("A Trinity firing state must retain its primary output");
            }
            if (variantOrdinal < 0 || plannedCount.signum() <= 0 || remainingCount.signum() < 0 ||
                    (!initialized && remainingCount.signum() != 0)) {
                throw new IllegalArgumentException("A Trinity firing state contains an invalid signature or cursor");
            }
        }
    }

    /**
     * Immutable durable state for one DAG or cycle stage.
     *
     * @param index             stable stage index
     * @param cycle             whether the stage belongs to a repeat block
     * @param dependencies      predecessor stage indexes
     * @param currentFiring     active firing cursor
     * @param completed         whether all planned work is complete
     * @param inputKeys         keys that can wake this stage
     * @param waitingKeys       keys currently blocking this stage
     * @param waitKind          current wait classification
     * @param retryAt           timed retry tick, or {@code -1}
     * @param nextDynamicDelay  next dynamic-input backoff delay
     * @param nextProviderDelay next provider backoff delay
     * @param retryVersion      version used to discard stale retry entries
     * @param firings           stable firing signatures and cursors
     * @param requiredAtStart   exact stage prefix requirement
     * @param netChange         exact stage transition balance
     */
    public record Stage(
                        int index,
                        boolean cycle,
                        List<Integer> dependencies,
                        int currentFiring,
                        boolean completed,
                        Set<AEKey> inputKeys,
                        Set<AEKey> waitingKeys,
                        WaitKind waitKind,
                        long retryAt,
                        int nextDynamicDelay,
                        int nextProviderDelay,
                        long retryVersion,
                        List<Firing> firings,
                        Map<AEKey, BigInteger> requiredAtStart,
                        Map<AEKey, BigInteger> netChange) {

        /**
         * Copies ordered collections and rejects malformed local stage metadata.
         */
        public Stage {
            dependencies = immutableIndexes(dependencies, "stage dependency");
            inputKeys = immutableSet(inputKeys);
            waitingKeys = immutableSet(waitingKeys);
            firings = List.copyOf(firings);
            requiredAtStart = immutableBigAmounts(requiredAtStart, false, "stage start requirement");
            netChange = immutableBigAmounts(netChange, true, "stage net change");
            if (index < 0 || dependencies.contains(index) || firings.isEmpty()) {
                throw new IllegalArgumentException("A Trinity stage state requires a valid index and firings");
            }
            if (nextDynamicDelay <= 0 || nextProviderDelay <= 0 || retryVersion < 0L ||
                    !inputKeys.containsAll(waitingKeys)) {
                throw new IllegalArgumentException("A Trinity stage contains invalid retry or waiting-key metadata");
            }
        }
    }

    /**
     * Immutable compressed cycle cursor.
     *
     * @param index                stable repeat-block index
     * @param stageOrder           ordered stages forming one cycle wave
     * @param remainingRepetitions logical repetitions still required
     * @param cursor               active stage position within the wave
     * @param waveCount            repetitions represented by the current compressed wave
     */
    public record RepeatBlock(
                              int index,
                              List<Integer> stageOrder,
                              BigInteger remainingRepetitions,
                              int cursor,
                              BigInteger waveCount) {

        /**
         * Rejects repeat cursors that cannot identify a unique active stage.
         */
        public RepeatBlock {
            stageOrder = immutableIndexes(stageOrder, "repeat stage");
            if (index < 0 || stageOrder.isEmpty() || remainingRepetitions.signum() < 0 ||
                    cursor < 0 || cursor >= stageOrder.size() || waveCount.signum() < 0 ||
                    waveCount.compareTo(remainingRepetitions) > 0) {
                throw new IllegalArgumentException("A Trinity repeat state contains an invalid cursor or count");
            }
        }
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(source));
    }

    private static Map<AEKey, BigInteger> immutableBigAmounts(Map<AEKey, BigInteger> source,
                                                              boolean signed,
                                                              String role) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (signed ? amount.signum() == 0 : amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity " + role + " contains an invalid amount");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Object2LongMap<AEKey> immutableLongAmounts(Object2LongMap<AEKey> source,
                                                              String role) {
        Object2LongMap<AEKey> copied = TrinityLongAmountSnapshot.copyOf(source);
        for (Object2LongMap.Entry<AEKey> entry : copied.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0L) {
                throw new IllegalArgumentException("A Trinity " + role + " contains an invalid amount");
            }
        }
        return copied;
    }

    private static <E> Set<E> immutableSet(Set<E> source) {
        return Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(source));
    }

    private static List<Integer> immutableIndexes(List<Integer> source, String role) {
        IntArrayList copied = new IntArrayList(source.size());
        IntOpenHashSet seen = new IntOpenHashSet();
        for (int index : source) {
            if (index < 0 || !seen.add(index)) {
                throw new IllegalArgumentException("A Trinity " + role + " requires unique non-negative indexes");
            }
            copied.add(index);
        }
        return Collections.unmodifiableList(copied);
    }
}
