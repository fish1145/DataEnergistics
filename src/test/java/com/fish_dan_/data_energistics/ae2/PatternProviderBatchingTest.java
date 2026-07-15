package com.fish_dan_.data_energistics.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PatternProviderBatchingTest {

    @Test
    void aggregatesUnitInputsAndUsesSmallestPerKeyCapacity() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L), counter(data, 1L, dataFlow, 3L));
        RecordingTarget target = new RecordingTarget(Map.of(data, 64L, dataFlow, 120L));

        long admitted = PatternProviderBatching.simulateCapacity(target, prototype, 128L);

        assertEquals(32L, admitted);
        assertEquals(256L, target.simulatedRequests.get(data));
        assertEquals(384L, target.simulatedRequests.get(dataFlow));
        assertEquals(1L, prototype[0].get(data));
        assertEquals(1L, prototype[1].get(data));
        assertEquals(3L, prototype[1].get(dataFlow));
    }

    @Test
    void returnsZeroWhenTargetCannotFitOneCompleteCraft() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 4L));
        RecordingTarget target = new RecordingTarget(Map.of(data, 3L));

        assertEquals(0L, PatternProviderBatching.simulateCapacity(target, prototype, 64L));
    }

    @Test
    void limitsInputlessPatternsToOneCraftWhenCapacityCannotBeMeasured() {
        KeyCounter[] prototype = counters(new KeyCounter());
        RecordingTarget target = new RecordingTarget(Map.of());

        assertEquals(1L, PatternProviderBatching.simulateCapacity(target, prototype, 64L));
        assertTrue(target.simulatedRequests.isEmpty());
    }

    @Test
    void advancesRoundRobinPastTargetsWithoutCapacity() {
        assertEquals(2, PatternProviderBatching.nextRoundRobinIndex(0, 1));
        assertEquals(5, PatternProviderBatching.nextRoundRobinIndex(2, 2));
        assertThrows(IllegalArgumentException.class, () -> PatternProviderBatching.nextRoundRobinIndex(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> PatternProviderBatching.nextRoundRobinIndex(0, -1));
    }

    @Test
    void expandsOnceAndQueuesActualRemainderWithoutMutatingPrototype() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 2L), counter(dataFlow, 1L));
        SharedCapacityTarget target = new SharedCapacityTarget(9L);
        KeyCounter remainder = new KeyCounter();
        RecordingPattern pattern = new RecordingPattern();
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        assertEquals(4L, PatternProviderBatching.simulateCapacity(target, prototype, 4L));
        PatternProviderBatching.pushExpanded(
                pattern,
                prototype,
                4L,
                target,
                () -> transferredInputOwnership.set(true),
                remainder::add);

        assertTrue(transferredInputOwnership.get());
        assertEquals(1, pattern.pushCalls);
        assertEquals(2L, total(pattern.pushedInputs, data));
        assertEquals(1L, total(pattern.pushedInputs, dataFlow));
        assertEquals(9L, target.inserted);
        assertEquals(3L, total(remainder));
        assertEquals(2L, prototype[0].get(data));
        assertEquals(1L, prototype[1].get(dataFlow));
    }

    @Test
    void scalesSparsePatternSinkAmountsForEveryAdmittedCraft() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 2L), counter(dataFlow, 1L));
        SparseSingleCraftPattern pattern = new SparseSingleCraftPattern(data, dataFlow);
        RecordingInsertTarget target = new RecordingInsertTarget();
        KeyCounter remainder = new KeyCounter();
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        PatternProviderBatching.pushExpanded(
                pattern,
                prototype,
                4L,
                target,
                () -> transferredInputOwnership.set(true),
                remainder::add);

        assertTrue(transferredInputOwnership.get());
        assertEquals(2L, pattern.receivedInputs.get(data));
        assertEquals(1L, pattern.receivedInputs.get(dataFlow));
        assertEquals(8L, target.inserted.get(data));
        assertEquals(4L, target.inserted.get(dataFlow));
        assertEquals(0L, total(remainder));
        assertEquals(2L, prototype[0].get(data));
        assertEquals(1L, prototype[1].get(dataFlow));
    }

    @Test
    void failsFastOnCapacityAndExpansionOverflow() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 2L));
        RecordingTarget target = new RecordingTarget(Map.of(data, Long.MAX_VALUE));

        assertThrows(
                ArithmeticException.class,
                () -> PatternProviderBatching.simulateCapacity(target, prototype, Long.MAX_VALUE));
        assertThrows(
                ArithmeticException.class,
                () -> PatternProviderBatching.scalePrototype(prototype, Long.MAX_VALUE));
        assertEquals(2L, prototype[0].get(data));
    }

    @Test
    void validatesEveryExpandedInputBeforeCrossingTheTargetBoundary() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L), counter(dataFlow, Long.MAX_VALUE));
        RecordingInsertTarget target = new RecordingInsertTarget();
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        assertThrows(
                ArithmeticException.class,
                () -> PatternProviderBatching.pushExpanded(
                        new OrderedPattern(data, dataFlow),
                        prototype,
                        2L,
                        target,
                        () -> transferredInputOwnership.set(true),
                        (what, amount) -> {}));

        assertFalse(transferredInputOwnership.get());
        assertEquals(0L, total(target.inserted));
        assertEquals(1L, prototype[0].get(data));
        assertEquals(Long.MAX_VALUE, prototype[1].get(dataFlow));
    }

    @Test
    void rejectsNonPositiveBatchCountsBeforeExpandingThePattern() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L));
        RecordingPattern pattern = new RecordingPattern();
        RecordingInsertTarget target = new RecordingInsertTarget();
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        assertThrows(
                IllegalArgumentException.class,
                () -> PatternProviderBatching.pushExpanded(
                        pattern,
                        prototype,
                        0L,
                        target,
                        () -> transferredInputOwnership.set(true),
                        (what, amount) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> PatternProviderBatching.pushExpanded(
                        pattern,
                        prototype,
                        -1L,
                        target,
                        () -> transferredInputOwnership.set(true),
                        (what, amount) -> {}));

        assertEquals(0, pattern.pushCalls);
        assertEquals(0L, total(target.inserted));
        assertFalse(transferredInputOwnership.get());
        assertEquals(1L, prototype[0].get(data));
    }

    @Test
    void admissionRejectsAnotherPrototypeAndASecondCommit() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L));
        KeyCounter[] other = counters(counter(data, 1L));
        AtomicInteger commits = new AtomicInteger();
        var admission = PatternProviderBatching.admission(7L, prototype, inputs -> {
            commits.incrementAndGet();
            assertSame(prototype, inputs);
            return false;
        });

        assertEquals(7L, admission.count());
        assertThrows(IllegalArgumentException.class, () -> admission.commit(other));
        assertFalse(admission.commit(prototype));
        assertEquals(1, commits.get());
        assertEquals(1L, prototype[0].get(data));
        assertThrows(IllegalStateException.class, () -> admission.commit(prototype));
    }

    @Test
    void marksOwnershipBeforeASecondModulatedInputThrows() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 2L), counter(dataFlow, 1L));
        OrderedPattern pattern = new OrderedPattern(data, dataFlow);
        FaultingTarget target = new FaultingTarget();
        KeyCounter remainder = new KeyCounter();
        var admission = PatternProviderBatching.ownershipAwareAdmission(3L, prototype,
                (inputs, transferOwnership) -> {
                    PatternProviderBatching.pushExpanded(
                            pattern,
                            inputs,
                            3L,
                            target,
                            transferOwnership,
                            remainder::add);
                    transferOwnership.run();
                    return true;
                });

        assertFalse(admission.hasTransferredInputOwnership());
        assertThrows(InjectedDispatchException.class, () -> admission.commit(prototype));
        assertTrue(admission.hasTransferredInputOwnership());
        assertEquals(6L, target.inserted);
        assertEquals(0L, total(remainder));
        assertEquals(2L, prototype[0].get(data));
        assertEquals(1L, prototype[1].get(dataFlow));
    }

    private static KeyCounter counter(AEKey firstKey, long firstAmount) {
        KeyCounter counter = new KeyCounter();
        counter.add(firstKey, firstAmount);
        return counter;
    }

    private static KeyCounter counter(
                                      AEKey firstKey,
                                      long firstAmount,
                                      AEKey secondKey,
                                      long secondAmount) {
        KeyCounter counter = counter(firstKey, firstAmount);
        counter.add(secondKey, secondAmount);
        return counter;
    }

    private static KeyCounter[] counters(KeyCounter... counters) {
        return counters;
    }

    private static long total(KeyCounter[] counters, AEKey key) {
        long total = 0L;
        for (KeyCounter counter : counters) {
            total = Math.addExact(total, counter.get(key));
        }
        return total;
    }

    private static long total(KeyCounter counter) {
        long total = 0L;
        for (var entry : counter) {
            total = Math.addExact(total, entry.getLongValue());
        }
        return total;
    }

    private static final class RecordingTarget implements PatternProviderTarget {

        private final Map<AEKey, Long> simulatedCapacities;
        private final Map<AEKey, Long> simulatedRequests = new HashMap<>();

        private RecordingTarget(Map<AEKey, Long> simulatedCapacities) {
            this.simulatedCapacities = simulatedCapacities;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable type) {
            if (type == Actionable.SIMULATE) {
                this.simulatedRequests.put(what, amount);
                return Math.min(amount, this.simulatedCapacities.getOrDefault(what, 0L));
            }
            return amount;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            return false;
        }
    }

    private static final class SharedCapacityTarget implements PatternProviderTarget {

        private long remaining;
        private long inserted;

        private SharedCapacityTarget(long capacity) {
            this.remaining = capacity;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable type) {
            if (type == Actionable.SIMULATE) {
                return amount;
            }
            long accepted = Math.min(amount, this.remaining);
            this.remaining -= accepted;
            this.inserted += accepted;
            return accepted;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            return false;
        }
    }

    private static final class RecordingInsertTarget implements PatternProviderTarget {

        private final KeyCounter inserted = new KeyCounter();

        @Override
        public long insert(AEKey what, long amount, Actionable type) {
            if (type == Actionable.MODULATE) {
                this.inserted.add(what, amount);
            }
            return amount;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            return false;
        }
    }

    private static final class FaultingTarget implements PatternProviderTarget {

        private int modulatedInputs;
        private long inserted;

        @Override
        public long insert(AEKey what, long amount, Actionable type) {
            if (type == Actionable.SIMULATE) {
                return amount;
            }
            this.modulatedInputs++;
            if (this.modulatedInputs == 2) {
                throw new InjectedDispatchException();
            }
            this.inserted = Math.addExact(this.inserted, amount);
            return amount;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            return false;
        }
    }

    private static final class RecordingPattern implements IPatternDetails {

        private int pushCalls;
        private KeyCounter[] pushedInputs;

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            this.pushCalls++;
            this.pushedInputs = inputHolder;
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
        }
    }

    private record OrderedPattern(AEKey first, AEKey second) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            inputSink.pushInput(this.first, total(inputHolder, this.first));
            inputSink.pushInput(this.second, total(inputHolder, this.second));
        }
    }

    private static final class SparseSingleCraftPattern implements IPatternDetails {

        private final AEKey first;
        private final AEKey second;
        private final KeyCounter receivedInputs = new KeyCounter();

        private SparseSingleCraftPattern(AEKey first, AEKey second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            for (KeyCounter inputs : inputHolder) {
                this.receivedInputs.addAll(inputs);
            }
            inputSink.pushInput(this.first, 2L);
            inputSink.pushInput(this.second, 1L);
        }
    }

    private static final class InjectedDispatchException extends RuntimeException {}
}
