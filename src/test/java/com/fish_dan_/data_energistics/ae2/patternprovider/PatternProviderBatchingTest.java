package com.fish_dan_.data_energistics.ae2.patternprovider;

import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataKey;

import net.minecraft.core.Direction;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void preservesSingleCraftRoutingForBlockingLocksAndDedicatedMachines() {
        assertFalse(PatternProviderBatching.selectsSingleCraftPath(false, LockCraftingMode.NONE, false));
        assertTrue(PatternProviderBatching.selectsSingleCraftPath(true, LockCraftingMode.NONE, false));
        assertTrue(PatternProviderBatching.selectsSingleCraftPath(false, LockCraftingMode.LOCK_UNTIL_RESULT, false));
        assertTrue(PatternProviderBatching.selectsSingleCraftPath(false, LockCraftingMode.LOCK_UNTIL_PULSE, false));
        assertTrue(PatternProviderBatching.selectsSingleCraftPath(false, LockCraftingMode.NONE, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> PatternProviderBatching.selectsSingleCraftPath(false, null, false));
    }

    @Test
    void expandsOnceAndQueuesActualRemainderWithoutMutatingPrototype() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 2L), counter(dataFlow, 1L));
        SharedCapacityTarget target = new SharedCapacityTarget(9L);
        TargetBatchAccess access = new TargetBatchAccess(target);
        RecordingPattern pattern = new RecordingPattern();
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        assertEquals(4L, PatternProviderBatching.simulateCapacity(target, prototype, 4L));
        PatternProviderBatching.pushExpanded(
                pattern,
                prototype,
                4L,
                access,
                Direction.NORTH,
                () -> transferredInputOwnership.set(true));

        assertTrue(transferredInputOwnership.get());
        assertTrue(access.alerted);
        assertEquals(1, pattern.pushCalls);
        assertEquals(2L, total(pattern.pushedInputs, data));
        assertEquals(1L, total(pattern.pushedInputs, dataFlow));
        assertEquals(9L, target.inserted);
        assertEquals(3L, total(access.sendList));
        assertEquals(Direction.NORTH, access.sendDirection);
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
        TargetBatchAccess access = new TargetBatchAccess(target);
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        PatternProviderBatching.pushExpanded(
                pattern,
                prototype,
                4L,
                access,
                Direction.SOUTH,
                () -> transferredInputOwnership.set(true));

        assertTrue(transferredInputOwnership.get());
        assertTrue(access.alerted);
        assertEquals(2L, pattern.receivedInputs.get(data));
        assertEquals(1L, pattern.receivedInputs.get(dataFlow));
        assertEquals(8L, target.inserted.get(data));
        assertEquals(4L, target.inserted.get(dataFlow));
        assertTrue(access.sendList.isEmpty());
        assertNull(access.sendDirection);
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
    void rejectsNullImmediateArgumentsAtTheirMethodBoundaries() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L));
        RecordingTarget target = new RecordingTarget(Map.of(data, 1L));
        TargetBatchAccess access = new TargetBatchAccess(target);
        RecordingPattern pattern = new RecordingPattern();
        ICraftingProvider provider = new NoopCraftingProvider();

        assertIllegalArgument(
                "Pattern provider capacity target and input prototype must not be null",
                () -> PatternProviderBatching.simulateCapacity(null, prototype, 1L));
        assertIllegalArgument(
                "Pattern provider capacity target and input prototype must not be null",
                () -> PatternProviderBatching.simulateCapacity(target, null, 1L));
        assertIllegalArgument(
                "Pattern details must not be null when expanding a pattern-provider batch",
                () -> PatternProviderBatching.pushExpanded(
                        null,
                        prototype,
                        1L,
                        access,
                        Direction.NORTH,
                        () -> {}));
        assertIllegalArgument(
                "Pattern-provider input prototype must not be null when scaling a batch",
                () -> PatternProviderBatching.scalePrototype(null, 1L));
        assertIllegalArgument(
                "Pattern details and input prototype must not be null when preparing a pattern-provider batch",
                () -> PatternProviderBatching.prepareSingle(provider, null, prototype, 1L));
        assertIllegalArgument(
                "Pattern details and input prototype must not be null when preparing a pattern-provider batch",
                () -> PatternProviderBatching.prepareSingle(provider, pattern, null, 1L));
    }

    @Test
    void rejectsNullExpansionCollaboratorsAtTheirMethodBoundary() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L));
        TargetBatchAccess access = new TargetBatchAccess(new RecordingTarget(Map.of(data, 1L)));
        RecordingPattern pattern = new RecordingPattern();

        assertIllegalArgument(
                "Pattern-provider batch access must not be null",
                () -> PatternProviderBatching.pushExpanded(
                        pattern, prototype, 1L, null, Direction.NORTH, () -> {}));
        assertIllegalArgument(
                "Pattern-provider batch direction must not be null",
                () -> PatternProviderBatching.pushExpanded(
                        pattern, prototype, 1L, access, null, () -> {}));
        assertIllegalArgument(
                "Pattern-provider ownership callback must not be null",
                () -> PatternProviderBatching.pushExpanded(
                        pattern, prototype, 1L, access, Direction.NORTH, null));
    }

    @Test
    void rejectsNullAdmissionCollaboratorsAtTheirMethodBoundary() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L));
        RecordingPattern pattern = new RecordingPattern();

        assertIllegalArgument(
                "Prepared pattern-provider input prototype must not be null",
                () -> PatternProviderBatching.admission(1L, null, ignored -> true));
        assertIllegalArgument(
                "Pattern-provider batch commit must not be null",
                () -> PatternProviderBatching.admission(1L, prototype, null));
        assertIllegalArgument(
                "Prepared pattern-provider input prototype must not be null",
                () -> PatternProviderBatching.ownershipAwareAdmission(1L, null, (ignored, transfer) -> true));
        assertIllegalArgument(
                "Ownership-aware pattern-provider batch commit must not be null",
                () -> PatternProviderBatching.ownershipAwareAdmission(1L, prototype, null));
        assertIllegalArgument(
                "Pattern-provider post-commit action must not be null",
                () -> PatternProviderBatching.prepareStandardBatch(
                        null, null, pattern, prototype, 1L, null));
    }

    @Test
    void rejectsNullPrototypeCountersWithTheirIndex() {
        AEKey data = DataKey.of();
        RecordingTarget target = new RecordingTarget(Map.of(data, 1L));
        KeyCounter[] malformedPrototype = new KeyCounter[] { counter(data, 1L), null };

        assertIllegalArgument(
                "Pattern-provider input prototype counter at index 1 must not be null",
                () -> PatternProviderBatching.simulateCapacity(target, malformedPrototype, 1L));
        assertIllegalArgument(
                "Pattern-provider input prototype counter at index 1 must not be null",
                () -> PatternProviderBatching.scalePrototype(malformedPrototype, 1L));
    }

    @Test
    void validatesEveryExpandedInputBeforeCrossingTheTargetBoundary() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L), counter(dataFlow, Long.MAX_VALUE));
        RecordingInsertTarget target = new RecordingInsertTarget();
        TargetBatchAccess access = new TargetBatchAccess(target);
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        assertThrows(
                ArithmeticException.class,
                () -> PatternProviderBatching.pushExpanded(
                        new OrderedPattern(data, dataFlow),
                        prototype,
                        2L,
                        access,
                        Direction.NORTH,
                        () -> transferredInputOwnership.set(true)));

        assertFalse(transferredInputOwnership.get());
        assertEquals(0L, total(target.inserted));
        assertTrue(access.sendList.isEmpty());
        assertNull(access.sendDirection);
        assertFalse(access.alerted);
        assertEquals(1L, prototype[0].get(data));
        assertEquals(Long.MAX_VALUE, prototype[1].get(dataFlow));
    }

    @Test
    void rejectsNonPositiveBatchCountsBeforeExpandingThePattern() {
        AEKey data = DataKey.of();
        KeyCounter[] prototype = counters(counter(data, 1L));
        RecordingPattern pattern = new RecordingPattern();
        RecordingInsertTarget target = new RecordingInsertTarget();
        TargetBatchAccess access = new TargetBatchAccess(target);
        AtomicBoolean transferredInputOwnership = new AtomicBoolean();

        assertThrows(
                IllegalArgumentException.class,
                () -> PatternProviderBatching.pushExpanded(
                        pattern,
                        prototype,
                        0L,
                        access,
                        Direction.NORTH,
                        () -> transferredInputOwnership.set(true)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PatternProviderBatching.pushExpanded(
                        pattern,
                        prototype,
                        -1L,
                        access,
                        Direction.NORTH,
                        () -> transferredInputOwnership.set(true)));

        assertEquals(0, pattern.pushCalls);
        assertEquals(0L, total(target.inserted));
        assertTrue(access.sendList.isEmpty());
        assertNull(access.sendDirection);
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
    void retainsCompleteBatchWhenSecondModulatedInputThrows() {
        AEKey data = DataKey.of();
        AEKey dataFlow = DataFlowKey.of();
        KeyCounter[] prototype = counters(counter(data, 2L), counter(dataFlow, 1L));
        OrderedPattern pattern = new OrderedPattern(data, dataFlow);
        FaultingTarget target = new FaultingTarget();
        TargetBatchAccess access = new TargetBatchAccess(target);
        var admission = PatternProviderBatching.ownershipAwareAdmission(3L, prototype,
                (inputs, transferOwnership) -> {
                    PatternProviderBatching.pushExpanded(
                            pattern,
                            inputs,
                            3L,
                            access,
                            Direction.WEST,
                            transferOwnership);
                    return true;
                });

        assertFalse(admission.hasTransferredInputOwnership());
        assertThrows(InjectedDispatchException.class, () -> admission.commit(prototype));
        assertTrue(admission.hasTransferredInputOwnership());
        assertTrue(access.alerted);
        assertEquals(6L, access.queuedBeforeFirstMutation.get(data));
        assertEquals(3L, access.queuedBeforeFirstMutation.get(dataFlow));
        assertEquals(6L, target.inserted.get(data));
        assertEquals(0L, target.inserted.get(dataFlow));
        assertEquals(0L, total(access.sendList, data));
        assertEquals(3L, total(access.sendList, dataFlow));
        assertEquals(Direction.WEST, access.sendDirection);
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

    private static long total(List<GenericStack> stacks, AEKey key) {
        long total = 0L;
        for (GenericStack stack : stacks) {
            if (stack.what().equals(key)) {
                total = Math.addExact(total, stack.amount());
            }
        }
        return total;
    }

    private static long total(List<GenericStack> stacks) {
        long total = 0L;
        for (GenericStack stack : stacks) {
            total = Math.addExact(total, stack.amount());
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

    private static void assertIllegalArgument(String message, Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(message, exception.getMessage());
    }

    private static final class TargetBatchAccess implements PatternProviderBatchAccess {

        private final PatternProviderTarget target;
        private final List<GenericStack> sendList = new ArrayList<>();
        private final KeyCounter queuedBeforeFirstMutation = new KeyCounter();
        private Direction sendDirection;
        private boolean alerted;
        private boolean capturedQueuedBatch;

        private TargetBatchAccess(PatternProviderTarget target) {
            this.target = target;
        }

        @Override
        public PatternProviderLogicHost dataEnergistics$getHost() {
            throw unexpectedAccess();
        }

        @Override
        public IManagedGridNode dataEnergistics$getMainNode() {
            throw unexpectedAccess();
        }

        @Override
        public List<IPatternDetails> dataEnergistics$getPatterns() {
            throw unexpectedAccess();
        }

        @Override
        public Set<AEKey> dataEnergistics$getPatternInputs() {
            throw unexpectedAccess();
        }

        @Override
        public List<GenericStack> dataEnergistics$getSendList() {
            return this.sendList;
        }

        @Override
        public int dataEnergistics$getRoundRobinIndex() {
            throw unexpectedAccess();
        }

        @Override
        public void dataEnergistics$setRoundRobinIndex(int roundRobinIndex) {
            throw unexpectedAccess();
        }

        @Override
        public void dataEnergistics$setSendDirection(Direction direction) {
            this.sendDirection = direction;
        }

        @Override
        public Set<Direction> dataEnergistics$invokeGetActiveSides() {
            throw unexpectedAccess();
        }

        @Override
        public PatternProviderTarget dataEnergistics$invokeFindAdapter(Direction side) {
            throw unexpectedAccess();
        }

        @Override
        public void dataEnergistics$alertPendingSendList() {
            this.alerted = true;
        }

        @Override
        public boolean dataEnergistics$invokeSendStacksOut() {
            if (this.sendDirection == null) {
                throw new IllegalStateException("Test batch has no fixed send direction");
            }
            if (!this.capturedQueuedBatch) {
                for (GenericStack stack : this.sendList) {
                    this.queuedBeforeFirstMutation.add(stack.what(), stack.amount());
                }
                this.capturedQueuedBatch = true;
            }

            boolean didSomething = false;
            for (var iterator = this.sendList.listIterator(); iterator.hasNext();) {
                GenericStack stack = iterator.next();
                long inserted = this.target.insert(stack.what(), stack.amount(), Actionable.MODULATE);
                if (inserted >= stack.amount()) {
                    iterator.remove();
                    didSomething = true;
                } else if (inserted > 0L) {
                    iterator.set(new GenericStack(stack.what(), stack.amount() - inserted));
                    didSomething = true;
                }
            }
            if (this.sendList.isEmpty()) {
                this.sendDirection = null;
            }
            return didSomething;
        }

        @Override
        public void dataEnergistics$invokeOnPushPatternSuccess(IPatternDetails patternDetails) {
            throw unexpectedAccess();
        }

        private AssertionError unexpectedAccess() {
            return new AssertionError("Unexpected pattern-provider batch test access");
        }
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

    private static final class NoopCraftingProvider implements ICraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            return true;
        }

        @Override
        public boolean isBusy() {
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
        private final KeyCounter inserted = new KeyCounter();

        @Override
        public long insert(AEKey what, long amount, Actionable type) {
            if (type == Actionable.SIMULATE) {
                return amount;
            }
            this.modulatedInputs++;
            if (this.modulatedInputs == 2) {
                throw new InjectedDispatchException();
            }
            this.inserted.add(what, amount);
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
