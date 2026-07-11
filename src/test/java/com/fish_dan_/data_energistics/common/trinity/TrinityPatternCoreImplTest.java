package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternCoreImplTest {

    private static final UUID HOST_ID = UUID.fromString("f14921fa-5649-4f5f-98c3-41af0ea28b12");

    private TrinityPatternCoreImplTest() {}

    @TestHolder("trinity_pattern_core_supports_physical_capacities")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void supportsExactlyThreePhysicalCapacities(GameTestHelper helper) {
        assertEquals(64, core(64).patternCapacity());
        assertEquals(128, core(128).patternCapacity());
        assertEquals(512, core(512).patternCapacity());
        assertThrows(IllegalArgumentException.class, () -> core(63));
        assertThrows(IllegalArgumentException.class, () -> core(256));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_inventory_enforces_supported_patterns")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternInventoryRejectsUnsupportedItemsAndCopiesAcceptedPattern(GameTestHelper helper) {
        TrinityPatternCoreImpl core = core(64);
        ItemStack unsupported = new ItemStack(Items.STICK);
        ItemStack pattern = pattern(Items.PAPER);

        assertEquals(0L, core.revision());
        assertFalse(core.trySetPattern(0, unsupported));
        assertEquals(0L, core.revision());
        assertTrue(core.pattern(0).isEmpty());
        assertTrue(core.trySetPattern(0, pattern));
        assertEquals(1L, core.revision());

        pattern.setCount(0);
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertEquals(1, core.pattern(0).getCount());
        assertTrue(core.decodedPattern(0) instanceof TestSupportedPattern);
        assertEquals(1, core.patternInventory().getSlotLimit(0));

        ItemStack insertedRemainder = core.patternInventory().insertItem(1, pattern(Items.MAP).copyWithCount(2), false);
        assertTrue(core.pattern(1).is(Items.MAP));
        assertEquals(1, insertedRemainder.getCount());
        assertEquals(2L, core.revision());
        assertTrue(core.patternInventory().extractItem(1, 1, false).is(Items.MAP));
        assertTrue(core.pattern(1).isEmpty());
        assertEquals(3L, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_executes_next_tick_in_fifo_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void enqueueIsAtomicAndExecutionStartsOnNextTickInFifoOrder(GameTestHelper helper) {
        TrinityPatternCoreImpl core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        core.trySetPattern(4, pattern);
        long catalogRevision = core.revision();
        PatternRoute route = route(core, 4);
        List<ItemStack> first = inputs(new ItemStack(Items.IRON_INGOT));
        List<ItemStack> second = inputs(new ItemStack(Items.GOLD_INGOT));

        assertTrue(core.enqueueBatch(route, pattern, first, 20L));
        assertTrue(core.enqueueBatch(route, pattern, second, 20L));
        assertFalse(core.enqueueBatch(route(core, 5), pattern, first, 20L));
        assertFalse(core.enqueueBatch(
                new PatternRoute(HOST_ID, UUID.randomUUID(), 4),
                pattern,
                first,
                20L));
        assertThrows(IllegalArgumentException.class, () -> core.enqueueBatch(route, pattern, List.of(new ItemStack(Items.DIAMOND)), 20L));
        assertEquals(2, core.queuedBatchCount(4));
        assertEquals(catalogRevision, core.revision());

        List<String> executed = new ArrayList<>();
        assertEquals(0, core.executeReadyBatches(20L, (slot, batch) -> {
            executed.add(batch.inputs().getFirst().getItem().toString());
            return TrinityPatternCore.BatchExecutionResult.completed(List.of());
        }));
        assertEquals(2, core.executeReadyBatches(21L, (slot, batch) -> {
            executed.add(batch.inputs().getFirst().is(Items.IRON_INGOT) ? "iron" : "gold");
            return TrinityPatternCore.BatchExecutionResult.completed(List.of(new ItemStack(Items.DIAMOND)));
        }));

        assertEquals(List.of("iron", "gold"), executed);
        assertEquals(0, core.queuedBatchCount(4));
        assertEquals(2, core.pendingOutputs(route).size());
        assertTrue(core.pendingOutputs(new PatternRoute(UUID.randomUUID(), core.coreId(), 4)).isEmpty());
        assertEquals(catalogRevision, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_replacement_sleeps_and_restores_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replacingPatternSleepsOldBatchUntilSamePatternReturns(GameTestHelper helper) {
        TrinityPatternCoreImpl core = core(64);
        ItemStack original = pattern(Items.PAPER);
        ItemStack replacement = pattern(Items.MAP);
        core.trySetPattern(0, original);
        assertEquals(1L, core.revision());
        core.enqueueBatch(route(core, 0), original, inputs(new ItemStack(Items.IRON_INGOT)), 1L);
        assertEquals(1L, core.revision());
        core.trySetPattern(0, replacement);
        assertEquals(2L, core.revision());

        AtomicInteger executions = new AtomicInteger();
        assertEquals(0, core.executeReadyBatches(2L, (slot, batch) -> {
            executions.incrementAndGet();
            return TrinityPatternCore.BatchExecutionResult.completed(List.of());
        }));
        assertEquals(1, core.queuedBatchCount(0));

        core.trySetPattern(0, original);
        assertEquals(3L, core.revision());
        assertEquals(1, core.executeReadyBatches(3L, (slot, batch) -> {
            executions.incrementAndGet();
            return TrinityPatternCore.BatchExecutionResult.completed(List.of());
        }));
        assertEquals(1, executions.get());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_invalid_cache_retains_pattern_and_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidatedPatternCachePausesQueueWithoutDeletingRetainedPattern(GameTestHelper helper) {
        AtomicBoolean decodable = new AtomicBoolean(true);
        TrinityPatternCoreImpl core = new TrinityPatternCoreImpl(
                64,
                UUID.randomUUID(),
                stack -> stack.is(Items.PAPER) && decodable.get() ? new TestSupportedPattern(stack) : null,
                () -> {});
        ItemStack pattern = pattern(Items.PAPER);
        assertTrue(core.trySetPattern(0, pattern));
        assertEquals(1L, core.revision());
        assertTrue(core.enqueueBatch(route(core, 0), pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertEquals(1L, core.revision());

        decodable.set(false);
        core.refreshPatternCache(0);
        assertEquals(2L, core.revision());

        assertTrue(core.pattern(0).is(Items.PAPER));
        assertEquals(0, core.executeReadyBatches(2L, (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(List.of())));
        assertEquals(1, core.queuedBatchCount(0));

        decodable.set(true);
        assertTrue(core.trySetPattern(0, pattern));
        assertTrue(core.decodedPattern(0) instanceof TestSupportedPattern);
        assertEquals(3L, core.revision());
        assertEquals(1, core.executeReadyBatches(
                3L,
                (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(List.of())));
        assertEquals(3L, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_cache_refresh_is_transient")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cacheRefreshDoesNotMarkPersistentBlockStateChanged(GameTestHelper helper) {
        AtomicInteger persistentChanges = new AtomicInteger();
        TrinityPatternCoreImpl core = new TrinityPatternCoreImpl(
                64,
                UUID.randomUUID(),
                stack -> stack.is(Items.PAPER) ? new TestSupportedPattern(stack) : null,
                persistentChanges::incrementAndGet);

        assertTrue(core.trySetPattern(0, pattern(Items.PAPER)));
        assertEquals(1, persistentChanges.get());

        core.refreshPatternCache(0);
        core.refreshAllPatternCaches();

        assertEquals(3L, core.revision());
        assertEquals(1, persistentChanges.get());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_nbt_preserves_routes_fifo_and_outputs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nbtRoundTripPreservesUuidPatternsFifoInputsAndPendingOutputs(GameTestHelper helper) {
        UUID coreId = UUID.fromString("c3d48bd4-ef15-4198-b5a9-26fa2489466a");
        TrinityPatternCoreImpl original = new TrinityPatternCoreImpl(
                128,
                coreId,
                TrinityPatternCoreImplTest::decode,
                () -> {});
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(original, 127);
        original.trySetPattern(127, pattern);
        original.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 10L);
        original.enqueueBatch(route, pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 11L);
        original.appendPendingOutputs(route, List.of(new ItemStack(Items.BUCKET), new ItemStack(Items.DIAMOND, 3)));
        CompoundTag saved = new CompoundTag();

        original.writeToTag(saved, helper.getLevel().registryAccess());

        TrinityPatternCoreImpl loaded = new TrinityPatternCoreImpl(
                128,
                TrinityPatternCoreImplTest::decode,
                () -> {});
        loaded.readFromTag(saved, helper.getLevel().registryAccess());

        assertEquals(coreId, loaded.coreId());
        assertEquals(1L, loaded.revision());
        assertEquals(128, loaded.patternCapacity());
        assertTrue(loaded.pattern(127).is(Items.PAPER));
        assertEquals(2, loaded.queuedBatchCount(127));
        assertTrue(loaded.queuedBatches(127).get(0).inputs().getFirst().is(Items.IRON_INGOT));
        assertTrue(loaded.queuedBatches(127).get(1).inputs().getFirst().is(Items.GOLD_INGOT));
        assertEquals(route, loaded.queuedBatches(127).getFirst().route());
        assertEquals(2, loaded.pendingOutputs(route).size());
        assertEquals(3, loaded.pendingOutputs(route).get(1).getCount());
        assertFalse(loaded.enqueueBatch(route, pattern, inputs(new ItemStack(Items.DIAMOND)), 12L));
        loaded.refreshAllPatternCaches();
        assertEquals(2L, loaded.revision());
        assertTrue(loaded.enqueueBatch(route, pattern, inputs(new ItemStack(Items.DIAMOND)), 12L));
        assertEquals(2L, loaded.revision());

        CompoundTag malformedQueueState = saved.copy();
        malformedQueueState.getList("queues", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("batches", Tag.TAG_COMPOUND)
                .getCompound(0)
                .remove("route");
        assertThrows(IllegalArgumentException.class, () -> new TrinityPatternCoreImpl(128, TrinityPatternCoreImplTest::decode, () -> {})
                .readFromTag(malformedQueueState, helper.getLevel().registryAccess()));

        CompoundTag malformedOutputState = saved.copy();
        malformedOutputState.getList("pending_outputs", Tag.TAG_COMPOUND).getCompound(0).remove("route");
        assertThrows(IllegalArgumentException.class, () -> new TrinityPatternCoreImpl(128, TrinityPatternCoreImplTest::decode, () -> {})
                .readFromTag(malformedOutputState, helper.getLevel().registryAccess()));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_nbt_rejects_mismatched_capacity_atomically")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mismatchedPersistedCapacityFailsBeforeMutatingFreshCore(GameTestHelper helper) {
        TrinityPatternCoreImpl source = core(64);
        source.trySetPattern(0, pattern(Items.PAPER));
        CompoundTag saved = new CompoundTag();
        source.writeToTag(saved, helper.getLevel().registryAccess());

        TrinityPatternCoreImpl destination = core(128);

        assertThrows(IllegalArgumentException.class, () -> destination.readFromTag(saved, helper.getLevel().registryAccess()));
        assertTrue(destination.pattern(0).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_pending_outputs_are_route_isolated_copies")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void pendingOutputAccessUsesDefensiveCopies(GameTestHelper helper) {
        TrinityPatternCoreImpl core = core(64);
        ItemStack output = new ItemStack(Items.DIAMOND, 4);
        PatternRoute route = route(core, 0);
        PatternRoute otherHostRoute = new PatternRoute(UUID.randomUUID(), core.coreId(), 0);
        core.appendPendingOutputs(route, List.of(output));
        core.appendPendingOutputs(otherHostRoute, List.of(new ItemStack(Items.GOLD_INGOT, 2)));
        assertEquals(0L, core.revision());

        output.setCount(1);
        List<ItemStack> firstRead = core.pendingOutputs(route);
        firstRead.getFirst().setCount(2);
        List<ItemStack> secondRead = core.pendingOutputs(route);

        assertNotSame(firstRead.getFirst(), secondRead.getFirst());
        assertEquals(4, secondRead.getFirst().getCount());
        core.replacePendingOutputs(route, List.of());
        assertTrue(core.pendingOutputs(route).isEmpty());
        assertEquals(2, core.pendingOutputs(otherHostRoute).getFirst().getCount());
        assertEquals(0L, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_refund_is_atomic_without_pattern_mutation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundIsAtomicAndDoesNotMutateInstalledPatternState(GameTestHelper helper) {
        TrinityPatternCoreImpl core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 0);
        core.trySetPattern(0, pattern);
        long catalogRevision = core.revision();
        core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT, 2)), 1L);
        core.appendPendingOutputs(route, List.of(new ItemStack(Items.GOLD_INGOT, 3)));
        assertEquals(catalogRevision, core.revision());

        RecordingRefundDelivery rejected = new RecordingRefundDelivery(false);
        assertFalse(core.tryRefundAll(rejected));
        assertTrue(rejected.prepared);
        assertFalse(rejected.delivered);
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertEquals(1, core.queuedBatchCount(0));
        assertEquals(1, core.pendingOutputs(route).size());
        assertEquals(catalogRevision, core.revision());

        RecordingRefundDelivery accepted = new RecordingRefundDelivery(true);
        assertTrue(core.tryRefundAll(accepted));

        assertTrue(accepted.delivered);
        assertEquals(2, accepted.deliveredStacks.size());
        assertTrue(accepted.deliveredStacks.stream().anyMatch(stack -> stack.is(Items.IRON_INGOT) && stack.getCount() == 2));
        assertTrue(accepted.deliveredStacks.stream().anyMatch(stack -> stack.is(Items.GOLD_INGOT) && stack.getCount() == 3));
        assertEquals(0L, accepted.deliveredStacks.stream().filter(stack -> stack.is(Items.PAPER)).count());
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertFalse(core.hasWork());
        assertEquals(catalogRevision, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_refund_transaction_locks_mutation_until_rollback")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundTransactionRejectsMutationAndRestoresBeforeDelivery(GameTestHelper helper) {
        TrinityPatternCoreImpl core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 0);
        core.trySetPattern(0, pattern);
        core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L);

        assertThrows(IllegalArgumentException.class, () -> core.prepareRefund((UUID) null));
        TrinityPatternCore.RefundTransaction transaction = core.prepareRefund(HOST_ID);
        assertThrows(IllegalStateException.class,
                () -> core.appendPendingOutputs(route, List.of(new ItemStack(Items.DIAMOND))));
        assertTrue(transaction.commit());
        assertThrows(IllegalStateException.class,
                () -> core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 2L));

        transaction.rollback();

        assertEquals(1, core.queuedBatchCount(0));
        core.appendPendingOutputs(route, List.of(new ItemStack(Items.DIAMOND)));
        assertEquals(1, core.pendingOutputs(route).size());
        helper.succeed();
    }

    private static TrinityPatternCoreImpl core(int capacity) {
        return new TrinityPatternCoreImpl(capacity, TrinityPatternCoreImplTest::decode, () -> {});
    }

    private static PatternRoute route(TrinityPatternCore core, int slot) {
        return new PatternRoute(HOST_ID, core.coreId(), slot);
    }

    private static IMolecularAssemblerSupportedPattern decode(ItemStack stack) {
        return stack.is(Items.PAPER) || stack.is(Items.MAP) ? new TestSupportedPattern(stack) : null;
    }

    private static ItemStack pattern(ItemLike item) {
        return new ItemStack(item);
    }

    private static List<ItemStack> inputs(ItemStack first) {
        ArrayList<ItemStack> inputs = new ArrayList<>(TrinityCraftingBatch.INPUT_SLOT_COUNT);
        inputs.add(first.copy());
        for (int slot = 1; slot < TrinityCraftingBatch.INPUT_SLOT_COUNT; slot++) {
            inputs.add(ItemStack.EMPTY);
        }
        return inputs;
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new GameTestAssertException("Expected condition to be false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertNotSame(Object unexpected, Object actual) {
        if (unexpected == actual) {
            throw new GameTestAssertException("Expected different object identities");
        }
    }

    private static <T extends Throwable> void assertThrows(Class<T> expectedType, Runnable action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expectedType.isInstance(exception)) {
                return;
            }
            throw new GameTestAssertException(
                    "Expected " + expectedType.getName() + ", got " + exception.getClass().getName());
        }
        throw new GameTestAssertException("Expected " + expectedType.getName() + " to be thrown");
    }

    /** Captures direct core delivery calls without introducing an external destination into this logic test. */
    private static final class RecordingRefundDelivery implements TrinityRefundDelivery {

        private final boolean prepareResult;
        private boolean prepared;
        private boolean delivered;
        private List<ItemStack> deliveredStacks = List.of();

        private RecordingRefundDelivery(boolean prepareResult) {
            this.prepareResult = prepareResult;
        }

        @Override
        public boolean prepare(List<ItemStack> stacks) {
            this.prepared = true;
            return this.prepareResult;
        }

        @Override
        public void deliver(List<ItemStack> stacks) {
            this.delivered = true;
            this.deliveredStacks = stacks.stream().map(ItemStack::copy).toList();
        }
    }

    private static final class TestSupportedPattern implements IMolecularAssemblerSupportedPattern {

        private final AEItemKey definition;

        private TestSupportedPattern(ItemStack definition) {
            this.definition = AEItemKey.of(definition);
        }

        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return new ItemStack(Items.DIAMOND);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            return NonNullList.withSize(input.size(), ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, AEItemKey key, Level level) {
            return true;
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return slot >= 0 && slot < TrinityCraftingBatch.INPUT_SLOT_COUNT;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {}

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }
    }
}
