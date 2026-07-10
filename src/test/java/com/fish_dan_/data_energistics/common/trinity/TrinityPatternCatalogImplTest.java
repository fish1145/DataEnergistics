package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    public static void refreshesOnlyTheCoreWhoseRevisionChanged(GameTestHelper helper) {
        TrinityPatternCatalogImpl catalog = new TrinityPatternCatalogImpl(UUID.randomUUID());
        TrinityPatternCoreImpl first = core(UUID.randomUUID());
        TrinityPatternCoreImpl second = core(UUID.randomUUID());
        first.trySetPattern(0, new ItemStack(Items.PAPER));
        second.trySetPattern(0, new ItemStack(Items.MAP));
        catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 64, first),
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO.above(), 64, second)));
        IPatternDetails firstBefore = catalog.getAvailablePatterns().get(0);
        IPatternDetails secondBefore = catalog.getAvailablePatterns().get(1);

        first.trySetPattern(1, new ItemStack(Items.MAP));

        assertTrue(catalog.refreshChangedPatterns());
        List<IPatternDetails> refreshed = catalog.getAvailablePatterns();
        assertNotSame(firstBefore, refreshed.get(0));
        assertSame(secondBefore, refreshed.get(2));
        assertFalse(catalog.refreshChangedPatterns());
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
        KeyCounter[] staleDelegateInputs = counters(iron, 1L);
        assertTrue(catalog.pushPattern(published, staleDelegateInputs, 10L));
        assertTrue(staleDelegateInputs[0].isEmpty());
        assertEquals(1, core.queuedBatchCount(7));

        core.trySetPattern(7, new ItemStack(Items.MAP));
        KeyCounter[] changedDefinitionInputs = counters(iron, 1L);
        assertFalse(catalog.pushPattern(published, changedDefinitionInputs, 10L));
        assertEquals(1L, changedDefinitionInputs[0].get(iron));
        assertEquals(1, core.queuedBatchCount(7));
        TrinityCraftingBatch batch = core.queuedBatches(7).getFirst();
        assertEquals(published.route(), batch.route());
        assertTrue(batch.inputs().getFirst().is(Items.IRON_INGOT));
        assertEquals(9, batch.inputs().size());

        TrinityPatternCatalog.RebuildResult invalid = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(BlockPos.ZERO, 128, core)));
        assertFalse(invalid.valid());
        assertTrue(catalog.hasWork());
        assertTrue(catalog.getAvailablePatterns().isEmpty());
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

    private static void assertUnmodifiable(List<?> values) {
        try {
            values.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new GameTestAssertException("Expected an immutable list snapshot");
    }

    private static TrinityPatternCoreImpl core(UUID coreId) {
        return new TrinityPatternCoreImpl(64, coreId, stack -> {
            if (stack.is(Items.PAPER) || stack.is(Items.MAP)) {
                return new FillingPattern(stack);
            }
            return null;
        }, () -> {});
    }

    private static KeyCounter[] counters(AEItemKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return new KeyCounter[] { counter };
    }

    private static final class FillingPattern implements IMolecularAssemblerSupportedPattern {

        private final AEItemKey definition;

        private FillingPattern(ItemStack definition) {
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
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }
    }
}
