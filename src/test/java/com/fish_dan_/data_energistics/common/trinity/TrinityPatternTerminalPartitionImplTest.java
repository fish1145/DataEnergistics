package com.fish_dan_.data_energistics.common.trinity;

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
public final class TrinityPatternTerminalPartitionImplTest {

    private TrinityPatternTerminalPartitionImplTest() {}

    @TestHolder("trinity_pattern_terminal_partitions_supported_core_tiers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void partitionsSupportedCoreTiersIntoStableBoundedLiveViews(GameTestHelper helper) {
        UUID hostId = UUID.fromString("b41b5a61-7fa2-40c9-b56d-66c822e8e11d");
        TrinityPatternCoreImpl small = core(64, UUID.randomUUID());
        TrinityPatternCoreImpl extended = core(128, UUID.randomUUID());
        TrinityPatternCoreImpl overlimit = core(512, UUID.randomUUID());
        PatternContainerGroup group = terminalGroup();

        List<TrinityPatternTerminalPartition> partitions = TrinityPatternTerminalPartition.createLayout(
                hostId,
                List.of(
                        new TrinityPatternCatalog.CoreMount(new BlockPos(3, 0, 0), 512, overlimit),
                        new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), 64, small),
                        new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 128, extended)),
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

        List<TrinityPatternTerminalPartition> rebuilt = TrinityPatternTerminalPartition.createLayout(
                hostId,
                List.of(
                        new TrinityPatternCatalog.CoreMount(new BlockPos(2, 0, 0), 128, extended),
                        new TrinityPatternCatalog.CoreMount(new BlockPos(1, 0, 0), 64, small),
                        new TrinityPatternCatalog.CoreMount(new BlockPos(3, 0, 0), 512, overlimit)),
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
        TrinityPatternCoreImpl core = core(512, UUID.randomUUID());
        List<TrinityPatternTerminalPartition> partitions = TrinityPatternTerminalPartition.createLayout(
                UUID.randomUUID(),
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 512, core)),
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
        TrinityPatternCoreImpl first = core(64, duplicateCoreId);
        TrinityPatternCoreImpl second = core(64, duplicateCoreId);

        assertThrows(IllegalArgumentException.class, () -> TrinityPatternTerminalPartition.createLayout(
                UUID.randomUUID(),
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 128, first)),
                terminalGroup()));
        assertThrows(IllegalArgumentException.class, () -> TrinityPatternTerminalPartition.createLayout(
                UUID.randomUUID(),
                List.of(
                        new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                        new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)),
                terminalGroup()));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_terminal_partition_detects_stale_layout")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void layoutComparisonDetectsAReplacedCoreOrChangedCoordinateOrder(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        UUID coreId = UUID.randomUUID();
        TrinityPatternCoreImpl original = core(64, coreId);
        TrinityPatternCoreImpl replacement = core(64, coreId);
        TrinityPatternTerminalPartition first = TrinityPatternTerminalPartition.createLayout(
                hostId,
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, original)),
                terminalGroup()).getFirst();
        TrinityPatternTerminalPartition replacedCore = TrinityPatternTerminalPartition.createLayout(
                hostId,
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, replacement)),
                terminalGroup()).getFirst();
        TrinityPatternTerminalPartition movedCore = TrinityPatternTerminalPartition.createLayout(
                hostId,
                List.of(new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, original)),
                terminalGroup()).getFirst();

        assertFalse(first.hasSameLayout(replacedCore));
        assertFalse(first.hasSameLayout(movedCore));
        first.detach();
        assertFalse(first.isAttached());
        helper.succeed();
    }

    private static TrinityPatternCoreImpl core(int capacity, UUID coreId) {
        return new TrinityPatternCoreImpl(capacity, coreId, stack -> {
            if (stack.is(Items.PAPER) || stack.is(Items.MAP)) {
                return new TestPattern(stack);
            }
            return null;
        }, () -> {});
    }

    private static PatternContainerGroup terminalGroup() {
        return new PatternContainerGroup(
                AEItemKey.of(Items.CRAFTING_TABLE),
                Component.literal("Trinity Data Core"),
                List.of());
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
}
