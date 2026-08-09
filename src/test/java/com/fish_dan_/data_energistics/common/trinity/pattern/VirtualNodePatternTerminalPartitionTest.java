package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class VirtualNodePatternTerminalPartitionTest {

    private VirtualNodePatternTerminalPartitionTest() {}

    @TestHolder("trinity_pattern_terminal_partitions_supported_core_tiers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void partitionsSupportedCoreTiersIntoStableBoundedLiveViews(GameTestHelper helper) {
        UUID hostId = UUID.fromString("b41b5a61-7fa2-40c9-b56d-66c822e8e11d");
        PersistentTrinityPatternCore small = core(64, UUID.randomUUID());
        PersistentTrinityPatternCore extended = core(128, UUID.randomUUID());
        PersistentTrinityPatternCore overlimit = core(512, UUID.randomUUID());
        PatternContainerGroup group = terminalGroup();
        MountedCorePatternCatalog catalog = formedCatalog(hostId, List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(3, 0, 0), 512, overlimit),
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), 64, small),
                new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 128, extended)));

        List<TrinityPatternTerminalPartition> partitions = TrinityPatternTerminalPartition.createLayout(
                catalog,
                group);

        assertEquals(6, partitions.size());
        assertEquals(List.of(64, 128, 128, 128, 128, 128), partitions.stream()
                .map(TrinityPatternTerminalPartition::slotCount)
                .toList());
        assertEquals(List.of(0, 0, 0, 128, 256, 384), partitions.stream()
                .map(TrinityPatternTerminalPartition::firstCoreSlot)
                .toList());
        assertEquals(List.of(small.coreId(), extended.coreId(), overlimit.coreId(), overlimit.coreId(),
                overlimit.coreId(), overlimit.coreId()),
                partitions.stream()
                        .map(partition -> partition.key().coreId())
                        .toList());
        assertEquals(List.of(0, 0, 0, 1, 2, 3), partitions.stream()
                .map(partition -> partition.key().partitionIndex())
                .toList());
        assertTrue(partitions.stream().allMatch(partition -> partition.key().hostId().equals(hostId)));
        assertTrue(partitions.stream().allMatch(
                partition -> partition.slotCount() <= TrinityPatternTerminalPartition.MAX_PATTERN_SLOTS));
        assertTrue(partitions.stream().allMatch(partition -> partition.getTerminalPatternInventory().size() <=
                TrinityPatternTerminalPartition.MAX_PATTERN_SLOTS));
        assertTrue(partitions.stream().noneMatch(TrinityPatternTerminalPartition::isAttached));
        assertTrue(partitions.stream().allMatch(partition -> partition.getGrid() == null));
        for (int index = 1; index < partitions.size(); index++) {
            assertEquals(partitions.get(index - 1).getTerminalSortOrder() + 1L,
                    partitions.get(index).getTerminalSortOrder());
            assertSame(partitions.getFirst().getTerminalGroup(), partitions.get(index).getTerminalGroup());
        }

        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 128, extended),
                new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), 64, small),
                new TrinityPatternCatalog.CoreMount(new BlockPos(3, 0, 0), 512, overlimit)));
        List<TrinityPatternTerminalPartition> rebuilt = TrinityPatternTerminalPartition.createLayout(
                catalog,
                group);
        assertEquals(partitions.stream().map(TrinityPatternTerminalPartition::key).toList(),
                rebuilt.stream().map(TrinityPatternTerminalPartition::key).toList());
        assertEquals(partitions.stream().map(TrinityPatternTerminalPartition::getTerminalSortOrder).toList(),
                rebuilt.stream().map(TrinityPatternTerminalPartition::getTerminalSortOrder).toList());
        for (int index = 0; index < partitions.size(); index++) {
            assertTrue(partitions.get(index).hasSameLayout(rebuilt.get(index)));
        }
        helper.succeed();
    }

    @TestHolder("trinity_pattern_terminal_partition_maps_live_core_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsTerminalSlotsDirectlyOntoThePhysicalCoreInventory(GameTestHelper helper) {
        PersistentTrinityPatternCore core = core(512, UUID.randomUUID());
        MountedCorePatternCatalog catalog = formedCatalog(
                UUID.randomUUID(),
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 512, core)));
        List<TrinityPatternTerminalPartition> partitions = TrinityPatternTerminalPartition.createLayout(
                catalog,
                terminalGroup());
        TrinityPatternTerminalPartition second = partitions.get(1);
        InternalInventory inventory = second.getTerminalPatternInventory();

        assertEquals(128, inventory.size());
        assertTrue(inventory.insertItem(0, new ItemStack(Items.PAPER), false).isEmpty());
        assertTrue(core.pattern(128).is(Items.PAPER));

        inventory.setItemDirect(127, new ItemStack(Items.MAP));
        assertTrue(core.pattern(255).is(Items.MAP));

        ItemStack rejected = inventory.insertItem(1, new ItemStack(Items.DIAMOND), false);
        assertTrue(rejected.is(Items.DIAMOND));
        assertTrue(core.pattern(129).isEmpty());

        assertTrue(core.trySetPattern(130, new ItemStack(Items.MAP)));
        assertTrue(inventory.getStackInSlot(2).is(Items.MAP));

        ItemStack extracted = inventory.extractItem(0, 1, false);
        assertTrue(extracted.is(Items.PAPER));
        assertTrue(core.pattern(128).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_terminal_partition_rejects_invalid_layouts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsLayoutsThatCouldPublishAmbiguousOrInvalidPartitions(GameTestHelper helper) {
        UUID duplicateCoreId = UUID.randomUUID();
        PersistentTrinityPatternCore first = core(64, duplicateCoreId);
        PersistentTrinityPatternCore second = core(64, duplicateCoreId);

        MountedCorePatternCatalog capacityCatalog = new MountedCorePatternCatalog(UUID.randomUUID());
        TrinityPatternCatalog.RebuildResult capacityFailure = capacityCatalog.rebuild(
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 128, first)));
        assertFalse(capacityFailure.valid());
        assertTrue(TrinityPatternTerminalPartition.createLayout(capacityCatalog, terminalGroup()).isEmpty());

        MountedCorePatternCatalog duplicateCatalog = new MountedCorePatternCatalog(UUID.randomUUID());
        TrinityPatternCatalog.RebuildResult duplicateFailure = duplicateCatalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)));
        assertFalse(duplicateFailure.valid());
        assertTrue(TrinityPatternTerminalPartition.createLayout(duplicateCatalog, terminalGroup()).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_terminal_partition_detects_stale_layout")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void layoutComparisonDetectsAReplacedCoreOrChangedCoordinateOrder(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        UUID coreId = UUID.randomUUID();
        PersistentTrinityPatternCore original = core(64, coreId);
        PersistentTrinityPatternCore replacement = core(64, coreId);
        MountedCorePatternCatalog catalog = formedCatalog(
                hostId,
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, original)));
        TrinityPatternTerminalPartition first = TrinityPatternTerminalPartition.createLayout(
                catalog,
                terminalGroup()).getFirst();
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, replacement)));
        TrinityPatternTerminalPartition replacedCore = TrinityPatternTerminalPartition.createLayout(
                catalog,
                terminalGroup()).getFirst();
        catalog.rebuild(List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, original)));
        TrinityPatternTerminalPartition movedCore = TrinityPatternTerminalPartition.createLayout(
                catalog,
                terminalGroup()).getFirst();

        assertFalse(first.hasSameLayout(replacedCore));
        assertFalse(first.hasSameLayout(movedCore));
        first.detach();
        assertFalse(first.isAttached());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_terminal_partition_stale_inventory_rejects_all_access")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void staleInventoryReferenceCannotReadOrMutateReformedLayout(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        UUID coreId = UUID.randomUUID();
        PersistentTrinityPatternCore original = core(128, coreId);
        PersistentTrinityPatternCore companion = core(64, UUID.randomUUID());
        TrinityPatternCatalog.CoreMount originalMount = new TrinityPatternCatalog.CoreMount(
                BlockPos.ZERO, 128, original);
        TrinityPatternCatalog.CoreMount companionMount = new TrinityPatternCatalog.CoreMount(
                BlockPos.ZERO.above(), 64, companion);
        MountedCorePatternCatalog catalog = formedCatalog(hostId, List.of(originalMount, companionMount));
        TrinityPatternTerminalPartition originalPartition = partitionForCore(catalog, coreId);
        InternalInventory staleInventory = originalPartition.getTerminalPatternInventory();
        InternalInventory staleSubInventory = staleInventory.getSubInventory(0, 2);
        IItemHandler staleItemHandler = staleInventory.toItemHandler();
        ItemStack paper = new ItemStack(Items.PAPER);

        assertTrue(staleInventory.insertItem(0, paper, false).isEmpty());
        assertTrue(original.pattern(0).is(Items.PAPER));
        long originalRevision = originalPartition.layoutRevision();
        assertTrue(original.trySetPattern(1, new ItemStack(Items.MAP)));
        catalog.onCoreChanged(
                original,
                new TrinityPatternSlot.Change(1, TrinityPatternSlot.ChangeKind.CATALOG));
        assertTrue(catalog.refreshChangedPatterns());
        assertEquals(originalRevision, catalog.layoutSnapshot().revision());
        assertTrue(staleInventory.getStackInSlot(1).is(Items.MAP));
        assertTrue(staleSubInventory.getStackInSlot(0).is(Items.PAPER));
        assertTrue(staleItemHandler.getStackInSlot(0).is(Items.PAPER));
        assertIllegalArgument(() -> staleInventory.getStackInSlot(staleInventory.size()));

        TrinityPatternCatalog.CoreMount movedOriginalMount = new TrinityPatternCatalog.CoreMount(
                new BlockPos(2, 0, 0), 128, original);
        TrinityPatternCatalog.CoreMount reorderedCompanionMount = new TrinityPatternCatalog.CoreMount(
                BlockPos.ZERO, 64, companion);
        catalog.rebuild(List.of(movedOriginalMount, reorderedCompanionMount));

        assertEquals(128, staleInventory.size());
        assertStaleInventory(staleInventory, paper);
        assertTrue(staleSubInventory.getStackInSlot(0).isEmpty());
        assertTrue(staleItemHandler.getStackInSlot(0).isEmpty());
        assertStackMatches(paper, staleItemHandler.insertItem(0, paper, false));
        assertTrue(staleItemHandler.extractItem(0, 1, false).isEmpty());
        staleInventory.setItemDirect(1, new ItemStack(Items.MAP));
        staleInventory.clear();
        assertTrue(original.pattern(0).is(Items.PAPER));
        assertTrue(original.pattern(1).is(Items.MAP));
        assertFalse(originalPartition.isVisibleInTerminal());

        TrinityPatternTerminalPartition movedPartition = partitionForCore(catalog, coreId);
        InternalInventory movedInventory = movedPartition.getTerminalPatternInventory();
        assertTrue(movedInventory.insertItem(2, paper, false).isEmpty());
        assertTrue(original.pattern(2).is(Items.PAPER));

        PersistentTrinityPatternCore replacement = core(128, coreId);
        TrinityPatternCatalog.CoreMount replacementMount = new TrinityPatternCatalog.CoreMount(
                movedOriginalMount.position(), 128, replacement);
        catalog.rebuild(List.of(replacementMount, reorderedCompanionMount));
        assertStaleInventory(movedInventory, paper);
        movedInventory.setItemDirect(0, paper);
        assertTrue(replacement.pattern(0).isEmpty());

        TrinityPatternTerminalPartition currentPartition = partitionForCore(catalog, coreId);
        InternalInventory currentInventory = currentPartition.getTerminalPatternInventory();
        assertTrue(currentInventory.insertItem(0, paper, false).isEmpty());
        assertTrue(replacement.pattern(0).is(Items.PAPER));
        assertFalse(originalPartition.hasSameLayout(currentPartition));

        catalog.invalidateLayout();
        assertStaleInventory(currentInventory, paper);
        catalog.rebuild(List.of(replacementMount, reorderedCompanionMount));
        assertStaleInventory(currentInventory, paper);
        TrinityPatternTerminalPartition rebuiltPartition = partitionForCore(catalog, coreId);
        assertFalse(currentPartition.hasSameLayout(rebuiltPartition));
        assertTrue(rebuiltPartition.getTerminalPatternInventory()
                .insertItem(1, new ItemStack(Items.MAP), false)
                .isEmpty());
        assertTrue(replacement.pattern(1).is(Items.MAP));
        helper.succeed();
    }

    private static PersistentTrinityPatternCore core(int capacity, UUID coreId) {
        return new PersistentTrinityPatternCore(capacity, coreId, stack -> {
            if (stack.is(Items.PAPER) || stack.is(Items.MAP)) {
                return new TestPattern(stack);
            }
            return null;
        }, TrinityPatternTestResolvers.create(), change -> {});
    }

    private static PatternContainerGroup terminalGroup() {
        return new PatternContainerGroup(
                AEItemKey.of(Items.CRAFTING_TABLE),
                Component.literal("Trinity Data Core"),
                List.of());
    }

    private static MountedCorePatternCatalog formedCatalog(UUID hostId,
                                                           List<TrinityPatternCatalog.CoreMount> mounts) {
        MountedCorePatternCatalog catalog = new MountedCorePatternCatalog(hostId);
        TrinityPatternCatalog.RebuildResult result = catalog.rebuild(mounts);
        if (!result.valid()) {
            throw new GameTestAssertException("Expected valid terminal catalog: " + result.failureReason());
        }
        return catalog;
    }

    private static TrinityPatternTerminalPartition partitionForCore(TrinityPatternCatalog catalog, UUID coreId) {
        return TrinityPatternTerminalPartition.createLayout(catalog, terminalGroup()).stream()
                .filter(partition -> partition.key().coreId().equals(coreId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertStaleInventory(InternalInventory inventory, ItemStack offered) {
        assertEquals(0, inventory.getSlotLimit(0));
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertFalse(inventory.isItemValid(0, offered));
        assertStackMatches(offered, inventory.insertItem(0, offered, false));
        assertTrue(inventory.extractItem(0, 1, false).isEmpty());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
    }

    private static void assertIllegalArgument(TestAction action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new GameTestAssertException("Expected an out-of-range Trinity terminal slot to fail");
    }

    private static void assertStackMatches(ItemStack expected, ItemStack actual) {
        assertTrue(ItemStack.isSameItemSameComponents(expected, actual));
        assertEquals(expected.getCount(), actual.getCount());
    }

    private static final class TestPattern implements IMolecularAssemblerSupportedPattern {

        private final AEItemKey definition;

        private TestPattern(ItemStack definition) {
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
            return slot == 0 && key.is(Items.IRON_INGOT);
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return slot == 0;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {
            AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
            if (table.length > 0 && table[0].get(iron) > 0L) {
                table[0].remove(iron, 1L);
                gridAccessor.set(0, iron.toStack());
            }
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
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }
    }

    @FunctionalInterface
    private interface TestAction {

        void run();
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

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
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
}
