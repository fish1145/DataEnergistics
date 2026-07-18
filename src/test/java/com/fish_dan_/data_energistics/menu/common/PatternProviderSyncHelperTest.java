package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.PatternProviderNameHelper;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.events.GridEvent;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
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

    @TestHolder("pattern_provider_name_access_stays_server_safe")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternProviderNameAccessStaysServerSafe(GameTestHelper helper) {
        TestPatternProvider unsupported = new ClientFieldPatternProvider(
                "Third-party Provider", Items.CRAFTING_TABLE, 1, 0, 10);
        assertTrue(!PatternProviderNameHelper.canRename(unsupported));
        assertEquals(null, PatternProviderNameHelper.getCustomName(unsupported));
        assertTrue(!PatternProviderNameHelper.setCustomName(unsupported, Component.literal("Ignored")));

        BlockPos providerPosition = new BlockPos(1, 1, 1);
        helper.setBlock(providerPosition, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(providerPosition);
        if (!(blockEntity instanceof PatternProviderBlockEntity provider)) {
            throw new GameTestAssertException("Placed AE2 pattern provider has no matching block entity");
        }

        Component customName = Component.literal("Dedicated Line");
        assertTrue(PatternProviderNameHelper.canRename(provider));
        assertTrue(PatternProviderNameHelper.setCustomName(provider, customName));
        assertEquals(customName, PatternProviderNameHelper.getCustomName(provider));

        assertTrue(PatternProviderNameHelper.setCustomName(provider, null));
        assertEquals(null, PatternProviderNameHelper.getCustomName(provider));
        PatternProviderNameHelper.syncRename(provider);
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

    @TestHolder("pattern_provider_upload_reports_committed_write_when_notification_fails")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reportsCommittedWriteWhenNotificationFails(GameTestHelper helper) {
        FailingSaveNotificationHost inventoryHost = new FailingSaveNotificationHost();
        AppEngInternalInventory patternInventory = new AppEngInternalInventory(inventoryHost, 1);
        TestPatternProvider provider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, patternInventory, 10);
        ItemStack encodedPattern = encodedProcessingPattern();

        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                List.of(provider), encodedPattern);

        assertEquals(1, inventoryHost.saveAttemptCount);
        assertTrue(result.transferred());
        assertTrue(result.remainder().isEmpty());
        assertTrue(!result.duplicateFound());
        ItemStack uploadedPattern = patternInventory.getStackInSlot(0);
        assertTrue(ItemStack.isSameItemSameComponents(encodedPattern, uploadedPattern));
        assertEquals(encodedPattern.getCount(), uploadedPattern.getCount());
        helper.succeed();
    }

    @TestHolder("pattern_provider_upload_uses_committed_delta_when_inventory_misreports_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesCommittedDeltaWhenInventoryMisreportsRemainder(GameTestHelper helper) {
        MisreportingPatternInventory patternInventory = new MisreportingPatternInventory();
        TestPatternProvider provider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, patternInventory, 10);
        ItemStack encodedPattern = encodedProcessingPattern();

        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                List.of(provider), encodedPattern);

        assertTrue(result.transferred());
        assertTrue(result.remainder().isEmpty());
        assertTrue(!result.duplicateFound());
        assertEquals(1, patternInventory.size());
        ItemStack uploadedPattern = patternInventory.getStackInSlot(0);
        assertTrue(ItemStack.isSameItemSameComponents(encodedPattern, uploadedPattern));
        assertEquals(1, uploadedPattern.getCount());
        helper.succeed();
    }

    @TestHolder("pattern_provider_upload_reports_committed_write_when_linkage_notification_fails")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reportsCommittedWriteWhenLinkageNotificationFails(GameTestHelper helper) {
        LinkageFailingSaveNotificationHost inventoryHost = new LinkageFailingSaveNotificationHost();
        AppEngInternalInventory patternInventory = new AppEngInternalInventory(inventoryHost, 1);
        TestPatternProvider provider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, patternInventory, 10);
        ItemStack encodedPattern = encodedProcessingPattern();

        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                List.of(provider), encodedPattern);

        assertEquals(1, inventoryHost.saveAttemptCount);
        assertTrue(result.transferred());
        assertTrue(result.remainder().isEmpty());
        assertTrue(!result.duplicateFound());
        ItemStack uploadedPattern = patternInventory.getStackInSlot(0);
        assertTrue(ItemStack.isSameItemSameComponents(encodedPattern, uploadedPattern));
        assertEquals(encodedPattern.getCount(), uploadedPattern.getCount());
        helper.succeed();
    }

    @TestHolder("pattern_provider_upload_does_not_commit_when_insertion_fails_before_write")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void doesNotCommitWhenInsertionFailsBeforeWrite(GameTestHelper helper) {
        FailingBeforeWritePatternInventory patternInventory = new FailingBeforeWritePatternInventory();
        TestPatternProvider provider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, patternInventory, 10);
        ItemStack encodedPattern = encodedProcessingPattern();

        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                List.of(provider), encodedPattern);

        assertTrue(!result.transferred());
        assertTrue(!result.duplicateFound());
        assertTrue(ItemStack.isSameItemSameComponents(encodedPattern, result.remainder()));
        assertEquals(encodedPattern.getCount(), result.remainder().getCount());
        assertTrue(patternInventory.getStackInSlot(0).isEmpty());
        helper.succeed();
    }

    @TestHolder("pattern_provider_upload_rejects_reported_commit_without_inventory_delta")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsReportedCommitWithoutInventoryDelta(GameTestHelper helper) {
        OverreportingPatternInventory patternInventory = new OverreportingPatternInventory();
        TestPatternProvider provider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, patternInventory, 10);
        ItemStack encodedPattern = encodedProcessingPattern();

        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                List.of(provider), encodedPattern);

        assertTrue(!result.transferred());
        assertTrue(!result.duplicateFound());
        assertTrue(ItemStack.isSameItemSameComponents(encodedPattern, result.remainder()));
        assertEquals(encodedPattern.getCount(), result.remainder().getCount());
        assertTrue(patternInventory.getStackInSlot(0).isEmpty());
        helper.succeed();
    }

    @TestHolder("pattern_provider_upload_commits_partial_insertions_across_providers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void commitsPartialInsertionsAcrossProviders(GameTestHelper helper) {
        AppEngInternalInventory firstInventory = new AppEngInternalInventory(1);
        AppEngInternalInventory secondInventory = new AppEngInternalInventory(1);
        firstInventory.setMaxStackSize(0, 1);
        secondInventory.setMaxStackSize(0, 1);
        TestPatternProvider firstProvider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, firstInventory, 10);
        TestPatternProvider secondProvider = new TestPatternProvider(
                "Assembler", Items.CRAFTING_TABLE, secondInventory, 20);
        ItemStack encodedPatterns = encodedProcessingPattern();
        encodedPatterns.setCount(2);

        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                List.of(firstProvider, secondProvider), encodedPatterns);

        assertTrue(result.transferred());
        assertTrue(result.remainder().isEmpty());
        assertTrue(!result.duplicateFound());
        assertEquals(1, firstInventory.getStackInSlot(0).getCount());
        assertEquals(1, secondInventory.getStackInSlot(0).getCount());
        assertTrue(ItemStack.isSameItemSameComponents(
                encodedPatterns, firstInventory.getStackInSlot(0)));
        assertTrue(ItemStack.isSameItemSameComponents(
                encodedPatterns, secondInventory.getStackInSlot(0)));
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
            this(baseName, icon, inventory(slots, usedSlots), sortOrder);
        }

        private TestPatternProvider(String baseName, Item icon, AppEngInternalInventory inventory, long sortOrder) {
            this.baseName = baseName;
            this.icon = AEItemKey.of(icon);
            this.inventory = inventory;
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

    private static final class ClientFieldPatternProvider extends TestPatternProvider {

        @SuppressWarnings("unused")
        private SoundInstance clientSound;

        private ClientFieldPatternProvider(String baseName, Item icon, int slots, int usedSlots, long sortOrder) {
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

    private static ItemStack encodedProcessingPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
    }

    private static final class MisreportingPatternInventory extends AppEngInternalInventory {

        private MisreportingPatternInventory() {
            super(1);
        }

        @Override
        public ItemStack addItems(ItemStack stack, boolean simulate) {
            ItemStack actualRemainder = insertItem(0, stack, simulate);
            return !simulate && actualRemainder.isEmpty() ? stack : actualRemainder;
        }
    }

    private static final class OverreportingPatternInventory extends AppEngInternalInventory {

        private OverreportingPatternInventory() {
            super(1);
        }

        @Override
        public ItemStack addItems(ItemStack stack, boolean simulate) {
            return simulate ? stack : ItemStack.EMPTY;
        }
    }

    private static final class FailingBeforeWritePatternInventory extends AppEngInternalInventory {

        private FailingBeforeWritePatternInventory() {
            super(1);
        }

        @Override
        public ItemStack addItems(ItemStack stack, boolean simulate) {
            throw new IllegalStateException("Simulated insertion failure before the pattern slot was changed");
        }
    }

    private static final class FailingSaveNotificationHost implements InternalInventoryHost {

        private int saveAttemptCount;

        @Override
        public void saveChangedInventory(AppEngInternalInventory inventory) {
            this.saveAttemptCount++;
            throw new IllegalStateException("Simulated notification failure after the pattern slot was committed");
        }

        @Override
        public boolean isClientSide() {
            return false;
        }
    }

    private static final class LinkageFailingSaveNotificationHost implements InternalInventoryHost {

        private int saveAttemptCount;

        @Override
        public void saveChangedInventory(AppEngInternalInventory inventory) {
            this.saveAttemptCount++;
            throw new NoClassDefFoundError("Simulated linkage failure after the pattern slot was committed");
        }

        @Override
        public boolean isClientSide() {
            return false;
        }
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
