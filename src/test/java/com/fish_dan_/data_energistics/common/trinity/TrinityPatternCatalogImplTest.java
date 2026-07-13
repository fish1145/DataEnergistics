package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternCatalogImplTest {

    private TrinityPatternCatalogImplTest() {}

    @TestHolder("trinity_pattern_catalog_sorts_and_validates_mounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sortsMountsAndRejectsCapacityOrIdentityConflicts(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl later = core(UUID.randomUUID());
        TrinityPatternCoreImpl earlier = core(UUID.randomUUID());
        later.trySetPattern(2, new ItemStack(Items.PAPER));
        earlier.trySetPattern(3, new ItemStack(Items.MAP));

        TrinityPatternCatalog.RebuildResult valid = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(5, 4, 3), 64, later),
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 4, 3), 64, earlier)));

        assertTrue(valid.valid());
        assertEquals(List.of(earlier.coreId(), later.coreId()), catalog.mountedCores().stream()
                .map(mount -> mount.core().coreId())
                .toList());
        assertEquals(List.of(earlier.coreId(), later.coreId()), catalog.getAvailablePatterns().stream()
                .map(RoutedCraftingPatternDetails.class::cast)
                .map(details -> details.route().coreId())
                .toList());
        assertUnmodifiable(catalog.mountedCores());
        assertUnmodifiable(catalog.getAvailablePatterns());

        TrinityPatternCatalog.RebuildResult removedCore = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(5, 4, 3), 64, later)));
        assertTrue(removedCore.changed());
        assertEquals(List.of(later.coreId()), catalog.getAvailablePatterns().stream()
                .map(RoutedCraftingPatternDetails.class::cast)
                .map(details -> details.route().coreId())
                .toList());

        TrinityPatternCatalog.RebuildResult reordered = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(5, 4, 3), 64, earlier),
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 4, 3), 64, later)));
        assertTrue(reordered.changed());
        assertEquals(List.of(later.coreId(), earlier.coreId()), catalog.getAvailablePatterns().stream()
                .map(RoutedCraftingPatternDetails.class::cast)
                .map(details -> details.route().coreId())
                .toList());

        TrinityPatternCatalog.RebuildResult capacityFailure = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 128, earlier)));
        assertFalse(capacityFailure.valid());
        assertEquals(BlockPos.ZERO, capacityFailure.failurePosition());
        assertTrue(capacityFailure.failureReason().contains("capacity mismatch"));

        UUID duplicateId = UUID.randomUUID();
        TrinityPatternCoreImpl firstDuplicate = core(duplicateId);
        TrinityPatternCoreImpl secondDuplicate = core(duplicateId);
        TrinityPatternCatalog.RebuildResult duplicateFailure = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 64, secondDuplicate),
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), 64, firstDuplicate)));
        assertFalse(duplicateFailure.valid());
        assertEquals(new BlockPos(2, 0, 0), duplicateFailure.failurePosition());
        assertTrue(duplicateFailure.failureReason().contains(duplicateId.toString()));
        assertTrue(catalog.getAvailablePatterns().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_keeps_duplicate_definitions_routed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsDuplicateDefinitionsAsIndependentRoutes(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl first = core(UUID.randomUUID());
        TrinityPatternCoreImpl second = core(UUID.randomUUID());
        first.trySetPattern(4, new ItemStack(Items.PAPER));
        second.trySetPattern(9, new ItemStack(Items.PAPER));

        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 64, second),
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), 64, first)));

        List<IPatternDetails> published = catalog.getAvailablePatterns();
        assertEquals(2, published.size());
        RoutedCraftingPatternDetails firstRoute = (RoutedCraftingPatternDetails) published.get(0);
        RoutedCraftingPatternDetails secondRoute = (RoutedCraftingPatternDetails) published.get(1);
        assertEquals(firstRoute.getDefinition(), secondRoute.getDefinition());
        assertFalse(firstRoute.equals(secondRoute));
        assertEquals(new PatternRoute(hostId, first.coreId(), 4), firstRoute.route());
        assertEquals(new PatternRoute(hostId, second.coreId(), 9), secondRoute.route());
        RoutedCraftingPatternDetails otherDefinition = new RoutedCraftingPatternDetails(
                firstRoute.route(),
                new FillingPattern(new ItemStack(Items.MAP)));
        assertFalse(firstRoute.equals(otherDefinition));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refreshes_only_changed_core")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refreshesOnlyTheChangedSlotWithinCore(GameTestHelper helper) {
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(UUID.randomUUID());
        TrinityPatternCoreImpl firstDelegate = core(UUID.randomUUID());
        TopologyCapacityCore first = new TopologyCapacityCore(firstDelegate, () -> {});
        TrinityPatternCoreImpl second = core(UUID.randomUUID());
        first.trySetPattern(0, new ItemStack(Items.PAPER));
        first.trySetPattern(1, new ItemStack(Items.MAP));
        second.trySetPattern(0, new ItemStack(Items.MAP));
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)));
        IPatternDetails unchangedBefore = catalog.getAvailablePatterns().get(0);
        IPatternDetails changedBefore = catalog.getAvailablePatterns().get(1);
        IPatternDetails secondBefore = catalog.getAvailablePatterns().get(2);
        List<IPatternDetails> aggregateBefore = catalog.getAvailablePatterns();
        long publicationBefore = catalog.publicationRevision();

        first.trySetPattern(1, new ItemStack(Items.PAPER));
        assertSame(aggregateBefore, catalog.getAvailablePatterns());
        assertEquals(publicationBefore, catalog.publicationRevision());
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));

        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> refreshed = catalog.getAvailablePatterns();
        assertSame(unchangedBefore, refreshed.get(0));
        assertNotSame(changedBefore, refreshed.get(1));
        assertSame(secondBefore, refreshed.get(2));
        assertEquals(publicationBefore + 1L, catalog.publicationRevision());
        assertEquals(1, first.patternCacheSnapshotCalls);
        assertEquals(1, first.cachedPatternCalls);
        assertFalse(catalog.refreshChangedPatterns());
        assertEquals(publicationBefore + 1L, catalog.publicationRevision());
        assertEquals(1, first.patternCacheSnapshotCalls);
        assertEquals(1, first.cachedPatternCalls);

        assertTrue(first.trySetPattern(1, ItemStack.EMPTY));
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));
        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> removed = catalog.getAvailablePatterns();
        assertEquals(2, removed.size());
        assertSame(unchangedBefore, removed.get(0));
        assertSame(secondBefore, removed.get(1));
        assertEquals(publicationBefore + 2L, catalog.publicationRevision());
        assertEquals(1, first.patternCacheSnapshotCalls);
        assertEquals(2, first.cachedPatternCalls);

        assertTrue(first.trySetPattern(1, new ItemStack(Items.MAP)));
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));
        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> republished = catalog.getAvailablePatterns();
        assertEquals(3, republished.size());
        assertSame(unchangedBefore, republished.get(0));
        assertNotSame(changedBefore, republished.get(1));
        assertSame(secondBefore, republished.get(2));
        assertEquals(publicationBefore + 3L, catalog.publicationRevision());
        assertEquals(1, first.patternCacheSnapshotCalls);
        assertEquals(3, first.cachedPatternCalls);

        long publicationBeforeMove = catalog.publicationRevision();
        ItemStack moved = first.patternInventory().extractItem(0, 1, false);
        assertTrue(first.patternInventory().insertItem(2, moved, false).isEmpty());
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.CATALOG));
        catalog.onCoreChanged(
                first,
                new TrinityPatternSlot.Change(2, TrinityPatternSlot.ChangeKind.CATALOG));

        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> movedPatterns = catalog.getAvailablePatterns();
        assertEquals(publicationBeforeMove + 1L, catalog.publicationRevision());
        assertSame(republished.get(1), movedPatterns.get(0));
        assertEquals(2, ((RoutedCraftingPatternDetails) movedPatterns.get(1)).route().slot());
        assertSame(secondBefore, movedPatterns.get(2));
        assertEquals(1, first.patternCacheSnapshotCalls);
        assertEquals(5, first.cachedPatternCalls);
        assertFalse(catalog.refreshChangedPatterns());
        assertEquals(publicationBeforeMove + 1L, catalog.publicationRevision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_reload_and_live_restore_update_only_changed_semantics")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reloadAndLiveRestoreUpdateOnlyChangedRuntimeSemantics(GameTestHelper helper) {
        AtomicReference<ItemStack> output = new AtomicReference<>(new ItemStack(Items.DIAMOND));
        ArrayList<TrinityPatternSlot.Change> changes = new ArrayList<>();
        TrinityPatternCoreImpl changed = new TrinityPatternCoreImpl(
                64,
                UUID.randomUUID(),
                stack -> stack.is(Items.PAPER) || stack.is(Items.MAP) ? new FillingPattern(stack, output.get()) : null,
                TrinityPatternTestResolvers.create(),
                changes::add);
        TrinityPatternCoreImpl unchanged = core(UUID.randomUUID());
        assertTrue(changed.trySetPattern(0, new ItemStack(Items.PAPER)));
        assertTrue(unchanged.trySetPattern(0, new ItemStack(Items.MAP)));
        changes.clear();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(UUID.randomUUID());
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, changed),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, unchanged)));
        List<IPatternDetails> before = catalog.getAvailablePatterns();
        long directoryRevision = changed.revision();
        long publicationRevision = catalog.publicationRevision();
        TrinityPatternCore.PatternCacheSnapshot directory = changed.patternCacheSnapshot();
        TrinityPatternCore.CachedPattern cached = changed.cachedPattern(0);
        assertTrue(cached != null);

        output.set(new ItemStack(Items.EMERALD));
        changed.refreshPatternCache(0);

        assertEquals(directoryRevision, changed.revision());
        assertSame(directory, changed.patternCacheSnapshot());
        assertEquals(1L, cached.runtimeBindingRevision());
        assertEquals(
                List.of(new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING)),
                changes);
        changes.forEach(change -> catalog.onCoreChanged(changed, change));
        assertTrue(catalog.refreshChangedPatterns());

        List<IPatternDetails> after = catalog.getAvailablePatterns();
        assertNotSame(before.get(0), after.get(0));
        assertSame(before.get(1), after.get(1));
        assertEquals(AEItemKey.of(Items.EMERALD), after.get(0).getOutputs().getFirst().what());
        assertEquals(publicationRevision + 1L, catalog.publicationRevision());

        CompoundTag retainedState = new CompoundTag();
        changed.writeToTag(retainedState, helper.getLevel().registryAccess());
        changes.clear();
        output.set(new ItemStack(Items.DIAMOND));
        changed.readFromTag(retainedState, helper.getLevel().registryAccess());

        assertEquals(directoryRevision, changed.revision());
        assertSame(directory, changed.patternCacheSnapshot());
        assertSame(cached, changed.cachedPattern(0));
        assertEquals(2L, cached.runtimeBindingRevision());
        assertEquals(
                List.of(new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING)),
                changes);
        changes.forEach(change -> catalog.onCoreChanged(changed, change));
        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> restoredRuntime = catalog.getAvailablePatterns();
        assertNotSame(after.get(0), restoredRuntime.get(0));
        assertSame(after.get(1), restoredRuntime.get(1));
        assertEquals(AEItemKey.of(Items.DIAMOND), restoredRuntime.get(0).getOutputs().getFirst().what());
        assertEquals(publicationRevision + 2L, catalog.publicationRevision());

        TrinityPatternCoreImpl replacement = new TrinityPatternCoreImpl(
                64,
                changed.coreId(),
                stack -> stack.is(Items.PAPER) || stack.is(Items.MAP) ? new FillingPattern(stack, output.get()) : null,
                TrinityPatternTestResolvers.create(),
                change -> {});
        assertTrue(replacement.trySetPattern(0, new ItemStack(Items.MAP)));
        CompoundTag replacementState = new CompoundTag();
        replacement.writeToTag(replacementState, helper.getLevel().registryAccess());
        changes.clear();
        changed.readFromTag(replacementState, helper.getLevel().registryAccess());

        assertEquals(directoryRevision + 1L, changed.revision());
        assertEquals(List.of(new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.CATALOG)), changes);
        changes.forEach(change -> catalog.onCoreChanged(changed, change));
        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> restoredDirectory = catalog.getAvailablePatterns();
        assertEquals(AEItemKey.of(Items.MAP), restoredDirectory.get(0).getDefinition());
        assertSame(restoredRuntime.get(1), restoredDirectory.get(1));
        assertEquals(publicationRevision + 3L, catalog.publicationRevision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_dispatch_uses_stable_slot_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void repeatedDispatchUsesStableSlotBindingWithoutRefreshingCatalog(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl delegate = core(UUID.randomUUID());
        assertTrue(delegate.trySetPattern(3, new ItemStack(Items.PAPER)));
        TopologyCapacityCore core = new TopologyCapacityCore(delegate, () -> {});
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core)));

        List<IPatternDetails> published = catalog.getAvailablePatterns();
        long publicationBefore = catalog.publicationRevision();
        RoutedCraftingPatternDetails route = (RoutedCraftingPatternDetails) published.getFirst();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        for (int dispatch = 0; dispatch < 128; dispatch++) {
            KeyCounter[] inputs = counters(iron, 1L);
            assertTrue(catalog.pushPattern(route, inputs, 20L));
            assertTrue(inputs[0].isEmpty());
        }

        assertSame(published, catalog.getAvailablePatterns());
        assertEquals(publicationBefore, catalog.publicationRevision());
        assertFalse(catalog.refreshChangedPatterns());
        assertEquals(1, core.patternCacheSnapshotCalls);
        assertEquals(0, core.cachedPatternCalls);
        assertEquals(1, core.patternSlotCalls);
        assertEquals(0, core.patternCalls);
        assertEquals(128, core.cachedEnqueueCalls);
        assertEquals(1, core.workingSlotsCalls);
        assertEquals(1, core.isSlotWorkingCalls);
        assertEquals(1, catalog.activeSlots().size());
        TrinityPatternCatalog.ActiveSlot activeSlot = catalog.activeSlots().getFirst();
        assertEquals(3, activeSlot.globalIndex());
        assertSame(delegate.patternSlot(3), activeSlot.slot());
        assertEquals(new PatternRoute(hostId, delegate.coreId(), 3), activeSlot.route());
        assertEquals(1, delegate.queuedBatchCount(3));
        assertEquals(128L, delegate.queuedBatches(3).getFirst().count());
        assertUnmodifiable(catalog.activeSlots());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_rejects_unflushed_runtime_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsUnflushedRuntimeBindingAfterReload(GameTestHelper helper) {
        AtomicBoolean paperDecodable = new AtomicBoolean(true);
        ArrayList<TrinityPatternSlot.Change> changes = new ArrayList<>();
        TrinityPatternCoreImpl delegate = new TrinityPatternCoreImpl(
                64,
                UUID.randomUUID(),
                stack -> stack.is(Items.MAP) || (paperDecodable.get() && stack.is(Items.PAPER)) ?
                        new FillingPattern(stack) : null,
                TrinityPatternTestResolvers.create(),
                changes::add);
        TopologyCapacityCore core = new TopologyCapacityCore(delegate, () -> {});
        assertTrue(core.trySetPattern(0, new ItemStack(Items.PAPER)));
        assertTrue(core.trySetPattern(1, new ItemStack(Items.MAP)));
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(UUID.randomUUID());
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core)));
        changes.clear();
        assertEquals(2, catalog.getAvailablePatterns().size());
        RoutedCraftingPatternDetails staleRoute = (RoutedCraftingPatternDetails) catalog
                .getAvailablePatterns()
                .getFirst();
        RoutedCraftingPatternDetails unaffectedRoute = (RoutedCraftingPatternDetails) catalog
                .getAvailablePatterns()
                .get(1);
        long directoryRevision = core.revision();
        long publicationRevision = catalog.publicationRevision();

        paperDecodable.set(false);
        delegate.refreshAllPatternCaches();

        assertEquals(
                List.of(new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING)),
                changes);
        assertSame(staleRoute, catalog.getAvailablePatterns().getFirst());
        assertEquals(directoryRevision, core.revision());
        assertEquals(publicationRevision, catalog.publicationRevision());
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter[] inputs = counters(iron, 1L);
        assertFalse(catalog.pushPattern(staleRoute, inputs, 1L));
        assertEquals(1L, inputs[0].get(iron));
        assertEquals(0, core.queuedBatchCount());
        assertSame(staleRoute, catalog.getAvailablePatterns().getFirst());
        assertEquals(2, catalog.getAvailablePatterns().size());

        changes.forEach(change -> catalog.onCoreChanged(core, change));
        assertTrue(catalog.refreshChangedPatterns());
        assertEquals(publicationRevision + 1L, catalog.publicationRevision());
        assertEquals(1, catalog.getAvailablePatterns().size());
        assertSame(unaffectedRoute, catalog.getAvailablePatterns().getFirst());
        assertTrue(delegate.pattern(0).is(Items.PAPER));
        assertTrue(delegate.pattern(1).is(Items.MAP));
        assertFalse(catalog.pushPattern(staleRoute, inputs, 1L));
        assertEquals(1L, inputs[0].get(iron));
        assertEquals(0, core.queuedBatchCount());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_invalidates_changed_core_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidatesChangedCoreIdentityBeforeReadingPatternCaches(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        UUID originalCoreId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        ArrayList<TrinityPatternSlot.Change> changes = new ArrayList<>();
        TrinityPatternCoreImpl core = new TrinityPatternCoreImpl(
                64,
                originalCoreId,
                stack -> stack.is(Items.PAPER) || stack.is(Items.MAP) ? new FillingPattern(stack) : null,
                TrinityPatternTestResolvers.create(),
                changes::add);
        core.trySetPattern(0, new ItemStack(Items.PAPER));
        changes.clear();
        TrinityPatternCatalog.CoreMount mount = new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core);
        catalog.rebuild(List.of(mount));
        TrinityPatternCatalog.LayoutSnapshot originalLayout = catalog.layoutSnapshot();
        RoutedCraftingPatternDetails originalPattern = (RoutedCraftingPatternDetails) catalog
                .getAvailablePatterns()
                .getFirst();

        UUID restoredCoreId = UUID.randomUUID();
        TrinityPatternCoreImpl restoredState = core(restoredCoreId);
        restoredState.trySetPattern(0, new ItemStack(Items.PAPER));
        CompoundTag restoredTag = new CompoundTag();
        restoredState.writeToTag(restoredTag, helper.getLevel().registryAccess());
        core.readFromTag(restoredTag, helper.getLevel().registryAccess());
        assertTrue(changes.isEmpty());
        core.refreshAllPatternCaches();
        catalog.onCoreChanged(
                core,
                new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.CATALOG));

        assertTrue(catalog.refreshChangedPatterns());
        TrinityPatternCatalog.LayoutSnapshot invalidLayout = catalog.layoutSnapshot();
        assertFalse(invalidLayout.active());
        assertEquals(originalLayout.revision() + 1L, invalidLayout.revision());
        assertTrue(catalog.getAvailablePatterns().isEmpty());
        assertTrue(catalog.mountedCores().isEmpty());
        assertFalse(catalog.isMountCurrent(originalLayout.revision(), mount));

        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter[] staleInputs = counters(iron, 1L);
        assertFalse(catalog.pushPattern(originalPattern, staleInputs, 20L));
        assertEquals(1L, staleInputs[0].get(iron));
        assertEquals(0, core.queuedBatchCount());

        TrinityPatternCatalog.RebuildResult restored = catalog.rebuild(List.of(mount));
        TrinityPatternCatalog.LayoutSnapshot restoredLayout = catalog.layoutSnapshot();
        assertTrue(restored.valid());
        assertEquals(invalidLayout.revision() + 1L, restoredLayout.revision());
        RoutedCraftingPatternDetails restoredPattern = (RoutedCraftingPatternDetails) catalog
                .getAvailablePatterns()
                .getFirst();
        assertEquals(restoredCoreId, restoredPattern.route().coreId());
        assertNotSame(originalPattern, restoredPattern);
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_capacity_changes_and_overflow")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void versionsCapacityChangesAndPreservesLayoutWhenTotalCapacityOverflows(GameTestHelper helper) {
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(UUID.randomUUID());
        TrinityPatternCoreImpl small = core(64, UUID.randomUUID());
        TrinityPatternCatalog.CoreMount smallMount = new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, small);
        catalog.rebuild(List.of(smallMount));
        TrinityPatternCatalog.LayoutSnapshot smallLayout = catalog.layoutSnapshot();

        TrinityPatternCoreImpl extended = core(128, UUID.randomUUID());
        extended.trySetPattern(0, new ItemStack(Items.PAPER));
        TrinityPatternCatalog.CoreMount extendedMount = new TrinityPatternCatalog.CoreMount(
                BlockPos.ZERO,
                128,
                extended);
        catalog.rebuild(List.of(extendedMount));
        TrinityPatternCatalog.LayoutSnapshot extendedLayout = catalog.layoutSnapshot();
        IPatternDetails extendedPattern = catalog.getAvailablePatterns().getFirst();

        assertEquals(smallLayout.revision() + 1L, extendedLayout.revision());
        assertEquals(128, extendedLayout.slotCount());
        assertFalse(catalog.isMountCurrent(extendedLayout.revision(), smallMount));
        assertTrue(catalog.isMountCurrent(extendedLayout.revision(), extendedMount));

        TrinityPatternCore huge = new TopologyCapacityCore(Integer.MAX_VALUE);
        TrinityPatternCore overflow = new TopologyCapacityCore(1);
        assertArithmeticOverflow(() -> catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), Integer.MAX_VALUE, huge),
                new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 1, overflow))));

        assertSame(extendedLayout, catalog.layoutSnapshot());
        assertTrue(catalog.isMountCurrent(extendedLayout.revision(), extendedMount));
        assertSame(extendedPattern, catalog.getAvailablePatterns().getFirst());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_versions_contiguous_global_layout")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void versionsContiguousGlobalLayoutAndInvalidatesStaleLookups(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl small = core(64, UUID.randomUUID());
        TrinityPatternCoreImpl extended = core(128, UUID.randomUUID());
        TrinityPatternCoreImpl overlimit = core(512, UUID.randomUUID());
        TrinityPatternCatalog.CoreMount smallMount = new TrinityPatternCatalog.CoreMount(
                new BlockPos(1, 0, 0), 64, small);
        TrinityPatternCatalog.CoreMount extendedMount = new TrinityPatternCatalog.CoreMount(
                new BlockPos(2, 0, 0), 128, extended);
        TrinityPatternCatalog.CoreMount overlimitMount = new TrinityPatternCatalog.CoreMount(
                new BlockPos(3, 0, 0), 512, overlimit);

        TrinityPatternCatalog.RebuildResult formed = catalog.rebuild(List.of(
                overlimitMount,
                smallMount,
                extendedMount));
        TrinityPatternCatalog.LayoutSnapshot firstLayout = catalog.layoutSnapshot();

        assertTrue(formed.valid());
        assertTrue(formed.changed());
        assertTrue(firstLayout.active());
        assertEquals(704, firstLayout.slotCount());
        assertEquals(List.of(0, 64, 192), firstLayout.ranges().stream()
                .map(TrinityPatternCatalog.CoreRange::firstGlobalIndex)
                .toList());
        assertEquals(List.of(64, 192, 704), firstLayout.ranges().stream()
                .map(TrinityPatternCatalog.CoreRange::lastGlobalIndexExclusive)
                .toList());
        assertResolvedSlot(catalog, firstLayout.revision(), 0, small, 0);
        assertResolvedSlot(catalog, firstLayout.revision(), 63, small, 63);
        assertResolvedSlot(catalog, firstLayout.revision(), 64, extended, 0);
        assertResolvedSlot(catalog, firstLayout.revision(), 191, extended, 127);
        assertResolvedSlot(catalog, firstLayout.revision(), 192, overlimit, 0);
        assertResolvedSlot(catalog, firstLayout.revision(), 703, overlimit, 511);
        assertNull(catalog.resolveGlobalSlot(firstLayout.revision(), -1));
        assertNull(catalog.resolveGlobalSlot(firstLayout.revision(), 704));
        assertUnmodifiable(firstLayout.mounts());
        assertUnmodifiable(firstLayout.ranges());
        assertTrue(catalog.isMountCurrent(firstLayout.revision(), smallMount));
        assertFalse(catalog.isMountCurrent(
                firstLayout.revision(),
                new TrinityPatternCatalog.CoreMount(smallMount.position(), 128, small)));

        assertTrue(small.trySetPattern(0, new ItemStack(Items.PAPER)));
        catalog.onCoreChanged(
                small,
                new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.CATALOG));
        assertTrue(catalog.refreshChangedPatterns());
        assertEquals(firstLayout.revision(), catalog.layoutSnapshot().revision());
        assertTrue(catalog.isMountCurrent(firstLayout.revision(), smallMount));
        TrinityPatternCatalog.RebuildResult sameLayout = catalog.rebuild(List.of(
                extendedMount,
                overlimitMount,
                smallMount));
        assertFalse(sameLayout.changed());
        assertEquals(firstLayout.revision(), catalog.layoutSnapshot().revision());

        TrinityPatternCatalog.CoreMount movedSmallMount = new TrinityPatternCatalog.CoreMount(
                new BlockPos(4, 0, 0), 64, small);
        catalog.rebuild(List.of(extendedMount, overlimitMount, movedSmallMount));
        TrinityPatternCatalog.LayoutSnapshot movedLayout = catalog.layoutSnapshot();
        assertEquals(firstLayout.revision() + 1L, movedLayout.revision());
        assertNull(catalog.resolveGlobalSlot(firstLayout.revision(), 0));
        assertResolvedSlot(catalog, movedLayout.revision(), 0, extended, 0);
        assertFalse(catalog.isMountCurrent(movedLayout.revision(), smallMount));
        assertFalse(catalog.isMountCurrent(firstLayout.revision(), movedSmallMount));
        assertTrue(catalog.isMountCurrent(movedLayout.revision(), movedSmallMount));

        TrinityPatternCoreImpl replacement = core(64, small.coreId());
        replacement.trySetPattern(0, new ItemStack(Items.PAPER));
        TrinityPatternCatalog.CoreMount replacementMount = new TrinityPatternCatalog.CoreMount(
                movedSmallMount.position(), 64, replacement);
        catalog.rebuild(List.of(extendedMount, overlimitMount, replacementMount));
        TrinityPatternCatalog.LayoutSnapshot replacedLayout = catalog.layoutSnapshot();
        assertEquals(movedLayout.revision() + 1L, replacedLayout.revision());
        assertNull(catalog.resolveGlobalSlot(movedLayout.revision(), movedLayout.slotCount() - 1));
        assertResolvedSlot(catalog, replacedLayout.revision(), replacedLayout.slotCount() - 1, replacement, 63);
        assertFalse(catalog.isMountCurrent(replacedLayout.revision(), movedSmallMount));
        assertTrue(catalog.isMountCurrent(replacedLayout.revision(), replacementMount));
        IPatternDetails beforeInvalidation = catalog.getAvailablePatterns().getFirst();

        PatternRoute retainedRoute = new PatternRoute(hostId, replacement.coreId(), 0);
        replacement.appendPendingOutputs(
                retainedRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND))));
        catalog.onCoreChanged(
                replacement,
                new TrinityPatternSlot.Change(0, TrinityPatternSlot.ChangeKind.WORK));
        catalog.invalidateLayout();
        TrinityPatternCatalog.LayoutSnapshot invalidLayout = catalog.layoutSnapshot();
        assertEquals(replacedLayout.revision() + 1L, invalidLayout.revision());
        assertFalse(invalidLayout.active());
        assertEquals(0, invalidLayout.slotCount());
        assertTrue(invalidLayout.mounts().isEmpty());
        assertTrue(catalog.mountedCores().isEmpty());
        assertNull(catalog.resolveGlobalSlot(invalidLayout.revision(), 0));
        assertFalse(catalog.isMountCurrent(invalidLayout.revision(), replacementMount));
        assertTrue(catalog.hasWork());

        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter[] invalidatedInputs = counters(iron, 1L);
        assertFalse(catalog.pushPattern(beforeInvalidation, invalidatedInputs, 30L));
        assertEquals(1L, invalidatedInputs[0].get(iron));

        catalog.rebuild(List.of(replacementMount));
        TrinityPatternCatalog.LayoutSnapshot restoredLayout = catalog.layoutSnapshot();
        assertEquals(invalidLayout.revision() + 1L, restoredLayout.revision());
        assertTrue(restoredLayout.active());
        IPatternDetails restoredPattern = catalog.getAvailablePatterns().getFirst();
        assertNotSame(beforeInvalidation, restoredPattern);

        catalog.clear();
        TrinityPatternCatalog.LayoutSnapshot clearedLayout = catalog.layoutSnapshot();
        assertFalse(clearedLayout.active());
        assertEquals(restoredLayout.revision() + 1L, clearedLayout.revision());
        assertTrue(catalog.mountedCores().isEmpty());
        assertTrue(catalog.getAvailablePatterns().isEmpty());
        assertNull(catalog.resolveGlobalSlot(clearedLayout.revision(), 0));
        assertFalse(catalog.hasWork());

        catalog.rebuild(List.of(replacementMount));
        assertEquals(clearedLayout.revision() + 1L, catalog.layoutSnapshot().revision());
        assertNotSame(restoredPattern, catalog.getAvailablePatterns().getFirst());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_push_is_atomic_and_exact")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void pushPatternConsumesInputsOnlyAfterExactRouteWasQueued(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl core = core(UUID.randomUUID());
        core.trySetPattern(7, new ItemStack(Items.PAPER));
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core)));
        RoutedCraftingPatternDetails published = catalog.getAvailablePatterns().stream()
                .map(RoutedCraftingPatternDetails.class::cast)
                .filter(details -> details.route().slot() == 7)
                .findFirst()
                .orElseThrow();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        KeyCounter[] leftoverInputs = counters(iron, 2L);
        assertFalse(catalog.pushPattern(published, leftoverInputs, 10L));
        assertEquals(2L, leftoverInputs[0].get(iron));
        assertEquals(0, core.queuedBatchCount(7));

        RoutedCraftingPatternDetails wrongHost = new RoutedCraftingPatternDetails(
                new PatternRoute(UUID.randomUUID(), core.coreId(), 7),
                published.delegate());
        KeyCounter[] wrongHostInputs = counters(iron, 1L);
        assertFalse(catalog.pushPattern(wrongHost, wrongHostInputs, 10L));
        assertEquals(1L, wrongHostInputs[0].get(iron));

        core.refreshPatternCache(7);
        KeyCounter[] reboundInputs = counters(iron, 1L);
        assertTrue(catalog.pushPattern(published, reboundInputs, 10L));
        assertTrue(reboundInputs[0].isEmpty());
        assertEquals(1, core.queuedBatchCount(7));
        assertFalse(catalog.refreshChangedPatterns());
        RoutedCraftingPatternDetails unchanged = catalog.getAvailablePatterns().stream()
                .map(RoutedCraftingPatternDetails.class::cast)
                .filter(details -> details.route().slot() == 7)
                .findFirst()
                .orElseThrow();
        assertSame(published, unchanged);

        core.trySetPattern(7, new ItemStack(Items.MAP));
        KeyCounter[] changedDefinitionInputs = counters(iron, 1L);
        assertFalse(catalog.pushPattern(unchanged, changedDefinitionInputs, 10L));
        assertEquals(1L, changedDefinitionInputs[0].get(iron));
        assertEquals(1, core.queuedBatchCount(7));
        TrinityCraftingBatch batch = core.queuedBatches(7).getFirst();
        assertEquals(unchanged.route(), batch.route());
        assertTrue(batch.inputs().getFirst().is(Items.IRON_INGOT));
        assertEquals(9, batch.inputs().size());

        TrinityPatternCatalog.RebuildResult invalid = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 128, core)));
        assertFalse(invalid.valid());
        assertTrue(catalog.hasWork());
        assertTrue(catalog.getAvailablePatterns().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_prepare_rejection_is_atomic")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateLeavesEveryCoreUntouchedWhenDeliveryPreparationRejects(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl first = core(UUID.randomUUID());
        TrinityPatternCoreImpl second = core(UUID.randomUUID());
        ItemStack firstPattern = new ItemStack(Items.PAPER);
        ItemStack secondPattern = new ItemStack(Items.MAP);
        PatternRoute firstRoute = new PatternRoute(hostId, first.coreId(), 0);
        first.trySetPattern(0, firstPattern);
        second.trySetPattern(0, secondPattern);
        first.enqueueBatch(firstRoute, firstPattern, craftingInputs(new ItemStack(Items.IRON_INGOT, 2)), 1L);
        first.appendPendingOutputs(
                firstRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 3))));
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)));

        assertTrue(catalog.hasRefundableState());
        RecordingRefundDelivery rejected = new RecordingRefundDelivery(false, false);
        assertFalse(catalog.tryRefundAll(rejected));

        assertEquals(1, rejected.prepareCalls);
        assertEquals(0, rejected.deliveryCalls);
        assertTrue(first.pattern(0).is(Items.PAPER));
        assertTrue(second.pattern(0).is(Items.MAP));
        assertEquals(1, first.queuedBatchCount(0));
        assertEquals(3L, first.pendingOutputs(firstRoute).getFirst().amount());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_ignores_installed_patterns")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateDoesNothingWhenOnlyPatternsAreInstalled(GameTestHelper helper) {
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(UUID.randomUUID());
        TrinityPatternCoreImpl core = core(UUID.randomUUID());
        core.trySetPattern(0, new ItemStack(Items.PAPER));
        long patternRevision = core.revision();
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core)));

        RecordingRefundDelivery delivery = new RecordingRefundDelivery(true, false);
        assertFalse(catalog.hasRefundableState());
        assertFalse(catalog.tryRefundAll(delivery));

        assertEquals(0, delivery.prepareCalls);
        assertEquals(0, delivery.deliveryCalls);
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertEquals(patternRevision, core.revision());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_keeps_foreign_host_work")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateDoesNotClearAnotherHostRoutes(GameTestHelper helper) {
        UUID firstHostId = UUID.randomUUID();
        UUID secondHostId = UUID.randomUUID();
        TrinityPatternCatalogImpl firstCatalog = new TrinityPatternCatalogImpl(firstHostId);
        TrinityPatternCatalogImpl secondCatalog = new TrinityPatternCatalogImpl(secondHostId);
        TrinityPatternCoreImpl core = core(UUID.randomUUID());
        ItemStack pattern = new ItemStack(Items.PAPER);
        PatternRoute firstRoute = new PatternRoute(firstHostId, core.coreId(), 0);
        PatternRoute secondRoute = new PatternRoute(secondHostId, core.coreId(), 0);
        core.trySetPattern(0, pattern);
        core.enqueueBatch(firstRoute, pattern, craftingInputs(new ItemStack(Items.IRON_INGOT, 2)), 1L);
        core.appendPendingOutputs(
                firstRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 3))));
        core.enqueueBatch(secondRoute, pattern, craftingInputs(new ItemStack(Items.GOLD_INGOT, 4)), 1L);
        core.appendPendingOutputs(
                secondRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.EMERALD, 5))));
        TrinityPatternCatalog.CoreMount mount = new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core);
        firstCatalog.rebuild(List.of(mount));
        secondCatalog.rebuild(List.of(mount));

        RecordingRefundDelivery delivery = new RecordingRefundDelivery(true, false);
        assertTrue(firstCatalog.hasRefundableState());
        assertTrue(secondCatalog.hasRefundableState());
        assertTrue(firstCatalog.tryRefundAll(delivery));

        assertEquals(2, delivery.deliveredItems.size());
        assertTrue(delivery.deliveredItems.stream().anyMatch(
                item -> item.key().is(Items.IRON_INGOT) && item.amount() == 2L));
        assertTrue(delivery.deliveredItems.stream().anyMatch(
                item -> item.key().is(Items.DIAMOND) && item.amount() == 3L));
        assertEquals(1, core.queuedBatchCount(0));
        assertEquals(1, core.pendingOutputs(secondRoute).size());
        assertTrue(core.queuedBatches(0).getFirst().route().hostId().equals(secondHostId));
        assertTrue(core.pendingOutputs(secondRoute).getFirst().key().is(Items.EMERALD));
        assertFalse(firstCatalog.hasRefundableState());
        assertTrue(secondCatalog.hasRefundableState());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_aggregate_delivers_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateDeliversOnlyQueuedStateWithoutMutatingPatterns(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl first = core(UUID.randomUUID());
        TrinityPatternCoreImpl second = core(UUID.randomUUID());
        ItemStack firstPattern = new ItemStack(Items.PAPER);
        ItemStack secondPattern = new ItemStack(Items.MAP);
        PatternRoute firstRoute = new PatternRoute(hostId, first.coreId(), 0);
        PatternRoute secondRoute = new PatternRoute(hostId, second.coreId(), 0);
        first.trySetPattern(0, firstPattern);
        second.trySetPattern(0, secondPattern);
        first.enqueueBatch(firstRoute, firstPattern, craftingInputs(new ItemStack(Items.IRON_INGOT, 2)), 1L);
        first.appendPendingOutputs(
                firstRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 3))));
        second.enqueueBatch(secondRoute, secondPattern, craftingInputs(new ItemStack(Items.GOLD_INGOT, 4)), 1L);
        long firstRevision = first.revision();
        long secondRevision = second.revision();
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)));

        RecordingRefundDelivery delivery = new RecordingRefundDelivery(true, false);
        assertTrue(catalog.tryRefundAll(delivery));

        assertEquals(1, delivery.prepareCalls);
        assertEquals(1, delivery.deliveryCalls);
        assertEquals(3, delivery.deliveredItems.size());
        assertTrue(delivery.deliveredItems.stream().anyMatch(
                item -> item.key().is(Items.IRON_INGOT) && item.amount() == 2L));
        assertTrue(delivery.deliveredItems.stream().anyMatch(
                item -> item.key().is(Items.DIAMOND) && item.amount() == 3L));
        assertTrue(delivery.deliveredItems.stream().anyMatch(
                item -> item.key().is(Items.GOLD_INGOT) && item.amount() == 4L));
        assertEquals(0L, delivery.deliveredItems.stream().filter(item -> item.key().is(Items.PAPER)).count());
        assertEquals(0L, delivery.deliveredItems.stream().filter(item -> item.key().is(Items.MAP)).count());
        assertTrue(first.pattern(0).is(Items.PAPER));
        assertTrue(second.pattern(0).is(Items.MAP));
        assertFalse(first.hasWork());
        assertFalse(second.hasWork());
        assertEquals(firstRevision, first.revision());
        assertEquals(secondRevision, second.revision());
        assertFalse(catalog.hasRefundableState());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_prepare_exception_rolls_back")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateRestoresEveryCoreWhenDeliveryPreparationThrows(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl first = core(UUID.randomUUID());
        TrinityPatternCoreImpl second = core(UUID.randomUUID());
        ItemStack firstPattern = new ItemStack(Items.PAPER);
        ItemStack secondPattern = new ItemStack(Items.MAP);
        PatternRoute firstRoute = new PatternRoute(hostId, first.coreId(), 0);
        PatternRoute secondRoute = new PatternRoute(hostId, second.coreId(), 0);
        first.trySetPattern(0, firstPattern);
        second.trySetPattern(0, secondPattern);
        first.enqueueBatch(firstRoute, firstPattern, craftingInputs(new ItemStack(Items.IRON_INGOT)), 1L);
        second.appendPendingOutputs(
                secondRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND))));
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)));

        RecordingRefundDelivery throwing = new RecordingRefundDelivery(true, true);
        assertFalse(catalog.tryRefundAll(throwing));

        assertEquals(1, throwing.prepareCalls);
        assertEquals(0, throwing.deliveryCalls);
        assertTrue(first.pattern(0).is(Items.PAPER));
        assertTrue(second.pattern(0).is(Items.MAP));
        assertEquals(1, first.queuedBatchCount(0));
        assertEquals(1, second.pendingOutputs(secondRoute).size());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_core_failure_does_not_deliver")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateDoesNotDeliverAfterLaterCoreCommitFails(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl first = core(UUID.randomUUID());
        TopologyCapacityCore failingSecond = new TopologyCapacityCore(64, true);
        ItemStack firstPattern = new ItemStack(Items.PAPER);
        first.trySetPattern(0, firstPattern);
        failingSecond.trySetPattern(0, new ItemStack(Items.MAP));
        PatternRoute firstRoute = new PatternRoute(hostId, first.coreId(), 0);
        PatternRoute secondRoute = new PatternRoute(hostId, failingSecond.coreId(), 0);
        first.enqueueBatch(firstRoute, firstPattern, craftingInputs(new ItemStack(Items.IRON_INGOT)), 1L);
        failingSecond.appendPendingOutputs(
                secondRoute, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND))));
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, failingSecond)));

        RecordingRefundDelivery delivery = new RecordingRefundDelivery(true, false);
        assertFalse(catalog.tryRefundAll(delivery));

        assertEquals(1, delivery.prepareCalls);
        assertEquals(0, delivery.deliveryCalls);
        assertTrue(first.pattern(0).is(Items.PAPER));
        assertTrue(failingSecond.pattern(0).is(Items.MAP));
        assertEquals(1, first.queuedBatchCount(0));
        assertEquals(1, failingSecond.pendingOutputs(secondRoute).size());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_catalog_refund_delivery_error_reports_committed_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundAggregateReportsCompletionAfterDeliveryThrows(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(hostId);
        TrinityPatternCoreImpl core = core(UUID.randomUUID());
        ItemStack pattern = new ItemStack(Items.PAPER);
        PatternRoute route = new PatternRoute(hostId, core.coreId(), 0);
        core.trySetPattern(0, pattern);
        core.enqueueBatch(route, pattern, craftingInputs(new ItemStack(Items.IRON_INGOT)), 1L);
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, core)));

        RecordingRefundDelivery throwing = new RecordingRefundDelivery(true, false, true);
        assertTrue(catalog.tryRefundAll(throwing));

        assertEquals(1, throwing.prepareCalls);
        assertEquals(1, throwing.deliveryCalls);
        assertTrue(core.pattern(0).is(Items.PAPER));
        assertFalse(core.hasWork());
        assertFalse(catalog.hasRefundableState());
        core.appendPendingOutputs(route, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND))));
        assertEquals(1, core.pendingOutputs(route).size());
        helper.succeed();
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new GameTestAssertException("Expected condition to be false");
        }
    }

    private static void assertNull(Object value) {
        if (value != null) {
            throw new GameTestAssertException("Expected null but got " + value);
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected the same object instance");
        }
    }

    private static void assertNotSame(Object unexpected, Object actual) {
        if (unexpected == actual) {
            throw new GameTestAssertException("Expected different object instances");
        }
    }

    private static void assertArithmeticOverflow(ThrowingAction action) {
        try {
            action.run();
        } catch (ArithmeticException expected) {
            return;
        }
        throw new GameTestAssertException("Expected total Trinity pattern capacity to overflow");
    }

    private static void assertUnmodifiable(List<?> values) {
        try {
            values.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new GameTestAssertException("Expected an immutable list snapshot");
    }

    private static TrinityPatternCoreImpl core(UUID coreId) {
        return core(64, coreId);
    }

    private static TrinityPatternCoreImpl core(int capacity, UUID coreId) {
        return new TrinityPatternCoreImpl(capacity, coreId, stack -> {
            if (stack.is(Items.PAPER) || stack.is(Items.MAP)) {
                return new FillingPattern(stack);
            }
            return null;
        }, TrinityPatternTestResolvers.create(), change -> {});
    }

    private static void assertResolvedSlot(TrinityPatternCatalog catalog,
                                           long revision,
                                           int globalIndex,
                                           TrinityPatternCore expectedCore,
                                           int expectedCoreSlot) {
        TrinityPatternCatalog.GlobalSlot resolved = catalog.resolveGlobalSlot(revision, globalIndex);
        if (resolved == null) {
            throw new GameTestAssertException("Expected global slot " + globalIndex + " to resolve");
        }
        assertSame(expectedCore, resolved.core());
        assertEquals(globalIndex, resolved.globalIndex());
        assertEquals(expectedCoreSlot, resolved.coreSlot());
    }

    private static KeyCounter[] counters(AEItemKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return new KeyCounter[] { counter };
    }

    private static List<ItemStack> craftingInputs(ItemStack first) {
        ArrayList<ItemStack> inputs = new ArrayList<>(TrinityCraftingBatch.INPUT_SLOT_COUNT);
        inputs.add(first.copy());
        for (int slot = 1; slot < TrinityCraftingBatch.INPUT_SLOT_COUNT; slot++) {
            inputs.add(ItemStack.EMPTY);
        }
        return inputs;
    }

    @FunctionalInterface
    private interface ThrowingAction {

        void run();
    }

    /**
     * Captures catalog delivery calls while keeping aggregate atomicity tests independent from world destinations.
     */
    private static final class RecordingRefundDelivery implements TrinityRefundDelivery {

        private final boolean prepareResult;
        private final boolean throwDuringPrepare;
        private final boolean throwDuringDelivery;
        private int prepareCalls;
        private int deliveryCalls;
        private List<TrinityItemAmount> deliveredItems = List.of();

        private RecordingRefundDelivery(boolean prepareResult, boolean throwDuringPrepare) {
            this(prepareResult, throwDuringPrepare, false);
        }

        private RecordingRefundDelivery(boolean prepareResult,
                                        boolean throwDuringPrepare,
                                        boolean throwDuringDelivery) {
            this.prepareResult = prepareResult;
            this.throwDuringPrepare = throwDuringPrepare;
            this.throwDuringDelivery = throwDuringDelivery;
        }

        @Override
        public boolean prepare(List<TrinityItemAmount> items) {
            this.prepareCalls++;
            if (this.throwDuringPrepare) {
                throw new IllegalStateException("delivery preparation failure");
            }
            return this.prepareResult;
        }

        @Override
        public void deliver(List<TrinityItemAmount> items) {
            this.deliveryCalls++;
            this.deliveredItems = List.copyOf(items);
            if (this.throwDuringDelivery) {
                throw new IllegalStateException("delivery failure after committed refund");
            }
        }
    }

    /**
     * Delegates state operations while exposing an extreme capacity solely to exercise topology overflow.
     */
    private static final class TopologyCapacityCore implements TrinityPatternCore {

        private final int topologyCapacity;
        private final TrinityPatternCore delegate;
        private final boolean rejectRefundCommit;
        private final Runnable cacheSynchronizer;
        private int patternSlotCalls;
        private int patternCalls;
        private int patternCacheSnapshotCalls;
        private int cachedPatternCalls;
        private int cachedEnqueueCalls;
        private int workingSlotsCalls;
        private int isSlotWorkingCalls;

        private TopologyCapacityCore(int topologyCapacity) {
            this(topologyCapacity, false);
        }

        private TopologyCapacityCore(int topologyCapacity, boolean rejectRefundCommit) {
            this(topologyCapacity, core(UUID.randomUUID()), rejectRefundCommit, () -> {});
        }

        private TopologyCapacityCore(TrinityPatternCore delegate, Runnable cacheSynchronizer) {
            this(delegate.patternCapacity(), delegate, false, cacheSynchronizer);
        }

        private TopologyCapacityCore(int topologyCapacity,
                                     TrinityPatternCore delegate,
                                     boolean rejectRefundCommit,
                                     Runnable cacheSynchronizer) {
            this.topologyCapacity = topologyCapacity;
            this.delegate = delegate;
            this.rejectRefundCommit = rejectRefundCommit;
            this.cacheSynchronizer = cacheSynchronizer;
        }

        @Override
        public UUID coreId() {
            return this.delegate.coreId();
        }

        @Override
        public int patternCapacity() {
            return this.topologyCapacity;
        }

        @Override
        public TrinityPatternSlot patternSlot(int slot) {
            this.patternSlotCalls++;
            return this.delegate.patternSlot(slot);
        }

        @Override
        public long revision() {
            return this.delegate.revision();
        }

        @Override
        public PatternCacheSnapshot patternCacheSnapshot() {
            this.patternCacheSnapshotCalls++;
            return this.delegate.patternCacheSnapshot();
        }

        @Override
        public CachedPattern cachedPattern(int slot) {
            this.cachedPatternCalls++;
            return this.delegate.cachedPattern(slot);
        }

        @Override
        public List<Integer> occupiedPatternSlots() {
            return this.delegate.occupiedPatternSlots();
        }

        @Override
        public InternalInventory patternInventory() {
            return this.delegate.patternInventory();
        }

        @Override
        public ItemStack pattern(int slot) {
            this.patternCalls++;
            return this.delegate.pattern(slot);
        }

        @Override
        public boolean trySetPattern(int slot, ItemStack pattern) {
            return this.delegate.trySetPattern(slot, pattern);
        }

        @Override
        public IMolecularAssemblerSupportedPattern decodedPattern(int slot) {
            return this.delegate.decodedPattern(slot);
        }

        @Override
        public void refreshPatternCache(int slot) {
            this.delegate.refreshPatternCache(slot);
        }

        @Override
        public void refreshAllPatternCaches() {
            this.delegate.refreshAllPatternCaches();
        }

        @Override
        public void ensurePatternCachesCurrent() {
            this.cacheSynchronizer.run();
            this.delegate.ensurePatternCachesCurrent();
        }

        @Override
        public boolean enqueueBatch(PatternRoute route,
                                    ItemStack patternSnapshot,
                                    List<ItemStack> inputs,
                                    long queuedTick) {
            return this.delegate.enqueueBatch(route, patternSnapshot, inputs, queuedTick);
        }

        @Override
        public boolean enqueueBatch(PatternRoute route,
                                    CachedPattern expectedPattern,
                                    long expectedRuntimeBindingRevision,
                                    TrinityCraftingBatch.InputSignature inputs,
                                    long queuedTick,
                                    long count) {
            this.cachedEnqueueCalls++;
            return this.delegate.enqueueBatch(
                    route,
                    expectedPattern,
                    expectedRuntimeBindingRevision,
                    inputs,
                    queuedTick,
                    count);
        }

        @Override
        public List<TrinityCraftingBatch> queuedBatches(int slot) {
            return this.delegate.queuedBatches(slot);
        }

        @Override
        public int queuedBatchCount(int slot) {
            return this.delegate.queuedBatchCount(slot);
        }

        @Override
        public int queuedBatchCount() {
            return this.delegate.queuedBatchCount();
        }

        @Override
        public int executeReadyBatches(long currentTick, BatchExecutor executor) {
            return this.delegate.executeReadyBatches(currentTick, executor);
        }

        @Override
        public int executeReadyBatches(int slot, long currentTick, BatchExecutor executor) {
            return this.delegate.executeReadyBatches(slot, currentTick, executor);
        }

        @Override
        public List<TrinityItemAmount> pendingOutputs(PatternRoute route) {
            return this.delegate.pendingOutputs(route);
        }

        @Override
        public void appendPendingOutputs(PatternRoute route, List<TrinityItemAmount> outputs) {
            this.delegate.appendPendingOutputs(route, outputs);
        }

        @Override
        public TrinityPatternOutputRouter.PendingOutputCursor openPendingOutputCursor(PatternRoute route) {
            return this.delegate.openPendingOutputCursor(route);
        }

        @Override
        public List<Integer> pendingOutputSlots(UUID hostId) {
            return this.delegate.pendingOutputSlots(hostId);
        }

        @Override
        public List<Integer> workingSlots(UUID hostId) {
            this.workingSlotsCalls++;
            return this.delegate.workingSlots(hostId);
        }

        @Override
        public boolean isSlotWorking(UUID hostId, int slot) {
            this.isSlotWorkingCalls++;
            return this.delegate.isSlotWorking(hostId, slot);
        }

        @Override
        public boolean hasWork() {
            return this.delegate.hasWork();
        }

        @Override
        public boolean hasWork(UUID hostId) {
            return this.delegate.hasWork(hostId);
        }

        @Override
        public RefundTransaction prepareRefund() {
            return wrapRefundTransaction(this.delegate.prepareRefund());
        }

        @Override
        public RefundTransaction prepareRefund(UUID hostId) {
            return wrapRefundTransaction(this.delegate.prepareRefund(hostId));
        }

        private RefundTransaction wrapRefundTransaction(RefundTransaction transaction) {
            if (!this.rejectRefundCommit) {
                return transaction;
            }
            return new RefundTransaction() {

                @Override
                public List<TrinityItemAmount> refundableItems() {
                    return transaction.refundableItems();
                }

                @Override
                public boolean commit() {
                    return false;
                }

                @Override
                public void complete() {
                    transaction.complete();
                }

                @Override
                public void rollback() {
                    transaction.rollback();
                }
            };
        }

        @Override
        public boolean tryRefundAll(TrinityRefundDelivery delivery) {
            return this.delegate.tryRefundAll(delivery);
        }

        @Override
        public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
            this.delegate.writeToTag(data, registries);
        }

        @Override
        public void hydrateFromTag(CompoundTag data, HolderLookup.Provider registries) {
            this.delegate.hydrateFromTag(data, registries);
        }

        @Override
        public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
            this.delegate.readFromTag(data, registries);
        }
    }

    private static final class FillingPattern implements IMolecularAssemblerSupportedPattern {

        private final AEItemKey definition;
        private final ItemStack output;

        private FillingPattern(ItemStack definition) {
            this(definition, new ItemStack(Items.DIAMOND));
        }

        private FillingPattern(ItemStack definition, ItemStack output) {
            this.definition = AEItemKey.of(definition);
            this.output = output.copy();
        }

        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return this.output.copy();
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            return NonNullList.withSize(input.size(), ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, AEItemKey key, Level level) {
            return slot == 0 && key.is(Items.IRON_INGOT);
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return slot == 0;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {
            AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
            if (table.length == 0 || table[0].get(iron) <= 0L) {
                return;
            }
            table[0].remove(iron, 1L);
            gridAccessor.set(0, iron.toStack());
        }

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
            return List.of(new GenericStack(AEItemKey.of(this.output), this.output.getCount()));
        }
    }
}
