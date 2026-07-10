package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.events.GridEvent;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingPlan;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreCraftingRuntimeTest {

    private static final int SCHEMA_VERSION = 1;

    private TrinityDataCoreCraftingRuntimeTest() {}

    @TestHolder("trinity_data_core_cpu_partitions_require_formed_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPartitionsRequireFormedStructure(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(false);

        helper.assertValueEqual(flower.getCpuPartitions().size(), 0, "Unformed flower should not expose CPUs");

        flower.loadTag(formedTag(), HolderLookup.Provider.create(Stream.empty()));

        helper.assertValueEqual(flower.getCpuPartitions().size(), 0, "Formed main structure should not expose CPU partitions");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().storageBytes(),
                0L,
                "Formed main structure should not contribute crafting storage");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_contribution_rebuilds_partitions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuContributionRebuildsPartitions(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(true);

        flower.setCpuContribution("petal", TrinityDataCoreCpuContribution.of(1024L, 2, 2));

        helper.assertValueEqual(flower.getCpuPartitions().size(), 2, "Child contribution should add CPU partitions");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().coProcessors(),
                2,
                "Child contribution should add co-processors");
        flower.clearCpuContribution("petal");
        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                0,
                "Clearing child contribution should remove child CPU partitions");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_pauses_and_resumes_existing_job")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimePausesAndResumesExistingJob(GameTestHelper helper) {
        BusyRuntimeFixture fixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        ICraftingLink link = fixture.cpu().logic().getLastLink();
        if (link == null) {
            helper.fail("Submitted CPU job should expose its crafting link");
            return;
        }

        link.cancel();
        fixture.runtime().setPaused(true);
        fixture.runtime().tick(null, null);

        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Paused runtime must not process or discard the canceled job");

        fixture.runtime().setPaused(false);
        fixture.runtime().tick(null, null);

        helper.assertFalse(fixture.runtime().hasBusyJobs(), "Resumed runtime should process the canceled job on its next tick");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_retains_job_across_structure_pause")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimeRetainsJobAcrossStructurePause(GameTestHelper helper) {
        BusyRuntimeFixture fixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        TrinityDataCoreVirtualCpu originalCpu = fixture.cpu();

        fixture.runtime().setPaused(true);
        fixture.runtime().clearContribution("cpu");
        fixture.runtime().setMainStructureFormed(false);

        helper.assertValueEqual(fixture.runtime().partitions().size(), 0, "Invalid structure must withdraw CPU partitions");
        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Invalid structure must retain its existing CPU job");

        fixture.runtime().setMainStructureFormed(true);
        fixture.runtime().setContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));
        fixture.runtime().setPaused(false);

        helper.assertValueEqual(fixture.runtime().partitions().size(), 1, "Recovered structure should republish its CPU partition");
        helper.assertTrue(
                fixture.runtime().partitions().getFirst() == originalCpu,
                "Recovered structure should reuse the partition that owns the paused job");
        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Recovered partition should still own the paused job");

        fixture.runtime().cancelAllJobs();

        helper.assertFalse(fixture.runtime().hasBusyJobs(), "Explicit host removal should cancel all retained jobs");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_inactive_cpu_job_round_trips_through_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void inactiveCpuJobRoundTripsThroughNbt(GameTestHelper helper) {
        BusyRuntimeFixture fixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        fixture.runtime().setPaused(true);
        fixture.runtime().clearContribution("cpu");
        fixture.runtime().setMainStructureFormed(false);

        CompoundTag saved = new CompoundTag();
        fixture.runtime().writeToTag(saved, helper.getLevel().registryAccess());

        TestFlower restoredFlower = new TestFlower(helper.absolutePos(new BlockPos(2, 1, 1)));
        restoredFlower.setLevel(helper.getLevel());
        restoredFlower.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        TrinityDataCoreCraftingRuntime restored = restoredFlower.getCraftingRuntime();
        restored.readFromTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(restored.partitions().size(), 0, "Inactive persisted CPU must remain withdrawn after reload");
        helper.assertTrue(restored.hasBusyJobs(), "Inactive persisted CPU must restore its paused job");

        restored.setMainStructureFormed(true);
        restored.setContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));

        helper.assertValueEqual(restored.partitions().size(), 1, "Restored CPU should republish after contribution recovery");
        helper.assertTrue(restored.partitions().getFirst().isBusy(), "Republished CPU should still own the restored job");
        restored.cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_host_root_nbt_round_trips_active_job")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostRootNbtRoundTripsActiveJob(GameTestHelper helper) {
        BusyRuntimeFixture fixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        UUID storageId = fixture.flower().getStorageId();
        UUID hostId = fixture.flower().getHostId();
        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Source host should own an active CPU job before saving");

        CompoundTag saved = new CompoundTag();
        fixture.flower().saveAdditional(saved, helper.getLevel().registryAccess());

        TestFlower restored = new TestFlower(helper.absolutePos(new BlockPos(3, 1, 1)));
        restored.setLevel(helper.getLevel());
        restored.loadTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(restored.getStorageId(), storageId, "Root NBT should preserve the storage UUID");
        helper.assertValueEqual(restored.getHostId(), hostId, "Root NBT should preserve the routing UUID");
        helper.assertTrue(restored.isStructureFormed(), "Root NBT should preserve the main structure state");
        helper.assertTrue(restored.isCpuStructureFormed(), "Root NBT should preserve the CPU structure state");
        helper.assertTrue(restored.isCraftingStructureFormed(), "Root NBT should preserve the crafting structure state");
        helper.assertValueEqual(restored.getCpuPartitions().size(), 1, "Root NBT should restore the active CPU partition");
        helper.assertTrue(restored.getCraftingRuntime().hasBusyJobs(), "Root NBT should restore the active CPU job");
        helper.assertTrue(restored.getCpuPartitions().getFirst().isBusy(), "Restored CPU partition should own the active job");

        fixture.runtime().cancelAllJobs();
        restored.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_main_structure_failure_retains_cpu_child_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mainStructureFailureRetainsCpuChildState(GameTestHelper helper) {
        BlockPos flowerPos = new BlockPos(1, 1, 1);
        helper.setBlock(flowerPos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(flowerPos));
        if (!(blockEntity instanceof DigitalConstructFlowerBlockEntity flower)) {
            helper.fail("Expected a placed Trinity Data Core block entity", flowerPos);
            return;
        }
        flower.loadTag(formedCraftingProfileTag(), helper.getLevel().registryAccess());
        flower.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 1, 1));

        helper.assertValueEqual(flower.getCpuPartitions().size(), 1, "CPU child contribution should be active before recheck");
        helper.assertTrue(flower.isCraftingStructureFormed(), "Crafting child structure should be active before recheck");
        helper.assertValueEqual(
                flower.getCraftingPatternCapacity(),
                704,
                "Crafting child profile should be active before recheck");
        TrinityDataCoreVirtualCpu retainedPartition = flower.getCpuPartitions().getFirst();

        flower.serverTick();

        helper.assertFalse(flower.isStructureFormed(), "Missing main structure should make the host unformed");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().storageBytes(),
                1024L,
                "Main structure failure should retain the last valid CPU contribution");
        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                0,
                "Main structure failure should withdraw CPU partitions from AE2 while paused");
        helper.assertFalse(flower.isCraftingStructureFormed(), "Main structure failure should withdraw crafting child status");
        helper.assertValueEqual(
                flower.getCraftingPatternCapacity(),
                704,
                "Main structure failure should retain the last valid crafting profile");

        flower.getCraftingRuntime().setMainStructureFormed(true);

        helper.assertValueEqual(flower.getCpuPartitions().size(), 1,
                "Recovered main structure should republish the retained CPU partition");
        helper.assertTrue(flower.getCpuPartitions().getFirst() == retainedPartition,
                "Recovered main structure should reuse the retained CPU partition and its job state");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_crafting_profile_round_trips_through_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void craftingProfileRoundTripsThroughNbt(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity original = digitalConstructFlower(false);
        original.loadTag(formedCraftingProfileTag(), HolderLookup.Provider.create(Stream.empty()));

        helper.assertTrue(original.isCraftingStructureFormed(), "Loaded crafting child structure should be formed");
        helper.assertValueEqual(
                original.getCraftingStructureMatchedBlockCount(),
                314,
                "Loaded crafting child structure should preserve matched block count");
        helper.assertValueEqual(
                original.getCraftingPatternCoreCount(),
                3,
                "Loaded crafting child structure should preserve pattern core count");
        helper.assertValueEqual(
                original.getCraftingPatternCapacity(),
                704,
                "Loaded crafting child structure should preserve pattern capacity");

        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, HolderLookup.Provider.create(Stream.empty()));
        DigitalConstructFlowerBlockEntity loaded = digitalConstructFlower(false);
        loaded.loadTag(saved, HolderLookup.Provider.create(Stream.empty()));

        helper.assertTrue(loaded.isCraftingStructureFormed(), "Saved crafting child structure should remain formed");
        helper.assertValueEqual(
                loaded.getCraftingStructureMatchedBlockCount(),
                314,
                "Saved crafting child structure should round-trip matched block count");
        helper.assertValueEqual(
                loaded.getCraftingPatternCoreCount(),
                3,
                "Saved crafting child structure should round-trip pattern core count");
        helper.assertValueEqual(
                loaded.getCraftingPatternCapacity(),
                704,
                "Saved crafting child structure should round-trip pattern capacity");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_defers_partition_logic_until_level_exists")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimeDefersPartitionLogicUntilLevelExists(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(true);

        CompoundTag runtimeTag = new CompoundTag();
        runtimeTag.putInt("schema_version", SCHEMA_VERSION);
        runtimeTag.put("contributions", new ListTag());
        ListTag partitionsTag = new ListTag();
        CompoundTag partitionTag = new CompoundTag();
        partitionTag.putInt("index", 0);
        partitionTag.putInt("partition_count", 1);
        partitionTag.putLong("storage_bytes", 1024L);
        partitionTag.putInt("co_processors", 0);
        partitionTag.putString("selection_mode", CpuSelectionMode.ANY.name());
        CompoundTag logicTag = new CompoundTag();
        logicTag.put("job", new CompoundTag());
        partitionTag.put("logic", logicTag);
        partitionsTag.add(partitionTag);
        runtimeTag.put("partitions", partitionsTag);

        flower.getCraftingRuntime().readFromTag(
                runtimeTag,
                HolderLookup.Provider.create(Stream.empty()));
        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                0,
                "Pending partition logic should not create CPU partitions without a child contribution");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_host_rejects_legacy_and_unsupported_schema")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostRejectsLegacyAndUnsupportedSchema(GameTestHelper helper) {
        BusyRuntimeFixture legacyFixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        UUID legacyStorageId = legacyFixture.flower().getStorageId();
        UUID legacyHostId = legacyFixture.flower().getHostId();
        CompoundTag legacyTag = formedTrinityTag();
        legacyTag.remove("schema_version");
        legacyTag.remove("trinity_data_core_storage_id");
        legacyTag.remove("trinity_data_core_host_id");
        legacyTag.putString("storage_id", legacyStorageId.toString());
        legacyTag.putUUID("host_id", legacyHostId);

        legacyFixture.flower().loadTag(legacyTag, helper.getLevel().registryAccess());

        assertRejectedHostState(helper, legacyFixture, legacyStorageId, legacyHostId, "Legacy root NBT");

        BusyRuntimeFixture unsupportedFixture = busyRuntime(helper, new BlockPos(3, 1, 1));
        UUID unsupportedStorageId = unsupportedFixture.flower().getStorageId();
        UUID unsupportedHostId = unsupportedFixture.flower().getHostId();
        CompoundTag unsupportedTag = formedTrinityTag();
        unsupportedTag.putInt("schema_version", SCHEMA_VERSION + 1);
        unsupportedTag.putUUID("trinity_data_core_storage_id", unsupportedStorageId);
        unsupportedTag.putUUID("trinity_data_core_host_id", unsupportedHostId);

        unsupportedFixture.flower().loadTag(unsupportedTag, helper.getLevel().registryAccess());

        assertRejectedHostState(
                helper,
                unsupportedFixture,
                unsupportedStorageId,
                unsupportedHostId,
                "Unsupported root NBT");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_id_round_trips_through_item_and_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageIdRoundTripsThroughItemAndNbt(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity original = digitalConstructFlower(false);
        ItemStack stack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        original.saveStorageIdToItem(stack);
        UUID storageId = stack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID);

        DigitalConstructFlowerBlockEntity placed = digitalConstructFlower(false);
        placed.restoreStorageIdFromItem(stack);
        ItemStack placedStack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        placed.saveStorageIdToItem(placedStack);
        helper.assertValueEqual(
                placedStack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                "Placed host should restore the storage id from the item component");

        CompoundTag saved = new CompoundTag();
        placed.saveAdditional(saved, HolderLookup.Provider.create(Stream.empty()));
        DigitalConstructFlowerBlockEntity loaded = digitalConstructFlower(false);
        loaded.loadTag(saved, HolderLookup.Provider.create(Stream.empty()));
        ItemStack loadedStack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        loaded.saveStorageIdToItem(loadedStack);
        helper.assertValueEqual(
                loadedStack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                "Loaded host should keep the storage id from block entity NBT");
        helper.succeed();
    }

    private static DigitalConstructFlowerBlockEntity digitalConstructFlower(boolean formed) {
        DigitalConstructFlowerBlockEntity flower = new DigitalConstructFlowerBlockEntity(
                BlockPos.ZERO,
                ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        if (formed) {
            flower.loadTag(formedTag(), HolderLookup.Provider.create(Stream.empty()));
        }
        return flower;
    }

    private static CompoundTag formedTag() {
        CompoundTag tag = currentHostTag();
        tag.putBoolean("formed", true);
        return tag;
    }

    private static CompoundTag currentHostTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", SCHEMA_VERSION);
        tag.putUUID("trinity_data_core_storage_id", UUID.randomUUID());
        tag.putUUID("trinity_data_core_host_id", UUID.randomUUID());
        CompoundTag runtimeTag = new CompoundTag();
        runtimeTag.putInt("schema_version", SCHEMA_VERSION);
        runtimeTag.put("contributions", new ListTag());
        runtimeTag.put("partitions", new ListTag());
        tag.put("trinity_data_core_crafting_runtime", runtimeTag);
        return tag;
    }

    private static CompoundTag formedCraftingProfileTag() {
        CompoundTag tag = formedTag();
        tag.putBoolean("crafting_structure_formed", true);
        tag.putInt("crafting_structure_matched_block_count", 314);
        tag.putInt("crafting_pattern_core_count", 3);
        tag.putInt("crafting_pattern_capacity", 704);
        return tag;
    }

    private static BusyRuntimeFixture busyRuntime(GameTestHelper helper, BlockPos flowerPos) {
        TestFlower flower = new TestFlower(helper.absolutePos(flowerPos));
        flower.setLevel(helper.getLevel());
        flower.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        flower.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));
        TestGrid grid = new TestGrid();

        TrinityDataCoreVirtualCpu cpu = flower.getCpuPartitions().getFirst();
        ICraftingSubmitResult result = cpu.submitJob(
                grid,
                new CraftingPlan(
                        new GenericStack(AEItemKey.of(Items.DIAMOND), 1L),
                        1L,
                        false,
                        false,
                        new KeyCounter(),
                        new KeyCounter(),
                        new KeyCounter(),
                        Map.of()),
                IActionSource.empty(),
                null);
        if (!result.successful()) {
            throw new IllegalStateException("Test CPU job submission failed: " + result.errorCode());
        }
        return new BusyRuntimeFixture(flower, flower.getCraftingRuntime(), cpu);
    }

    private static CompoundTag formedTrinityTag() {
        CompoundTag tag = formedTag();
        tag.putBoolean("cpu_structure_formed", true);
        tag.putBoolean("crafting_structure_formed", true);
        tag.putInt("crafting_pattern_core_count", 1);
        tag.putInt("crafting_pattern_capacity", 64);
        return tag;
    }

    private static void assertRejectedHostState(GameTestHelper helper,
                                                BusyRuntimeFixture fixture,
                                                UUID persistedStorageId,
                                                UUID persistedHostId,
                                                String source) {
        helper.assertFalse(fixture.flower().isStructureFormed(), source + " must not restore the main structure state");
        helper.assertFalse(fixture.flower().isCpuStructureFormed(), source + " must not restore the CPU child state");
        helper.assertFalse(
                fixture.flower().isCraftingStructureFormed(),
                source + " must not restore the crafting child state");
        helper.assertValueEqual(
                fixture.runtime().profile().storageBytes(),
                0L,
                source + " must discard CPU contributions");
        helper.assertFalse(fixture.runtime().hasBusyJobs(), source + " must discard running CPU jobs");
        helper.assertFalse(
                fixture.flower().getStorageId().equals(persistedStorageId),
                source + " must not retain the persisted storage identity");
        helper.assertFalse(
                fixture.flower().getHostId().equals(persistedHostId),
                source + " must not retain the persisted routing identity");
    }

    private record BusyRuntimeFixture(TestFlower flower,
                                      TrinityDataCoreCraftingRuntime runtime,
                                      TrinityDataCoreVirtualCpu cpu) {}

    private static final class TestFlower extends DigitalConstructFlowerBlockEntity {

        private TestFlower(BlockPos pos) {
            super(pos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        }

        @Override
        public boolean hasActiveAccessHatch() {
            return true;
        }
    }

    private static final class TestGrid implements IGrid {

        private final IStorageService storageService = new EmptyStorageService();

        @Override
        public <C extends IGridService> C getService(Class<C> serviceType) {
            if (serviceType == IStorageService.class) {
                return serviceType.cast(this.storageService);
            }
            throw new IllegalArgumentException("Unsupported test grid service: " + serviceType.getName());
        }

        @Override
        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            return Set.of();
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getMachines(Class<T> machineClass) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> machineClass) {
            return Set.of();
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            return Set.of();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public IGridNode getPivot() {
            throw new IllegalStateException("Test grid has no pivot node");
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void export(JsonWriter jsonWriter) throws IOException {
            jsonWriter.beginObject();
            jsonWriter.endObject();
        }
    }

    private static final class EmptyStorageService implements IStorageService {

        private final MEStorage inventory = new EmptyStorage();

        @Override
        public MEStorage getInventory() {
            return this.inventory;
        }

        @Override
        public KeyCounter getCachedInventory() {
            return new KeyCounter();
        }

        @Override
        public void addGlobalStorageProvider(IStorageProvider provider) {
            throw new UnsupportedOperationException("Test storage does not mount providers");
        }

        @Override
        public void removeGlobalStorageProvider(IStorageProvider provider) {
            throw new UnsupportedOperationException("Test storage does not mount providers");
        }

        @Override
        public void refreshNodeStorageProvider(IGridNode node) {
            throw new UnsupportedOperationException("Test storage does not mount providers");
        }

        @Override
        public void refreshGlobalStorageProvider(IStorageProvider provider) {
            throw new UnsupportedOperationException("Test storage does not mount providers");
        }

        @Override
        public void invalidateCache() {
            throw new UnsupportedOperationException("Test storage has no cache");
        }
    }

    private static final class EmptyStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {}

        @Override
        public Component getDescription() {
            return Component.literal("empty test storage");
        }
    }
}
