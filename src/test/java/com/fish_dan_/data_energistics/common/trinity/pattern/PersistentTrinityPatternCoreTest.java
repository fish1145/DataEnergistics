package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolution;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternRecipeIdResolvers;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternOutputRouter.PendingOutputCursor;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import appeng.crafting.pattern.EncodedStonecuttingPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class PersistentTrinityPatternCoreTest {

    private static final UUID HOST_ID = UUID.fromString("f14921fa-5649-4f5f-98c3-41af0ea28b12");
    private static final ResourceLocation TEST_RESOLVER_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "test_pattern");

    private PersistentTrinityPatternCoreTest() {}

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
        PersistentTrinityPatternCore core = core(64);
        ItemStack unsupported = new ItemStack(Items.STICK);
        ItemStack pattern = pattern(Items.PAPER);

        assertEquals(0L, core.revision());
        assertFalse(core.trySetPattern(0, unsupported));
        assertEquals(0L, core.revision());
        assertTrue(core.pattern(0).isEmpty());
        assertTrue(core.trySetPattern(0, pattern));
        assertEquals(1L, core.revision());
        TrinityPatternCore.PatternCacheSnapshot installedSnapshot = core.patternCacheSnapshot();
        TrinityPatternCore.CachedPattern installedCache = core.cachedPattern(0);
        List<Integer> installedSlots = core.occupiedPatternSlots();
        assertTrue(installedCache != null);
        assertEquals(AEItemKey.of(Items.PAPER), installedCache.encodedDefinition());
        assertTrue(installedCache.definition().matchesPattern(new ItemStack(Items.PAPER)));
        assertTrue(installedCache.recipeResolution() != null);
        assertEquals(List.of(0), core.occupiedPatternSlots());

        assertTrue(core.trySetPattern(0, pattern(Items.PAPER).copyWithCount(64)));
        assertEquals(1L, core.revision());
        assertTrue(installedSnapshot == core.patternCacheSnapshot());
        assertTrue(installedCache == core.cachedPattern(0));
        assertTrue(installedSlots == core.occupiedPatternSlots());
        assertFalse(core.trySetPattern(0, unsupported));
        assertTrue(core.patternInventory().insertItem(2, pattern(Items.MAP), true).isEmpty());
        assertTrue(core.pattern(2).isEmpty());
        assertTrue(core.patternInventory().extractItem(0, 1, true).is(Items.PAPER));
        assertEquals(1L, core.revision());
        assertTrue(installedSnapshot == core.patternCacheSnapshot());
        assertTrue(installedCache == core.cachedPattern(0));
        assertTrue(installedSlots == core.occupiedPatternSlots());

        pattern.setCount(0);
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertEquals(1, core.pattern(0).getCount());
        assertTrue(core.decodedPattern(0) instanceof TestSupportedPattern);
        assertEquals(1, core.patternInventory().getSlotLimit(0));

        ItemStack insertedRemainder = core.patternInventory().insertItem(1, pattern(Items.MAP).copyWithCount(2), false);
        assertTrue(core.pattern(1).is(Items.MAP));
        assertEquals(1, insertedRemainder.getCount());
        assertEquals(2L, core.revision());
        assertEquals(List.of(0, 1), core.occupiedPatternSlots());
        assertTrue(core.patternInventory().extractItem(1, 1, false).is(Items.MAP));
        assertTrue(core.pattern(1).isEmpty());
        assertEquals(3L, core.revision());
        assertEquals(List.of(0), core.occupiedPatternSlots());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_move_changes_only_source_and_target")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void movingPatternChangesOnlySourceAndTargetDirectoryEntries(GameTestHelper helper) {
        ArrayList<TrinityPatternSlot.Change> catalogChanges = new ArrayList<>();
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                64,
                PersistentTrinityPatternCoreTest::decode,
                testResolvers(),
                change -> {
                    if (change.kind() == TrinityPatternSlot.ChangeKind.CATALOG) {
                        catalogChanges.add(change);
                    }
                });
        ItemStack pattern = pattern(Items.PAPER);
        assertTrue(core.trySetPattern(3, pattern));
        long revisionBeforeMove = core.revision();
        catalogChanges.clear();

        assertTrue(core.trySetPattern(7, pattern));
        assertTrue(core.trySetPattern(3, ItemStack.EMPTY));

        assertEquals(revisionBeforeMove + 2L, core.revision());
        assertEquals(List.of(7), core.occupiedPatternSlots());
        assertEquals(List.of(7, 3), catalogChanges.stream().map(TrinityPatternSlot.Change::slot).toList());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_reload_visits_only_occupied_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refreshAllPatternCachesVisitsOnlyOccupiedSlots(GameTestHelper helper) {
        AtomicInteger decodeCalls = new AtomicInteger();
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                512,
                stack -> {
                    decodeCalls.incrementAndGet();
                    return decode(stack);
                },
                testResolvers(),
                change -> {});
        assertTrue(core.trySetPattern(3, pattern(Items.PAPER)));
        assertTrue(core.trySetPattern(500, pattern(Items.MAP)));
        decodeCalls.set(0);
        var directory = core.patternCacheSnapshot();
        List<Integer> occupiedSlots = core.occupiedPatternSlots();

        core.refreshPatternCache(250);
        core.refreshAllPatternCaches();

        assertEquals(2, decodeCalls.get());
        assertEquals(List.of(3, 500), core.occupiedPatternSlots());
        assertTrue(occupiedSlots == core.occupiedPatternSlots());
        assertTrue(directory == core.patternCacheSnapshot());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_executes_next_tick_in_fifo_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void enqueueIsAtomicAndExecutionStartsOnNextTickInFifoOrder(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        core.trySetPattern(4, pattern);
        long catalogRevision = core.revision();
        PatternRoute route = route(core, 4);
        List<ItemStack> first = inputs(new ItemStack(Items.IRON_INGOT));
        List<ItemStack> second = inputs(new ItemStack(Items.GOLD_INGOT));

        assertTrue(core.enqueueBatch(route, pattern, first, 20L));
        assertTrue(core.enqueueBatch(route, pattern, second, 20L));
        assertFalse(core.enqueueBatch(route(core, 5), pattern, first, 20L));
        assertThrows(
                IllegalArgumentException.class,
                () -> core.enqueueBatch(
                        new PatternRoute(HOST_ID, UUID.randomUUID(), 4),
                        pattern,
                        first,
                        20L));
        assertThrows(IllegalArgumentException.class, () -> core.enqueueBatch(route, pattern, List.of(new ItemStack(Items.DIAMOND)), 20L));
        assertEquals(2, core.queuedBatchCount(4));
        assertEquals(catalogRevision, core.revision());
        first.getFirst().setCount(4);
        List<ItemStack> exposedInputs = core.queuedBatches(4).getFirst().inputs();
        exposedInputs.getFirst().setCount(8);
        assertEquals(1, core.queuedBatches(4).getFirst().inputs().getFirst().getCount());

        List<String> executed = new ArrayList<>();
        assertEquals(0, core.executeReadyBatches(20L, (slot, batch) -> {
            executed.add(batch.inputs().getFirst().getItem().toString());
            return TrinityPatternCore.BatchExecutionResult.completed(batch, List.of());
        }));
        assertEquals(2, core.executeReadyBatches(21L, (slot, batch) -> {
            executed.add(batch.inputs().getFirst().is(Items.IRON_INGOT) ? "iron" : "gold");
            return TrinityPatternCore.BatchExecutionResult.completed(
                    batch, List.of(new ItemStack(Items.DIAMOND)));
        }));

        assertEquals(List.of("iron", "gold"), executed);
        assertEquals(0, core.queuedBatchCount(4));
        assertEquals(1, core.pendingOutputs(route).size());
        assertAmount(Items.DIAMOND, 2L, core.pendingOutputs(route).getFirst());
        assertTrue(core.pendingOutputs(new PatternRoute(UUID.randomUUID(), core.coreId(), 4)).isEmpty());
        assertEquals(catalogRevision, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_execution_transfers_work_to_pending")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void executionRetainsWorkingSlotUntilCountedOutputIsConsumed(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 6);
        assertTrue(core.trySetPattern(6, pattern));
        assertTrue(core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));

        assertEquals(1, core.executeReadyBatches(
                2L,
                (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(
                        batch, List.of(new ItemStack(Items.DIAMOND, 3)))));

        assertEquals(0, core.queuedBatchCount(6));
        assertEquals(List.of(6), core.workingSlots(HOST_ID));
        assertEquals(List.of(6), core.pendingOutputSlots(HOST_ID));
        assertAmount(Items.DIAMOND, 3L, core.pendingOutputs(route).getFirst());
        try (PendingOutputCursor cursor = core.openPendingOutputCursor(route)) {
            assertTrue(cursor.advance());
            cursor.consumeCurrent(2L);
            assertAmount(Items.DIAMOND, 1L, cursor.current());
            cursor.consumeCurrent(1L);
            assertFalse(cursor.advance());
        }
        assertTrue(core.workingSlots(HOST_ID).isEmpty());
        assertTrue(core.pendingOutputSlots(HOST_ID).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_enqueue_reports_typed_changes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void enqueueReportsPersistentChangesAndOnlyOneWorkTransition(GameTestHelper helper) {
        List<TrinityPatternSlot.ChangeKind> changes = new ArrayList<>();
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                64,
                PersistentTrinityPatternCoreTest::decode,
                testResolvers(),
                change -> changes.add(change.kind()));
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 0);
        assertTrue(core.trySetPattern(0, pattern));
        changes.clear();

        assertTrue(core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertTrue(core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertEquals(1, core.queuedBatchCount(0));
        assertEquals(
                List.of(
                        TrinityPatternSlot.ChangeKind.PERSISTENT,
                        TrinityPatternSlot.ChangeKind.WORK,
                        TrinityPatternSlot.ChangeKind.PERSISTENT),
                changes);
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_merges_only_exact_adjacent_groups")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void enqueueMergesOnlyExactAdjacentGroups(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 2);
        List<ItemStack> iron = inputs(new ItemStack(Items.IRON_INGOT));
        List<ItemStack> gold = inputs(new ItemStack(Items.GOLD_INGOT));
        assertTrue(core.trySetPattern(2, pattern));

        assertTrue(core.enqueueBatch(route, pattern, iron, 4L));
        assertTrue(core.enqueueBatch(route, pattern, iron, 4L));
        assertTrue(core.enqueueBatch(route, pattern, iron, 4L));
        assertEquals(1, core.queuedBatchCount(2));
        assertEquals(3L, core.queuedBatches(2).getFirst().count());

        assertTrue(core.enqueueBatch(route, pattern, iron, 5L));
        assertTrue(core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT, 2)), 5L));
        assertTrue(core.enqueueBatch(route, pattern, gold, 5L));
        assertTrue(core.enqueueBatch(new PatternRoute(UUID.randomUUID(), core.coreId(), 2), pattern, gold, 5L));
        assertEquals(5, core.queuedBatchCount(2));
        assertEquals(1L, core.queuedBatches(2).get(1).count());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_merges_ten_thousand_dispatches")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void tenThousandIdenticalDispatchesExecuteAsOneCountedGroup(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 2);
        List<ItemStack> iron = inputs(new ItemStack(Items.IRON_INGOT));
        assertTrue(core.trySetPattern(2, pattern));
        long directoryRevision = core.revision();
        TrinityPatternCore.PatternCacheSnapshot directory = core.patternCacheSnapshot();
        TrinityPatternCore.CachedPattern cachedPattern = core.cachedPattern(2);

        for (int dispatch = 0; dispatch < 10_000; dispatch++) {
            assertTrue(core.enqueueBatch(route, pattern, iron, 4L));
        }

        assertEquals(directoryRevision, core.revision());
        assertTrue(directory == core.patternCacheSnapshot());
        assertTrue(cachedPattern == core.cachedPattern(2));
        assertEquals(1, core.queuedBatchCount(2));
        assertEquals(10_000L, core.queuedBatches(2).getFirst().count());
        AtomicInteger executions = new AtomicInteger();
        assertEquals(1, core.executeReadyBatches(5L, (slot, batch) -> {
            executions.incrementAndGet();
            assertEquals(10_000L, batch.count());
            return TrinityPatternCore.BatchExecutionResult.completed(
                    batch, List.of(new ItemStack(Items.DIAMOND, 2)));
        }));
        assertEquals(1, executions.get());
        assertEquals(directoryRevision, core.revision());
        assertTrue(directory == core.patternCacheSnapshot());
        assertTrue(cachedPattern == core.cachedPattern(2));
        assertEquals(1, core.pendingOutputs(route).size());
        assertAmount(Items.DIAMOND, 20_000L, core.pendingOutputs(route).getFirst());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_slot_definition_uses_complete_pattern")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void definitionIdentityIncludesCompletePatternAndRecipeId(GameTestHelper helper) {
        TrinityPatternSlot slot = new TrinityPatternSlot(
                0, PersistentTrinityPatternCoreTest::decode, testResolvers(), change -> {});
        ItemStack firstPattern = pattern(Items.PAPER);
        firstPattern.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        ItemStack secondPattern = pattern(Items.PAPER);
        secondPattern.set(DataComponents.CUSTOM_NAME, Component.literal("second"));
        PatternRoute route = new PatternRoute(HOST_ID, UUID.randomUUID(), 0);
        List<ItemStack> inputs = inputs(new ItemStack(Items.IRON_INGOT));

        assertTrue(slot.trySetPattern(firstPattern));
        assertTrue(slot.enqueue(route, firstPattern, inputs, 4L));
        assertTrue(slot.trySetPattern(secondPattern));
        assertTrue(slot.enqueue(route, secondPattern, inputs, 4L));

        List<TrinityCraftingBatch> batches = slot.queuedBatches();
        assertEquals(2, batches.size());
        assertFalse(batches.get(0).definitionId() == batches.get(1).definitionId());
        assertTrue(batches.get(0).definition().matchesPattern(firstPattern));
        assertTrue(batches.get(1).definition().matchesPattern(secondPattern));
        assertEquals(batches.get(0).definition().resolution(), batches.get(1).definition().resolution());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_tracks_multiple_hosts_in_one_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostWorkIndexTracksEverySameSlotEnqueueAndCompletion(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        UUID secondHost = UUID.fromString("42de32c2-b693-4fec-9847-fd2e0f89a21e");
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute firstRoute = route(core, 0);
        PatternRoute secondRoute = new PatternRoute(secondHost, core.coreId(), 0);
        assertTrue(core.trySetPattern(0, pattern));
        assertTrue(core.enqueueBatch(
                firstRoute, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertTrue(core.enqueueBatch(
                secondRoute, pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 1L));
        assertTrue(core.hasWork(HOST_ID));
        assertTrue(core.hasWork(secondHost));
        assertEquals(List.of(0), core.workingSlots(HOST_ID));
        assertEquals(List.of(0), core.workingSlots(secondHost));

        assertEquals(1, core.executeReadyBatches(2L, (slot, batch) -> batch.route().equals(firstRoute) ? TrinityPatternCore.BatchExecutionResult.completed(batch, List.of()) : TrinityPatternCore.BatchExecutionResult.paused()));

        assertFalse(core.hasWork(HOST_ID));
        assertTrue(core.hasWork(secondHost));
        assertTrue(core.workingSlots(HOST_ID).isEmpty());
        assertEquals(List.of(0), core.workingSlots(secondHost));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_working_slots_are_sorted_union_snapshots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void workingSlotsUniteQueuedAndPendingRoutesAsSortedImmutableSnapshots(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        UUID otherHost = UUID.fromString("65e2cd06-602f-47d8-86fa-7a3e2b848a99");
        ItemStack pattern = pattern(Items.PAPER);
        assertTrue(core.trySetPattern(9, pattern));
        assertTrue(core.trySetPattern(2, pattern));
        assertTrue(core.enqueueBatch(
                route(core, 9), pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertTrue(core.enqueueBatch(
                route(core, 2), pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 1L));
        core.appendPendingOutputs(route(core, 7), List.of(amount(Items.DIAMOND, 2L)));
        core.appendPendingOutputs(route(core, 2), List.of(amount(Items.EMERALD, 3L)));
        core.appendPendingOutputs(
                new PatternRoute(otherHost, core.coreId(), 1),
                List.of(amount(Items.BUCKET, 1L)));

        List<Integer> snapshot = core.workingSlots(HOST_ID);
        assertEquals(List.of(2, 7, 9), snapshot);
        assertEquals(List.of(2, 7), core.pendingOutputSlots(HOST_ID));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(10));

        core.appendPendingOutputs(route(core, 5), List.of(amount(Items.DIAMOND, 1L)));
        assertEquals(List.of(2, 7, 9), snapshot);
        assertEquals(List.of(2, 5, 7, 9), core.workingSlots(HOST_ID));
        assertEquals(List.of(1), core.workingSlots(otherHost));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_work_events_follow_host_union")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void workEventsTrackHostUnionMembershipDespiteForeignSameSlotWork(GameTestHelper helper) {
        List<TrinityPatternSlot.Change> workChanges = new ArrayList<>();
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                64,
                PersistentTrinityPatternCoreTest::decode,
                testResolvers(),
                change -> {
                    if (change.kind() == TrinityPatternSlot.ChangeKind.WORK) {
                        workChanges.add(change);
                    }
                });
        UUID otherHost = UUID.fromString("ec8b7c10-ce0d-498e-b431-5cb1af9d66f2");
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute ownRoute = route(core, 4);
        PatternRoute otherRoute = new PatternRoute(otherHost, core.coreId(), 4);
        assertTrue(core.trySetPattern(4, pattern));

        assertTrue(core.enqueueBatch(
                ownRoute, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertEquals(1, workChanges.size());
        core.appendPendingOutputs(ownRoute, List.of(amount(Items.DIAMOND, 2L)));
        assertEquals(1, workChanges.size());
        assertTrue(core.enqueueBatch(
                otherRoute, pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 1L));
        assertEquals(2, workChanges.size());

        core.patternSlot(4).clearQueuedBatches(HOST_ID);
        assertEquals(2, workChanges.size());
        assertEquals(List.of(4), core.workingSlots(HOST_ID));
        try (PendingOutputCursor cursor = core.openPendingOutputCursor(ownRoute)) {
            assertTrue(cursor.advance());
            cursor.consumeCurrent(cursor.current().amount());
        }
        assertEquals(3, workChanges.size());
        assertTrue(core.workingSlots(HOST_ID).isEmpty());
        assertEquals(List.of(4), core.workingSlots(otherHost));

        core.patternSlot(4).clearQueuedBatches(otherHost);
        assertEquals(4, workChanges.size());
        assertTrue(core.workingSlots(otherHost).isEmpty());
        assertTrue(workChanges.stream().allMatch(change -> change.slot() == 4));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_slot_max_count_starts_new_group")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void maxCountTailStartsNewGroupWithoutOverflow(GameTestHelper helper) {
        TrinityPatternSlot slot = new TrinityPatternSlot(
                0, PersistentTrinityPatternCoreTest::decode, testResolvers(), change -> {});
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = new PatternRoute(HOST_ID, UUID.randomUUID(), 0);
        List<ItemStack> inputs = inputs(new ItemStack(Items.IRON_INGOT));
        assertTrue(slot.trySetPattern(pattern));
        assertTrue(slot.enqueue(route, pattern, inputs, 7L));
        TrinityPatternDefinition definition = slot.queuedBatches().getFirst().definition();
        slot.replaceQueuedBatches(List.of(TrinityCraftingBatch.resolved(
                7L, route, definition, inputs, Long.MAX_VALUE, true)));

        assertTrue(slot.enqueue(route, pattern, inputs, 7L));

        assertEquals(2, slot.queuedBatchCount());
        assertEquals(Long.MAX_VALUE, slot.queuedBatches().getFirst().count());
        assertEquals(1L, slot.queuedBatches().get(1).count());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_rejects_opaque_and_ambiguous_patterns")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void newOpaquePatternsAreRejectedAndResolverAmbiguityFailsFast(GameTestHelper helper) {
        TrinityPatternRecipeIdResolvers emptyResolvers = new TrinityPatternRecipeIdResolvers(List.of());
        PersistentTrinityPatternCore opaqueCore = new PersistentTrinityPatternCore(
                64, PersistentTrinityPatternCoreTest::decode, emptyResolvers, change -> {});
        assertFalse(opaqueCore.trySetPattern(0, pattern(Items.PAPER)));

        assertThrows(IllegalArgumentException.class,
                () -> new TrinityPatternRecipeIdResolvers(List.of(
                        new TestRecipeIdResolver(),
                        new TestRecipeIdResolver())));

        TrinityPatternRecipeIdResolvers ambiguousResolvers = new TrinityPatternRecipeIdResolvers(List.of(
                new TestRecipeIdResolver(),
                new ConflictingTestRecipeIdResolver()));
        PersistentTrinityPatternCore ambiguousCore = new PersistentTrinityPatternCore(
                64, PersistentTrinityPatternCoreTest::decode, ambiguousResolvers, change -> {});
        assertThrows(IllegalStateException.class,
                () -> ambiguousCore.trySetPattern(0, pattern(Items.PAPER)));

        TrinityPatternRecipeIdResolvers nullRecipeResolvers = new TrinityPatternRecipeIdResolvers(
                List.of(new NullRecipeIdResolver()));
        PersistentTrinityPatternCore nullRecipeCore = new PersistentTrinityPatternCore(
                64, PersistentTrinityPatternCoreTest::decode, nullRecipeResolvers, change -> {});
        assertFalse(nullRecipeCore.trySetPattern(0, pattern(Items.PAPER)));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_recipe_id_resolvers_cover_ae2_builtins")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void builtInResolversReadCraftingStonecuttingAndSmithingRecipeIds(GameTestHelper helper) {
        ResourceLocation craftingRecipe = ResourceLocation.withDefaultNamespace("crafting_table");
        ResourceLocation stonecuttingRecipe = ResourceLocation.withDefaultNamespace("stone_stairs");
        ResourceLocation smithingRecipe = ResourceLocation.withDefaultNamespace("netherite_sword_smithing");
        ItemStack crafting = pattern(Items.PAPER);
        crafting.set(
                AEComponents.ENCODED_CRAFTING_PATTERN,
                new EncodedCraftingPattern(
                        inputs(new ItemStack(Items.OAK_PLANKS)),
                        new ItemStack(Items.CRAFTING_TABLE),
                        craftingRecipe,
                        false,
                        false));
        ItemStack stonecutting = pattern(Items.PAPER);
        stonecutting.set(
                AEComponents.ENCODED_STONECUTTING_PATTERN,
                new EncodedStonecuttingPattern(
                        new ItemStack(Items.STONE),
                        new ItemStack(Items.STONE_STAIRS),
                        false,
                        stonecuttingRecipe));
        ItemStack smithing = pattern(Items.PAPER);
        smithing.set(
                AEComponents.ENCODED_SMITHING_TABLE_PATTERN,
                new EncodedSmithingTablePattern(
                        new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        new ItemStack(Items.DIAMOND_SWORD),
                        new ItemStack(Items.NETHERITE_INGOT),
                        new ItemStack(Items.NETHERITE_SWORD),
                        false,
                        smithingRecipe));
        TrinityPatternRecipeIdResolvers resolvers = TrinityPatternRecipeIdResolvers.createWithBuiltIns();

        assertEquals(
                new TrinityPatternRecipeIdResolution(
                        TrinityPatternRecipeIdResolvers.AE2_CRAFTING, craftingRecipe),
                resolvers.resolve(new TestSupportedPattern(crafting)).orElseThrow());
        assertEquals(
                new TrinityPatternRecipeIdResolution(
                        TrinityPatternRecipeIdResolvers.AE2_STONECUTTING, stonecuttingRecipe),
                resolvers.resolve(new TestSupportedPattern(stonecutting)).orElseThrow());
        assertEquals(
                new TrinityPatternRecipeIdResolution(
                        TrinityPatternRecipeIdResolvers.AE2_SMITHING, smithingRecipe),
                resolvers.resolve(new TestSupportedPattern(smithing)).orElseThrow());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_replacement_sleeps_and_restores_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replacingPatternSleepsOldBatchUntilSamePatternReturns(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
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
            return TrinityPatternCore.BatchExecutionResult.completed(batch, List.of());
        }));
        assertEquals(1, core.queuedBatchCount(0));

        core.trySetPattern(0, original);
        assertEquals(3L, core.revision());
        assertEquals(1, core.executeReadyBatches(3L, (slot, batch) -> {
            executions.incrementAndGet();
            return TrinityPatternCore.BatchExecutionResult.completed(batch, List.of());
        }));
        assertEquals(1, executions.get());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_recipe_id_mismatch_sleeps_and_resumes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void recipeIdMismatchSleepsPersistedGroupUntilIdentityReturns(GameTestHelper helper) {
        ResourceLocation originalRecipe = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "original");
        ResourceLocation changedRecipe = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "changed");
        AtomicReference<ResourceLocation> recipeId = new AtomicReference<>(originalRecipe);
        TrinityPatternRecipeIdResolvers resolvers = new TrinityPatternRecipeIdResolvers(
                List.of(new MutableTestRecipeIdResolver(recipeId)));
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                64, PersistentTrinityPatternCoreTest::decode, resolvers, change -> {});
        ItemStack pattern = pattern(Items.PAPER);
        assertTrue(core.trySetPattern(0, pattern));
        assertTrue(core.enqueueBatch(
                route(core, 0), pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));

        recipeId.set(changedRecipe);
        core.refreshPatternCache(0);
        assertEquals(0, core.executeReadyBatches(
                2L, (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(batch, List.of())));
        assertEquals(1, core.queuedBatchCount(0));

        recipeId.set(originalRecipe);
        core.refreshPatternCache(0);
        assertEquals(1, core.executeReadyBatches(
                3L, (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(batch, List.of())));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_invalid_cache_retains_pattern_and_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidatedPatternCachePausesQueueWithoutDeletingRetainedPattern(GameTestHelper helper) {
        AtomicBoolean decodable = new AtomicBoolean(true);
        AtomicInteger runtimeBindingChanges = new AtomicInteger();
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                64,
                UUID.randomUUID(),
                stack -> stack.is(Items.PAPER) && decodable.get() ? new TestSupportedPattern(stack) : null,
                testResolvers(),
                change -> {
                    if (change.kind() == TrinityPatternSlot.ChangeKind.RUNTIME_BINDING) {
                        runtimeBindingChanges.incrementAndGet();
                    }
                });
        ItemStack pattern = pattern(Items.PAPER);
        assertTrue(core.trySetPattern(0, pattern));
        assertEquals(1L, core.revision());
        TrinityPatternCore.PatternCacheSnapshot directory = core.patternCacheSnapshot();
        TrinityPatternCore.CachedPattern cached = core.cachedPattern(0);
        assertTrue(cached != null);
        assertEquals(0L, cached.runtimeBindingRevision());
        assertTrue(core.enqueueBatch(route(core, 0), pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L));
        assertEquals(1L, core.revision());

        decodable.set(false);
        core.refreshPatternCache(0);
        assertEquals(1L, core.revision());
        assertTrue(directory == core.patternCacheSnapshot());
        assertTrue(cached == core.cachedPattern(0));
        assertEquals(1L, cached.runtimeBindingRevision());
        assertTrue(cached.details() == null);
        assertEquals(1, runtimeBindingChanges.get());

        assertTrue(core.pattern(0).is(Items.PAPER));
        assertEquals(0, core.executeReadyBatches(2L, (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(batch, List.of())));
        assertEquals(1, core.queuedBatchCount(0));

        decodable.set(true);
        core.refreshPatternCache(0);
        assertTrue(core.decodedPattern(0) instanceof TestSupportedPattern);
        assertEquals(1L, core.revision());
        assertTrue(directory == core.patternCacheSnapshot());
        assertEquals(2L, cached.runtimeBindingRevision());
        assertTrue(cached.details() instanceof TestSupportedPattern);
        assertEquals(2, runtimeBindingChanges.get());
        assertEquals(1, core.executeReadyBatches(
                3L,
                (slot, batch) -> TrinityPatternCore.BatchExecutionResult.completed(batch, List.of())));
        assertEquals(1L, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_cache_refresh_is_transient")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cacheRefreshDoesNotMarkPersistentBlockStateChanged(GameTestHelper helper) {
        AtomicInteger persistentChanges = new AtomicInteger();
        PersistentTrinityPatternCore core = new PersistentTrinityPatternCore(
                64,
                UUID.randomUUID(),
                stack -> stack.is(Items.PAPER) ? new TestSupportedPattern(stack) : null,
                testResolvers(),
                change -> {
                    if (change.kind() == TrinityPatternSlot.ChangeKind.PERSISTENT) {
                        persistentChanges.incrementAndGet();
                    }
                });

        assertTrue(core.trySetPattern(0, pattern(Items.PAPER)));
        assertEquals(1, persistentChanges.get());
        TrinityPatternCore.PatternCacheSnapshot directory = core.patternCacheSnapshot();
        TrinityPatternCore.CachedPattern cached = core.cachedPattern(0);

        core.refreshPatternCache(0);
        core.refreshAllPatternCaches();

        assertEquals(1L, core.revision());
        assertTrue(directory == core.patternCacheSnapshot());
        assertTrue(cached == core.cachedPattern(0));
        assertEquals(0L, cached.runtimeBindingRevision());
        assertEquals(1, persistentChanges.get());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_nbt_preserves_routes_fifo_and_outputs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nbtRoundTripPreservesUuidPatternsFifoInputsAndPendingOutputs(GameTestHelper helper) {
        UUID coreId = UUID.fromString("c3d48bd4-ef15-4198-b5a9-26fa2489466a");
        PersistentTrinityPatternCore original = new PersistentTrinityPatternCore(
                512,
                coreId,
                PersistentTrinityPatternCoreTest::decode,
                testResolvers(),
                change -> {});
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(original, 511);
        original.trySetPattern(511, pattern);
        original.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 10L);
        original.enqueueBatch(route, pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 11L);
        original.appendPendingOutputs(route, List.of(
                amount(Items.BUCKET, 1L),
                amount(Items.DIAMOND, 3L)));
        CompoundTag saved = new CompoundTag();

        original.writeToTag(saved, helper.getLevel().registryAccess());

        AtomicInteger hydrationDecodeCalls = new AtomicInteger();
        ArrayList<TrinityPatternSlot.Change> hydrationChanges = new ArrayList<>();
        PersistentTrinityPatternCore loaded = new PersistentTrinityPatternCore(
                512,
                stack -> {
                    hydrationDecodeCalls.incrementAndGet();
                    return decode(stack);
                },
                testResolvers(),
                hydrationChanges::add);
        TrinityPatternSlot stableSlot = loaded.patternSlot(511);
        loaded.hydrateFromTag(saved, helper.getLevel().registryAccess());

        assertEquals(1, hydrationDecodeCalls.get());
        assertTrue(hydrationChanges.isEmpty());
        assertEquals(coreId, loaded.coreId());
        assertTrue(stableSlot == loaded.patternSlot(511));
        assertTrue(stableSlot.pattern().is(Items.PAPER));
        assertEquals(1L, loaded.revision());
        assertEquals(512, loaded.patternCapacity());
        assertTrue(loaded.pattern(511).is(Items.PAPER));
        assertEquals(List.of(511), loaded.occupiedPatternSlots());
        TrinityPatternCore.PatternCacheSnapshot hydratedDirectory = loaded.patternCacheSnapshot();
        TrinityPatternCore.CachedPattern hydratedPattern = loaded.cachedPattern(511);
        List<Integer> hydratedSlots = loaded.occupiedPatternSlots();
        hydrationDecodeCalls.set(0);
        hydrationChanges.clear();
        loaded.readFromTag(saved, helper.getLevel().registryAccess());
        assertEquals(1, hydrationDecodeCalls.get());
        assertTrue(hydrationChanges.isEmpty());
        assertEquals(1L, loaded.revision());
        assertTrue(hydratedDirectory == loaded.patternCacheSnapshot());
        assertTrue(hydratedPattern == loaded.cachedPattern(511));
        assertTrue(hydratedSlots == loaded.occupiedPatternSlots());
        assertEquals(2, loaded.queuedBatchCount(511));
        assertTrue(loaded.queuedBatches(511).get(0).inputs().getFirst().is(Items.IRON_INGOT));
        assertTrue(loaded.queuedBatches(511).get(1).inputs().getFirst().is(Items.GOLD_INGOT));
        assertEquals(route, loaded.queuedBatches(511).getFirst().route());
        assertEquals(2, loaded.pendingOutputs(route).size());
        assertAmount(Items.DIAMOND, 3L, loaded.pendingOutputs(route).get(1));
        assertEquals(List.of(511), loaded.workingSlots(HOST_ID));
        assertEquals(List.of(511), loaded.pendingOutputSlots(HOST_ID));
        assertTrue(loaded.decodedPattern(511) instanceof TestSupportedPattern);
        TrinityPatternCore.PatternCacheSnapshot loadedDirectory = loaded.patternCacheSnapshot();
        assertTrue(loaded.enqueueBatch(route, pattern, inputs(new ItemStack(Items.DIAMOND)), 12L));
        loaded.refreshAllPatternCaches();
        assertEquals(1L, loaded.revision());
        assertTrue(loadedDirectory == loaded.patternCacheSnapshot());
        assertTrue(loaded.enqueueBatch(route, pattern, inputs(new ItemStack(Items.DIAMOND)), 12L));
        assertEquals(1L, loaded.revision());

        CompoundTag malformedQueueState = saved.copy();
        malformedQueueState.getList("slots", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("batches", Tag.TAG_COMPOUND)
                .getCompound(0)
                .remove("route");
        assertThrows(IllegalArgumentException.class, () -> new PersistentTrinityPatternCore(
                512, PersistentTrinityPatternCoreTest::decode, testResolvers(), change -> {})
                .readFromTag(malformedQueueState, helper.getLevel().registryAccess()));

        CompoundTag malformedOutputState = saved.copy();
        malformedOutputState.getList("slots", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("pending_outputs", Tag.TAG_COMPOUND)
                .getCompound(0)
                .remove("route");
        assertThrows(IllegalArgumentException.class, () -> new PersistentTrinityPatternCore(
                512, PersistentTrinityPatternCoreTest::decode, testResolvers(), change -> {})
                .readFromTag(malformedOutputState, helper.getLevel().registryAccess()));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_migrates_legacy_v2_atomically")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void legacyV2StateMigratesToVersionedCurrentStateWithoutLosingWork(GameTestHelper helper) {
        UUID coreId = UUID.fromString("ac854209-4ac5-4d7a-a244-945abcc45ea8");
        PersistentTrinityPatternCore source = new PersistentTrinityPatternCore(
                64,
                coreId,
                PersistentTrinityPatternCoreTest::decode,
                testResolvers(),
                change -> {});
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = new PatternRoute(HOST_ID, coreId, 9);
        assertTrue(source.trySetPattern(9, pattern));
        assertTrue(source.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 17L));
        source.appendPendingOutputs(route, List.of(amount(Items.DIAMOND, 5L)));
        CompoundTag currentState = new CompoundTag();
        source.writeToTag(currentState, helper.getLevel().registryAccess());
        CompoundTag legacyV2State = asLegacyV2State(currentState);
        CompoundTag originalLegacyV2State = legacyV2State.copy();

        PersistentTrinityPatternCore migrated = core(64);
        assertTrue(migrated.hydrateFromTagAndReportMigration(
                legacyV2State,
                helper.getLevel().registryAccess()));

        assertEquals(originalLegacyV2State, legacyV2State);
        assertEquals(coreId, migrated.coreId());
        assertTrue(migrated.pattern(9).is(Items.PAPER));
        assertEquals(1, migrated.queuedBatchCount(9));
        assertEquals(route, migrated.queuedBatches(9).getFirst().route());
        assertTrue(migrated.queuedBatches(9).getFirst().inputs().getFirst().is(Items.IRON_INGOT));
        assertAmount(Items.DIAMOND, 5L, migrated.pendingOutputs(route).getFirst());
        assertFalse(migrated.hasPendingRefund(HOST_ID));

        CompoundTag rewritten = new CompoundTag();
        migrated.writeToTag(rewritten, helper.getLevel().registryAccess());
        assertEquals(3, rewritten.getInt("version"));
        CompoundTag rewrittenOutbox = rewritten.getCompound("refund_outbox");
        assertTrue(rewrittenOutbox.getList("patterns", Tag.TAG_COMPOUND).isEmpty());
        assertTrue(rewrittenOutbox.getList("retained", Tag.TAG_COMPOUND).isEmpty());

        PersistentTrinityPatternCore roundTripped = core(64);
        assertFalse(roundTripped.hydrateFromTagAndReportMigration(
                rewritten,
                helper.getLevel().registryAccess()));
        assertEquals(coreId, roundTripped.coreId());
        assertEquals(1, roundTripped.queuedBatchCount(9));
        assertAmount(Items.DIAMOND, 5L, roundTripped.pendingOutputs(route).getFirst());

        CompoundTag unversionedCurrentState = currentState.copy();
        unversionedCurrentState.remove("version");
        PersistentTrinityPatternCore unversionedCurrent = core(64);
        assertTrue(unversionedCurrent.hydrateFromTagAndReportMigration(
                unversionedCurrentState,
                helper.getLevel().registryAccess()));

        PersistentTrinityPatternCore unchanged = core(64);
        assertTrue(unchanged.trySetPattern(0, pattern(Items.MAP)));
        CompoundTag ambiguousMissingOutbox = legacyV2State.copy();
        ambiguousMissingOutbox.remove("version");
        CompoundTag originalAmbiguousState = ambiguousMissingOutbox.copy();
        assertThrows(
                IllegalArgumentException.class,
                () -> unchanged.readFromTagAndReportMigration(
                        ambiguousMissingOutbox,
                        helper.getLevel().registryAccess()));
        assertEquals(originalAmbiguousState, ambiguousMissingOutbox);
        assertTrue(unchanged.pattern(0).is(Items.MAP));
        assertTrue(unchanged.pattern(9).isEmpty());

        CompoundTag versionedMissingOutbox = currentState.copy();
        versionedMissingOutbox.remove("refund_outbox");
        assertThrows(
                IllegalArgumentException.class,
                () -> unchanged.readFromTagAndReportMigration(
                        versionedMissingOutbox,
                        helper.getLevel().registryAccess()));
        assertTrue(unchanged.pattern(0).is(Items.MAP));
        assertTrue(unchanged.pattern(9).isEmpty());

        CompoundTag mixedV2AndCurrentState = legacyV2State.copy();
        mixedV2AndCurrentState.put("refund_outbox", currentState.getCompound("refund_outbox").copy());
        assertThrows(
                IllegalArgumentException.class,
                () -> unchanged.readFromTagAndReportMigration(
                        mixedV2AndCurrentState,
                        helper.getLevel().registryAccess()));
        assertTrue(unchanged.pattern(0).is(Items.MAP));
        assertTrue(unchanged.pattern(9).isEmpty());

        CompoundTag unsupportedV1State = new CompoundTag();
        unsupportedV1State.putUUID("core_id", coreId);
        unsupportedV1State.putInt("pattern_capacity", 64);
        unsupportedV1State.put("patterns", new ListTag());
        unsupportedV1State.put("queues", new ListTag());
        unsupportedV1State.put("pending_outputs", new ListTag());
        assertThrows(
                IllegalArgumentException.class,
                () -> unchanged.readFromTagAndReportMigration(
                        unsupportedV1State,
                        helper.getLevel().registryAccess()));
        assertTrue(unchanged.pattern(0).is(Items.MAP));
        assertTrue(unchanged.pattern(9).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_rebuilds_multi_host_work_indexes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void persistedLoadRebuildsMultiHostQueuedAndPendingWorkIndexes(GameTestHelper helper) {
        UUID coreId = UUID.fromString("65ffbb90-79f4-4ce1-b52a-9aeff3bc892f");
        UUID otherHost = UUID.fromString("39dc42a8-e0fb-4645-ae41-5c1e542f6c66");
        PersistentTrinityPatternCore source = new PersistentTrinityPatternCore(
                64,
                coreId,
                PersistentTrinityPatternCoreTest::decode,
                testResolvers(),
                change -> {});
        ItemStack paper = pattern(Items.PAPER);
        ItemStack map = pattern(Items.MAP);
        assertTrue(source.trySetPattern(11, paper));
        assertTrue(source.trySetPattern(7, map));
        assertTrue(source.enqueueBatch(
                new PatternRoute(HOST_ID, coreId, 11),
                paper,
                inputs(new ItemStack(Items.IRON_INGOT)),
                1L));
        assertTrue(source.enqueueBatch(
                new PatternRoute(otherHost, coreId, 7),
                map,
                inputs(new ItemStack(Items.GOLD_INGOT)),
                1L));
        source.appendPendingOutputs(
                new PatternRoute(HOST_ID, coreId, 2),
                List.of(amount(Items.DIAMOND, 4L)));
        source.appendPendingOutputs(
                new PatternRoute(otherHost, coreId, 2),
                List.of(amount(Items.EMERALD, 5L)));
        CompoundTag saved = new CompoundTag();
        source.writeToTag(saved, helper.getLevel().registryAccess());

        PersistentTrinityPatternCore loaded = core(64);
        loaded.readFromTag(saved, helper.getLevel().registryAccess());

        assertEquals(List.of(2, 11), loaded.workingSlots(HOST_ID));
        assertEquals(List.of(2, 7), loaded.workingSlots(otherHost));
        assertEquals(List.of(2), loaded.pendingOutputSlots(HOST_ID));
        assertEquals(List.of(2), loaded.pendingOutputSlots(otherHost));
        assertTrue(loaded.hasWork(HOST_ID));
        assertTrue(loaded.hasWork(otherHost));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_definition_mismatch_fails_atomically")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void persistedRecipeIdMismatchAndMalformedResolutionAreAtomic(GameTestHelper helper) {
        PersistentTrinityPatternCore source = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(source, 0);
        source.trySetPattern(0, pattern);
        source.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L);
        CompoundTag saved = new CompoundTag();
        source.writeToTag(saved, helper.getLevel().registryAccess());
        CompoundTag definition = saved.getList("slots", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("definitions", Tag.TAG_COMPOUND)
                .getCompound(0);

        CompoundTag mismatched = saved.copy();
        mismatched.getList("slots", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("definitions", Tag.TAG_COMPOUND)
                .getCompound(0)
                .putString("recipe_id", Data_Energistics.MODID + ":other");
        CompoundTag malformed = saved.copy();
        malformed.getList("slots", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("definitions", Tag.TAG_COMPOUND)
                .getCompound(0)
                .remove("recipe_id");
        PersistentTrinityPatternCore destination = core(64);
        destination.trySetPattern(0, pattern(Items.MAP));
        assertThrows(IllegalArgumentException.class,
                () -> destination.readFromTag(mismatched, helper.getLevel().registryAccess()));
        assertTrue(destination.pattern(0).is(Items.MAP));
        assertThrows(IllegalArgumentException.class,
                () -> destination.readFromTag(malformed, helper.getLevel().registryAccess()));
        assertTrue(destination.pattern(0).is(Items.MAP));

        CompoundTag duplicateDefinition = saved.copy();
        ListTag definitions = duplicateDefinition.getList("slots", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("definitions", Tag.TAG_COMPOUND);
        definitions.add(definitions.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,
                () -> destination.readFromTag(duplicateDefinition, helper.getLevel().registryAccess()));
        assertTrue(destination.pattern(0).is(Items.MAP));
        assertTrue(definition.contains("recipe_id", Tag.TAG_STRING));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_nbt_rejects_mismatched_capacity_atomically")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mismatchedPersistedCapacityFailsBeforeMutatingFreshCore(GameTestHelper helper) {
        PersistentTrinityPatternCore source = core(64);
        source.trySetPattern(0, pattern(Items.PAPER));
        CompoundTag saved = new CompoundTag();
        source.writeToTag(saved, helper.getLevel().registryAccess());

        PersistentTrinityPatternCore destination = core(128);

        assertThrows(IllegalArgumentException.class, () -> destination.readFromTag(saved, helper.getLevel().registryAccess()));
        assertTrue(destination.pattern(0).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_pending_outputs_merge_and_segment_counted_amounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void pendingOutputAppendMergesAdjacentKeysAndSegmentsAtLongMaximum(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        PatternRoute route = route(core, 0);
        PatternRoute otherHostRoute = new PatternRoute(UUID.randomUUID(), core.coreId(), 0);
        core.appendPendingOutputs(route, List.of(
                amount(Items.DIAMOND, Long.MAX_VALUE - 2L),
                amount(Items.DIAMOND, 5L),
                amount(Items.GOLD_INGOT, 4L),
                amount(Items.DIAMOND, 6L)));
        core.appendPendingOutputs(otherHostRoute, List.of(amount(Items.EMERALD, 2L)));
        assertEquals(0L, core.revision());

        List<TrinityItemAmount> firstRead = core.pendingOutputs(route);
        assertEquals(4, firstRead.size());
        assertAmount(Items.DIAMOND, Long.MAX_VALUE, firstRead.get(0));
        assertAmount(Items.DIAMOND, 3L, firstRead.get(1));
        assertAmount(Items.GOLD_INGOT, 4L, firstRead.get(2));
        assertAmount(Items.DIAMOND, 6L, firstRead.get(3));
        assertThrows(UnsupportedOperationException.class,
                () -> firstRead.add(amount(Items.STICK, 1L)));

        core.appendPendingOutputs(route, List.of(amount(Items.DIAMOND, Long.MAX_VALUE)));
        List<TrinityItemAmount> secondRead = core.pendingOutputs(route);
        assertEquals(5, secondRead.size());
        assertAmount(Items.DIAMOND, Long.MAX_VALUE, secondRead.get(3));
        assertAmount(Items.DIAMOND, 6L, secondRead.get(4));
        assertEquals(4, firstRead.size());

        try (PendingOutputCursor cursor = core.openPendingOutputCursor(route)) {
            while (cursor.advance()) {
                cursor.consumeCurrent(cursor.current().amount());
            }
        }
        assertTrue(core.pendingOutputs(route).isEmpty());
        assertAmount(Items.EMERALD, 2L, core.pendingOutputs(otherHostRoute).getFirst());
        assertEquals(0L, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_refund_is_atomic_without_pattern_mutation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundIsAtomicAndDoesNotMutateInstalledPatternState(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 0);
        core.trySetPattern(0, pattern);
        long catalogRevision = core.revision();
        core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT, 2)), 1L);
        core.appendPendingOutputs(route, List.of(amount(Items.GOLD_INGOT, 3L)));
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
        assertEquals(2, accepted.deliveredItems.size());
        assertTrue(accepted.deliveredItems.stream()
                .anyMatch(item -> item.key().equals(AEItemKey.of(Items.IRON_INGOT)) && item.amount() == 2L));
        assertTrue(accepted.deliveredItems.stream()
                .anyMatch(item -> item.key().equals(AEItemKey.of(Items.GOLD_INGOT)) && item.amount() == 3L));
        assertEquals(0L, accepted.deliveredItems.stream()
                .filter(item -> item.key().equals(AEItemKey.of(Items.PAPER)))
                .count());
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertFalse(core.hasWork());
        assertEquals(catalogRevision, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_refund_transaction_locks_mutation_until_rollback")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundTransactionRejectsMutationAndRestoresBeforeDelivery(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(64);
        ItemStack pattern = pattern(Items.PAPER);
        PatternRoute route = route(core, 0);
        core.trySetPattern(0, pattern);
        core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.IRON_INGOT)), 1L);
        assertEquals(List.of(0), core.workingSlots(HOST_ID));

        assertThrows(IllegalArgumentException.class, () -> core.prepareRefund((UUID) null));
        TrinityPatternCore.RefundTransaction transaction = core.prepareRefund(HOST_ID);
        assertThrows(IllegalStateException.class,
                () -> core.appendPendingOutputs(route, List.of(amount(Items.DIAMOND, 1L))));
        assertTrue(transaction.commit());
        assertTrue(core.workingSlots(HOST_ID).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> core.enqueueBatch(route, pattern, inputs(new ItemStack(Items.GOLD_INGOT)), 2L));

        transaction.rollback();

        assertEquals(1, core.queuedBatchCount(0));
        assertEquals(List.of(0), core.workingSlots(HOST_ID));
        core.appendPendingOutputs(route, List.of(amount(Items.DIAMOND, 1L)));
        assertEquals(1, core.pendingOutputs(route).size());
        helper.succeed();
    }

    private static CompoundTag asLegacyV2State(CompoundTag currentState) {
        CompoundTag legacyState = currentState.copy();
        legacyState.putInt("version", 2);
        legacyState.remove("refund_outbox");
        ListTag slots = legacyState.getList("slots", Tag.TAG_COMPOUND);
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            ListTag definitions = slots.getCompound(slotIndex).getList("definitions", Tag.TAG_COMPOUND);
            for (int definitionIndex = 0; definitionIndex < definitions.size(); definitionIndex++) {
                definitions.getCompound(definitionIndex).putBoolean("resolved", true);
            }
        }
        return legacyState;
    }

    private static PersistentTrinityPatternCore core(int capacity) {
        return new PersistentTrinityPatternCore(
                capacity, PersistentTrinityPatternCoreTest::decode, testResolvers(), change -> {});
    }

    private static TrinityPatternRecipeIdResolvers testResolvers() {
        return new TrinityPatternRecipeIdResolvers(List.of(new TestRecipeIdResolver()));
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

    private static TrinityItemAmount amount(ItemLike item, long amount) {
        return new TrinityItemAmount(AEItemKey.of(item), amount);
    }

    private static void assertAmount(ItemLike item, long amount, TrinityItemAmount actual) {
        assertEquals(AEItemKey.of(item), actual.key());
        assertEquals(amount, actual.amount());
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

    /**
     * Captures direct core delivery calls without introducing an external destination into this logic test.
     */
    private static final class RecordingRefundDelivery implements TrinityRefundDelivery {

        private final boolean prepareResult;
        private boolean prepared;
        private boolean delivered;
        private List<TrinityItemAmount> deliveredItems = List.of();

        private RecordingRefundDelivery(boolean prepareResult) {
            this.prepareResult = prepareResult;
        }

        @Override
        public boolean prepare(List<TrinityItemAmount> items) {
            this.prepared = true;
            return this.prepareResult;
        }

        @Override
        public List<TrinityItemAmount> deliver(List<TrinityItemAmount> items) {
            this.delivered = true;
            this.deliveredItems = List.copyOf(items);
            return List.of();
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

    /**
     * Gives the test-only supported pattern a stable recipe identity without weakening production opaque rejection.
     */
    private static final class TestRecipeIdResolver implements TrinityPatternRecipeIdResolver {

        @Override
        public ResourceLocation id() {
            return TEST_RESOLVER_ID;
        }

        @Override
        public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
            return pattern instanceof TestSupportedPattern;
        }

        @Override
        public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
            String path = pattern.getDefinition().toStack().is(Items.PAPER) ? "paper" : "map";
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, path);
        }
    }

    /**
     * Deliberately overlaps the primary test resolver to verify ambiguity rejection.
     */
    private static final class ConflictingTestRecipeIdResolver implements TrinityPatternRecipeIdResolver {

        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "conflicting_test_pattern");
        }

        @Override
        public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
            return pattern instanceof TestSupportedPattern;
        }

        @Override
        public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "conflict");
        }
    }

    /**
     * Violates the resolver contract so the registry's extension boundary can be verified directly.
     */
    private static final class NullRecipeIdResolver implements TrinityPatternRecipeIdResolver {

        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "null_recipe_test_pattern");
        }

        @Override
        public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
            return pattern instanceof TestSupportedPattern;
        }

        @Override
        public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
            return null;
        }
    }

    /**
     * Mutable recipe identity models a data reload changing what an opaque integration resolves.
     */
    private static final class MutableTestRecipeIdResolver implements TrinityPatternRecipeIdResolver {

        private final AtomicReference<ResourceLocation> recipeId;

        private MutableTestRecipeIdResolver(AtomicReference<ResourceLocation> recipeId) {
            this.recipeId = recipeId;
        }

        @Override
        public ResourceLocation id() {
            return TEST_RESOLVER_ID;
        }

        @Override
        public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
            return pattern instanceof TestSupportedPattern;
        }

        @Override
        public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
            return this.recipeId.get();
        }
    }
}
