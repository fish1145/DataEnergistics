package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.events.GridEvent;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class PatternProviderSyncHelperTest {

    private PatternProviderSyncHelperTest() {}

    @TestHolder("pattern_provider_sync_merges_same_display_entry")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mergesSameDisplayEntryAndTargets(GameTestHelper helper) {
        TestPatternProvider first = provider("Assembler", Items.CRAFTING_TABLE, 3, 2, 20);
        TestPatternProvider second = provider("Assembler", Items.CRAFTING_TABLE, 2, 1, 10);
        Map<Long, List<PatternContainer>> targetsById = new HashMap<>();

        var result = collect(List.of(first, second), targetsById);

        assertEquals(1, result.providers().size());
        var merged = result.providers().getFirst();
        assertEquals(5, merged.patternSlotCount());
        assertEquals(3, merged.usedPatternSlotCount());
        assertEquals(List.of(second, first), targetsById.get(merged.id()));
        helper.succeed();
    }

    @TestHolder("pattern_provider_sync_keeps_different_display_entries_separate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsDifferentNamesAndIconsSeparate(GameTestHelper helper) {
        TestPatternProvider base = provider("Assembler", Items.CRAFTING_TABLE, 2, 0, 10);
        TestPatternProvider differentName = provider("Dedicated Line", Items.CRAFTING_TABLE, 2, 0, 20);
        TestPatternProvider differentIcon = provider("Assembler", Items.FURNACE, 2, 0, 30);

        var result = collect(List.of(base, differentName, differentIcon), new HashMap<>());

        assertEquals(3, result.providers().size());
        helper.succeed();
    }

    @TestHolder("pattern_provider_sync_custom_name_splits_group")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void customDisplayNameSplitsGroup(GameTestHelper helper) {
        TestPatternProvider base = provider("Assembler", Items.CRAFTING_TABLE, 2, 0, 10);
        TestPatternProvider renamed = provider("Assembler", Items.CRAFTING_TABLE, 2, 0, 20);
        renamed.customName = Component.literal("Dedicated Line");

        var result = collect(List.of(base, renamed), new HashMap<>());

        assertEquals(2, result.providers().size());
        helper.succeed();
    }

    @TestHolder("pattern_provider_sync_preserves_special_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesSpecialAggregationKeys(GameTestHelper helper) {
        TestPatternProvider matrixFirst = new TileAssemblerMatrixPattern(
                "First", Items.CRAFTING_TABLE, 1, 0, 10);
        TestPatternProvider matrixSecond = new TileAssemblerMatrixPattern(
                "Second", Items.FURNACE, 2, 1, 20);
        TestPatternProvider f4First = new NeoEcoCraftingProviderF4(
                "Crafting System", Items.CRAFTING_TABLE, 1, 0, 30);
        TestPatternProvider f4Second = new NeoEcoCraftingProviderF4(
                "Crafting System", Items.CRAFTING_TABLE, 2, 1, 40);
        TestPatternProvider f6 = new NeoEcoCraftingProviderF6(
                "Crafting System", Items.CRAFTING_TABLE, 3, 2, 50);

        var result = collect(
                List.of(matrixFirst, matrixSecond, f4First, f4Second, f6), new HashMap<>());

        assertEquals(3, result.providers().size());
        assertTrue(result.providers().stream().anyMatch(provider -> provider.patternSlotCount() == 3 &&
                provider.usedPatternSlotCount() == 1));
        assertTrue(result.providers().stream().anyMatch(provider -> provider.patternSlotCount() == 3 &&
                provider.usedPatternSlotCount() == 2));
        helper.succeed();
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProviderList collect(
                                                                                List<? extends PatternContainer> providers,
                                                                                Map<Long, List<PatternContainer>> targetsById) {
        AtomicLong nextId = new AtomicLong(1);
        return PatternProviderSyncHelper.collectSyncedPatternProviders(
                new TestGrid(providers),
                new IdentityHashMap<>(),
                targetsById,
                nextId::getAndIncrement,
                null,
                null,
                ItemStack.EMPTY);
    }

    private static TestPatternProvider provider(String name, Item icon, int slots, int usedSlots, long sortOrder) {
        return new TestPatternProvider(name, icon, slots, usedSlots, sortOrder);
    }

    private static class TestPatternProvider implements PatternContainer {

        private final String baseName;
        private final AEItemKey icon;
        private final AppEngInternalInventory inventory;
        private final long sortOrder;
        private Component customName;

        private TestPatternProvider(String baseName, Item icon, int slots, int usedSlots, long sortOrder) {
            this.baseName = baseName;
            this.icon = AEItemKey.of(icon);
            this.inventory = inventory(slots, usedSlots);
            this.sortOrder = sortOrder;
        }

        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return this.inventory;
        }

        @Override
        public long getTerminalSortOrder() {
            return this.sortOrder;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            Component displayName = this.customName == null ? Component.literal(this.baseName) : this.customName;
            return new PatternContainerGroup(this.icon, displayName, List.of());
        }
    }

    private static final class TileAssemblerMatrixPattern extends TestPatternProvider {

        private TileAssemblerMatrixPattern(String baseName, Item icon, int slots, int usedSlots, long sortOrder) {
            super(baseName, icon, slots, usedSlots, sortOrder);
        }
    }

    private static final class NeoEcoCraftingProviderF4 extends TestPatternProvider {

        private NeoEcoCraftingProviderF4(String baseName, Item icon, int slots, int usedSlots, long sortOrder) {
            super(baseName, icon, slots, usedSlots, sortOrder);
        }
    }

    private static final class NeoEcoCraftingProviderF6 extends TestPatternProvider {

        private NeoEcoCraftingProviderF6(String baseName, Item icon, int slots, int usedSlots, long sortOrder) {
            super(baseName, icon, slots, usedSlots, sortOrder);
        }
    }

    private static AppEngInternalInventory inventory(int slots, int usedSlots) {
        AppEngInternalInventory inventory = new AppEngInternalInventory(slots);
        for (int slot = 0; slot < usedSlots; slot++) {
            inventory.setItemDirect(slot, new ItemStack(Items.PAPER));
        }
        return inventory;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static final class TestGrid implements IGrid {

        private final Set<PatternContainer> providers;

        private TestGrid(List<? extends PatternContainer> providers) {
            this.providers = new LinkedHashSet<>(providers);
        }

        @Override
        public <C extends IGridService> C getService(Class<C> serviceClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            return List.of(PatternContainer.class);
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Set<T> getMachines(Class<T> machineClass) {
            if (machineClass == PatternProviderLogicHost.class) {
                return Set.of();
            }
            if (machineClass == PatternContainer.class) {
                return (Set<T>) this.providers;
            }
            return Set.of();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> machineClass) {
            return getMachines(machineClass);
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            return List.of();
        }

        @Override
        public boolean isEmpty() {
            return this.providers.isEmpty();
        }

        @Override
        public IGridNode getPivot() {
            return null;
        }

        @Override
        public int size() {
            return this.providers.size();
        }

        @Override
        public void export(JsonWriter jsonWriter) throws IOException {}
    }
}
