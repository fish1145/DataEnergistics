package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.util.LongAmountMath;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridService;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
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
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.GridNode;
import appeng.me.service.CraftingService;
import com.google.common.collect.ImmutableSet;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreCraftingRuntimeTest {

    private static final int HOST_SCHEMA_VERSION = 1;
    private static final int RUNTIME_SCHEMA_VERSION = 2;
    private static final int LEGACY_RUNTIME_SCHEMA_VERSION = 1;
    private static final int CPU_LOGIC_SCHEMA_VERSION = 1;
    private static final long COUNTED_BATCH_SIZE = 128L;

    private TrinityDataCoreCraftingRuntimeTest() {}

    @TestHolder("trinity_data_core_cpu_partitions_require_formed_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPartitionsRequireFormedStructure(GameTestHelper helper) {
        TrinityDataCoreBlockEntity host = trinityDataCore(false);

        helper.assertValueEqual(host.getCpuPartitions().size(), 0, "Unformed host should not expose CPUs");

        host.loadTag(formedTag(), HolderLookup.Provider.create(Stream.empty()));

        helper.assertValueEqual(host.getCpuPartitions().size(), 0, "Formed main structure should not expose CPU partitions");
        helper.assertValueEqual(
                host.getCraftingRuntime().profile().storageBytes(),
                0L,
                "Formed main structure should not contribute crafting storage");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_rejects_wrong_grid_and_stale_partition_without_extracting")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRejectsWrongGridAndStalePartitionWithoutExtracting(GameTestHelper helper) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        TestGrid leaseGrid = new TestGrid();
        TestGrid wrongGrid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), leaseGrid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        CraftingPlan plan = ingredientPlan(iron, 2L);
        seedStorage(leaseGrid.storage(), iron, 2L);
        seedStorage(wrongGrid.storage(), iron, 2L);

        assertOfflineWithoutExtraction(helper, reserveCpu, wrongGrid, plan, iron, "Wrong grid");

        host.setCpuProviderAvailable(false);
        assertOfflineWithoutExtraction(helper, reserveCpu, leaseGrid, plan, iron, "Unavailable CPU child");

        host.setCpuProviderAvailable(true);
        host.clearCpuContribution("cpu");
        assertOfflineWithoutExtraction(helper, reserveCpu, leaseGrid, plan, iron, "Stale CPU partition");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_permanent_removal_recovers_cpu_inventory_when_grid_rejects")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void permanentRemovalRecoversCpuInventoryWhenGridRejects(GameTestHelper helper) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(2048L, 0, 2));
        seedStorage(grid.storage(), iron, 4L);

        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        for (int job = 0; job < 2; job++) {
            ICraftingSubmitResult result = reserveCpu.submitJob(
                    grid,
                    ingredientPlan(iron, 2L),
                    IActionSource.empty(),
                    null);
            helper.assertTrue(result.successful(), "Recovery test should submit a job to every retained CPU");
        }
        List<TrinityDataCoreVirtualCpu> cpus = host.getCpuPartitions().stream()
                .filter(TrinityDataCoreVirtualCpu::isBusy)
                .toList();
        helper.assertValueEqual(cpus.size(), 2, "Recovery test should allocate worker CPUs 1 and 2");
        helper.assertValueEqual(cpus.get(0).number(), 1, "First recovery job should use worker CPU 1");
        helper.assertValueEqual(cpus.get(1).number(), 2, "Second recovery job should use worker CPU 2");
        helper.assertValueEqual(grid.storage().getStored(iron), 0L,
                "Submitted CPU jobs should own all four extracted ingredients");

        grid.storage().setMaxAcceptedPerInsert(0L);
        host.onPermanentRemoval();

        for (TrinityDataCoreVirtualCpu cpu : cpus) {
            helper.assertFalse(cpu.isBusy(), "Permanent removal should cancel every recovery test job");
            helper.assertValueEqual(cpu.getStored(iron), 0L,
                    "Every retained CPU inventory must be empty after durable recovery");
        }
        helper.assertValueEqual(
                TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer()).amount(host.getStorageId(), iron),
                BigInteger.valueOf(4L),
                "All grid-rejected CPU ingredients should persist under the dropped host storage UUID");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_contribution_rebuilds_partitions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuContributionRebuildsPartitions(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());

        host.setCpuContribution("partition", TrinityDataCoreCpuContribution.of(1024L, 2, 2));

        helper.assertValueEqual(host.getCpuPartitions().size(), 1, "Idle runtime should publish only reserved CPU 0");
        helper.assertValueEqual(host.getCpuPartitions().getFirst().number(), 0, "Idle runtime should reserve CPU number 0");
        helper.assertValueEqual(
                host.getCraftingRuntime().profile().coProcessors(),
                2,
                "Child contribution should add co-processors");
        host.clearCpuContribution("partition");
        helper.assertValueEqual(
                host.getCpuPartitions().size(),
                0,
                "Clearing child contribution should remove child CPU partitions");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_pool_reuses_lowest_available_worker")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPoolReusesLowestAvailableWorker(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 2));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();

        helper.assertTrue(
                reserveCpu.submitJob(grid, emptyJobPlan(), IActionSource.empty(), null).successful(),
                "First pool job should be accepted");
        helper.assertTrue(
                reserveCpu.submitJob(grid, emptyJobPlan(), IActionSource.empty(), null).successful(),
                "Second pool job should be accepted");

        List<TrinityDataCoreVirtualCpu> published = host.getCpuPartitions();
        helper.assertValueEqual(published.size(), 3, "Pool should publish reserved CPU 0 and two busy workers");
        helper.assertValueEqual(published.get(0).number(), 0, "Reserved CPU must remain first");
        helper.assertValueEqual(published.get(1).number(), 1, "First job must use worker CPU 1");
        helper.assertValueEqual(published.get(2).number(), 2, "Second job must use worker CPU 2");

        TrinityDataCoreVirtualCpu releasedWorker = published.get(1);
        releasedWorker.cancelJob();
        helper.assertTrue(
                reserveCpu.submitJob(grid, emptyJobPlan(), IActionSource.empty(), null).successful(),
                "A new job should reuse the released low worker number");

        published = host.getCpuPartitions();
        helper.assertValueEqual(published.get(1).number(), 1, "Released worker number 1 must be reused first");
        helper.assertFalse(
                published.get(1) == releasedWorker,
                "Reused worker number should belong to a fresh runtime worker object");
        helper.assertValueEqual(published.get(2).number(), 2, "Worker CPU 2 must retain its existing job");
        host.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_releases_all_workers_after_rotated_tick")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimeReleasesAllWorkersAfterRotatedTick(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 3));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        for (int workerNumber = 1; workerNumber <= 3; workerNumber++) {
            helper.assertTrue(
                    reserveCpu.submitJob(grid, emptyJobPlan(), IActionSource.empty(), null).successful(),
                    "Release test should allocate worker " + workerNumber);
        }
        List<TrinityDataCoreVirtualCpu> workers = host.getCpuPartitions().subList(1, 4);
        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        runtime.cancelAllJobs();

        runtime.tick(grid.energyService(), grid.craftingService(), CraftingDispatchWindow.create());

        for (TrinityDataCoreVirtualCpu worker : workers) {
            helper.assertFalse(
                    runtime.hasCpu(worker),
                    "One rotated tick must release worker " + worker.number() + " exactly once");
        }
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_pool_supports_worker_256_boundary")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPoolSupportsWorker256Boundary(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 256));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        CraftingPlan plan = emptyJobPlan();

        for (int workerNumber = 1; workerNumber <= 256; workerNumber++) {
            ICraftingSubmitResult result = reserveCpu.submitJob(grid, plan, IActionSource.empty(), null);
            helper.assertTrue(result.successful(), "Worker allocation should succeed through CPU 256");
        }

        List<TrinityDataCoreVirtualCpu> published = host.getCpuPartitions();
        helper.assertValueEqual(published.size(), 257, "Full pool should publish CPU 0 and 256 busy workers");
        helper.assertValueEqual(published.getFirst().number(), 0, "Full pool must keep reserved CPU 0 first");
        helper.assertValueEqual(published.getLast().number(), 256, "Full pool must expose worker CPU 256");
        helper.assertTrue(
                reserveCpu.submitJob(grid, plan, IActionSource.empty(), null) == CraftingSubmitResult.CPU_BUSY,
                "A 257th worker job must be rejected as CPU busy");
        host.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_256_workers_dispatch_independent_operation_budgets")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void workers256DispatchIndependentOperationBudgets(GameTestHelper helper) {
        int workerCount = TrinityDataCoreCpuProfile.MAX_PARTITION_COUNT;
        int providerCount = workerCount / CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER;
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        PendingPatternDetails pattern = new PendingPatternDetails(output);
        List<RecordingCraftingProvider> providers = new ArrayList<>(providerCount);
        List<ICraftingProvider> configuredProviders = new ArrayList<>(providerCount);
        for (int providerIndex = 0; providerIndex < providerCount; providerIndex++) {
            RecordingCraftingProvider provider = new RecordingCraftingProvider(pattern);
            providers.add(provider);
            configuredProviders.add(provider);
        }

        TestGrid grid = new TestGrid();
        grid.setCraftingProviders(configuredProviders);
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("independent", TrinityDataCoreCpuContribution.of(1L, 0, workerCount));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        CraftingPlan plan = patternPlan(pattern, 1L);
        for (int workerNumber = 1; workerNumber <= workerCount; workerNumber++) {
            ICraftingSubmitResult result = reserveCpu.submitJob(grid, plan, IActionSource.empty(), null);
            helper.assertTrue(result.successful(), "Independent budget job should allocate worker " + workerNumber);
        }

        CraftingDispatchWindow dispatchWindow = CraftingDispatchWindow.create();
        long startedNanos = System.nanoTime();
        host.getCraftingRuntime().tick(grid.energyService(), grid.craftingService(), dispatchWindow);
        long elapsedNanos = System.nanoTime() - startedNanos;

        helper.assertValueEqual(
                dispatchWindow.attemptCount(),
                workerCount,
                "Every worker must own one independent physical-operation allowance");
        for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
            helper.assertValueEqual(
                    providers.get(providerIndex).pushCount(),
                    CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER,
                    "Provider " + providerIndex + " should receive its complete fair physical window");
        }
        List<TrinityDataCoreVirtualCpu> workers = host.getCpuPartitions().subList(1, workerCount + 1);
        for (TrinityDataCoreVirtualCpu worker : workers) {
            helper.assertValueEqual(
                    worker.getWaitingFor(output),
                    1L,
                    "Worker " + worker.number() + " must retain only its own waiting output");
        }
        Data_Energistics.LOGGER.info(
                "Trinity dispatch Phase 0 baseline: workers={}, providers={}, logicalCrafts={}, physicalCalls={}, tickNanos={}",
                workerCount,
                providerCount,
                workerCount,
                dispatchWindow.attemptCount(),
                elapsedNanos);
        host.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_omits_released_worker_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimeOmitsReleasedWorkerNbt(GameTestHelper helper) {
        BusyRuntimeFixture fixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        fixture.cpu().cancelJob();

        CompoundTag saved = new CompoundTag();
        fixture.runtime().writeToTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(
                saved.getList("partitions", 10).size(),
                0,
                "Released worker and reserved CPU 0 must not be serialized");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_persists_hidden_inventory_worker")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimePersistsHiddenInventoryWorker(GameTestHelper helper) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        seedStorage(grid.storage(), iron, 2L);
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        helper.assertTrue(
                reserveCpu.submitJob(grid, ingredientPlan(iron, 2L), IActionSource.empty(), null).successful(),
                "Inventory retention job should be accepted");
        TrinityDataCoreVirtualCpu worker = singleBusyWorker(host.getCraftingRuntime());

        grid.storage().setMaxAcceptedPerInsert(0L);
        worker.cancelJob();

        helper.assertValueEqual(host.getCpuPartitions().size(), 1, "Idle inventory worker must be hidden behind CPU 0");
        helper.assertValueEqual(worker.getStored(iron), 2L, "Rejected ingredients must remain in the hidden worker");
        CompoundTag saved = new CompoundTag();
        host.getCraftingRuntime().writeToTag(saved, helper.getLevel().registryAccess());
        ListTag partitions = saved.getList("partitions", 10);
        helper.assertValueEqual(partitions.size(), 1, "Hidden inventory worker must remain serialized");
        helper.assertValueEqual(partitions.getCompound(0).getInt("index"), 1, "Retained inventory must belong to worker CPU 1");
        CompoundTag logic = partitions.getCompound(0).getCompound("logic");
        helper.assertValueEqual(logic.getList("inventory", 10).size(), 1, "Retained worker inventory must be persisted");
        helper.assertFalse(logic.contains("job"), "Canceled inventory worker must not persist a completed job");

        grid.storage().setMaxAcceptedPerInsert(Long.MAX_VALUE);
        host.getCraftingRuntime().tick(
                grid.energyService(),
                grid.craftingService(),
                CraftingDispatchWindow.create());
        helper.assertValueEqual(worker.getStored(iron), 0L, "Hidden worker should retry inventory return while online");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_maximum_cpu_profile_dispatches_pattern")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void maximumCpuProfileDispatchesPattern(GameTestHelper helper) {
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        PendingPatternDetails pattern = new PendingPatternDetails(output);
        RecordingCraftingProvider provider = new RecordingCraftingProvider(pattern);
        TestGrid grid = new TestGrid();
        grid.setCraftingProvider(provider);
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution(
                "maximum",
                TrinityDataCoreCpuContribution.of(Long.MAX_VALUE, Integer.MAX_VALUE, 1));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        CraftingPlan plan = new CraftingPlan(
                new GenericStack(output, 1L),
                1L,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of(pattern, 1L));

        ICraftingSubmitResult result = reserveCpu.submitJob(grid, plan, IActionSource.empty(), null);
        helper.assertTrue(result.successful(), "Maximum CPU profile should accept a directly dispatched pattern job");
        TrinityDataCoreVirtualCpu cpu = singleBusyWorker(host.getCraftingRuntime());
        helper.assertValueEqual(
                cpu.getCoProcessors(),
                Integer.MAX_VALUE,
                "The dispatch test must exercise the full co-processor profile");

        cpu.tick(grid.energyService(), grid.craftingService(), CraftingDispatchWindow.create());

        helper.assertValueEqual(provider.pushCount(), 1, "Maximum CPU profile must push at least one pattern");
        helper.assertTrue(provider.pushedPattern() == pattern, "The provider must receive the submitted pattern task");
        helper.assertValueEqual(provider.pushedInputSlots(), 0, "The zero-input test pattern should dispatch intact");
        helper.assertValueEqual(cpu.getWaitingFor(output), 1L, "The dispatched output must enter CPU waiting state");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_runtime_rotates_worker_dispatch_priority")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimeRotatesWorkerDispatchPriority(GameTestHelper helper) {
        long taskCount = CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER * 2L;
        PendingPatternDetails firstPattern = new PendingPatternDetails(AEItemKey.of(Items.DIAMOND));
        PendingPatternDetails secondPattern = new PendingPatternDetails(AEItemKey.of(Items.EMERALD));
        SequencedCraftingProvider provider = new SequencedCraftingProvider(List.of(firstPattern, secondPattern));
        TestGrid grid = new TestGrid();
        grid.setCraftingProvider(provider);
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution(
                "fairness",
                TrinityDataCoreCpuContribution.of(4096L, Integer.MAX_VALUE, 2));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();

        helper.assertTrue(
                reserveCpu.submitJob(
                        grid,
                        patternPlan(firstPattern, taskCount),
                        IActionSource.empty(),
                        null)
                        .successful(),
                "First fairness worker job should be accepted");
        helper.assertTrue(
                reserveCpu.submitJob(
                        grid,
                        patternPlan(secondPattern, taskCount),
                        IActionSource.empty(),
                        null)
                        .successful(),
                "Second fairness worker job should be accepted");

        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        runtime.tick(grid.energyService(), grid.craftingService(), CraftingDispatchWindow.create());
        helper.assertValueEqual(
                provider.pushCount(firstPattern),
                (long) CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER,
                "First tick should give worker 1 the shared provider window");
        helper.assertValueEqual(
                provider.pushCount(secondPattern),
                0L,
                "First tick should leave worker 2 behind the exhausted shared provider window");

        runtime.tick(grid.energyService(), grid.craftingService(), CraftingDispatchWindow.create());
        helper.assertValueEqual(
                provider.pushCount(firstPattern),
                (long) CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER,
                "Second tick must not let worker 1 take the new provider window again");
        helper.assertValueEqual(
                provider.pushCount(secondPattern),
                (long) CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER,
                "Second tick should rotate the new provider window to worker 2");
        runtime.cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_coordinator_reports_worker_availability")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuCoordinatorReportsWorkerAvailability(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("capacity", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        TrinityDataCoreVirtualCpu coordinator = host.getCpuPartitions().getFirst();

        helper.assertTrue(coordinator.canAcceptJob(), "An empty coordinator must advertise one available worker");
        helper.assertTrue(
                coordinator.submitJob(grid, emptyJobPlan(), IActionSource.empty(), null).successful(),
                "The only worker slot should accept its first job");
        helper.assertFalse(coordinator.canAcceptJob(), "A full coordinator must not remain auto-selectable");

        host.getCraftingRuntime().cancelAllJobs();
        helper.assertTrue(coordinator.canAcceptJob(), "A releasable worker must make the coordinator available again");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_auto_selection_skips_full_runtime")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuAutoSelectionSkipsFullRuntime(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost fullHost = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        NetworkedTestHost availableHost = new NetworkedTestHost(helper.absolutePos(new BlockPos(3, 1, 1)), grid);
        for (NetworkedTestHost host : List.of(fullHost, availableHost)) {
            host.setLevel(helper.getLevel());
            host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
            host.setCpuContribution("selection", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        }
        helper.assertTrue(
                fullHost.getCpuPartitions()
                        .getFirst()
                        .submitJob(grid, emptyJobPlan(), IActionSource.empty(), null)
                        .successful(),
                "The first runtime must be full before auto-selection");
        TrinityCraftingRuntimeRegistry registry = (TrinityCraftingRuntimeRegistry) grid.craftingService();
        registry.publish(new RuntimeGridNode(grid), fullHost.getCraftingRuntime());
        registry.publish(new RuntimeGridNode(grid), availableHost.getCraftingRuntime());

        ICraftingSubmitResult result = grid.craftingService().submitJob(
                emptyJobPlan(),
                null,
                null,
                true,
                IActionSource.empty());

        helper.assertTrue(result.successful(),
                "Auto-selection should continue to the available Trinity runtime: " + result.errorCode());
        helper.assertValueEqual(fullHost.getCraftingRuntime().occupiedWorkerCount(), 1,
                "The full runtime must retain only its original worker");
        helper.assertValueEqual(availableHost.getCraftingRuntime().occupiedWorkerCount(), 1,
                "The second runtime must receive the auto-selected job");
        fullHost.getCraftingRuntime().cancelAllJobs();
        availableHost.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_auto_selection_reports_full_runtime")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuAutoSelectionReportsFullRuntime(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("selection_diagnostic", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        helper.assertTrue(
                host.getCpuPartitions()
                        .getFirst()
                        .submitJob(grid, emptyJobPlan(), IActionSource.empty(), null)
                        .successful(),
                "The only Trinity worker must be occupied before checking diagnostics");
        TrinityCraftingRuntimeRegistry registry = (TrinityCraftingRuntimeRegistry) grid.craftingService();
        registry.publish(new RuntimeGridNode(grid), host.getCraftingRuntime());

        ICraftingSubmitResult result = grid.craftingService().submitJob(
                emptyJobPlan(),
                null,
                null,
                true,
                IActionSource.empty());

        helper.assertValueEqual(
                result.errorCode(),
                CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND,
                "A full pure-Trinity grid must report an unsuitable CPU instead of no CPU");
        helper.assertValueEqual(
                result.errorDetail(),
                new UnsuitableCpus(0, 1, 0, 0),
                "The unsuitable diagnostic must identify the occupied Trinity coordinator");
        host.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_auto_selection_round_robins_equal_runtimes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuAutoSelectionRoundRobinsEqualRuntimes(GameTestHelper helper) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost firstHost = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        NetworkedTestHost secondHost = new NetworkedTestHost(helper.absolutePos(new BlockPos(3, 1, 1)), grid);
        for (NetworkedTestHost host : List.of(firstHost, secondHost)) {
            host.setLevel(helper.getLevel());
            host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
            host.setCpuContribution("selection_rr", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        }
        TrinityCraftingRuntimeRegistry registry = (TrinityCraftingRuntimeRegistry) grid.craftingService();
        registry.publish(new RuntimeGridNode(grid), firstHost.getCraftingRuntime());
        registry.publish(new RuntimeGridNode(grid), secondHost.getCraftingRuntime());

        ICraftingSubmitResult firstResult = grid.craftingService().submitJob(
                emptyJobPlan(),
                null,
                null,
                true,
                IActionSource.empty());
        helper.assertTrue(firstResult.successful(),
                "First equal-runtime auto-selection should succeed: " + firstResult.errorCode());
        int firstHostInitialJobs = firstHost.getCraftingRuntime().occupiedWorkerCount();
        int secondHostInitialJobs = secondHost.getCraftingRuntime().occupiedWorkerCount();
        helper.assertValueEqual(
                firstHostInitialJobs + secondHostInitialJobs,
                1,
                "Exactly one equal runtime should receive the first job");

        firstHost.getCraftingRuntime().cancelAllJobs();
        secondHost.getCraftingRuntime().cancelAllJobs();
        ICraftingSubmitResult secondResult = grid.craftingService().submitJob(
                emptyJobPlan(),
                null,
                null,
                true,
                IActionSource.empty());

        helper.assertTrue(secondResult.successful(),
                "Second equal-runtime auto-selection should succeed: " + secondResult.errorCode());
        helper.assertValueEqual(
                firstHost.getCraftingRuntime().occupiedWorkerCount(),
                secondHostInitialJobs,
                "The first host should receive the opposite allocation after cursor advancement");
        helper.assertValueEqual(
                secondHost.getCraftingRuntime().occupiedWorkerCount(),
                firstHostInitialJobs,
                "The second host should receive the opposite allocation after cursor advancement");
        firstHost.getCraftingRuntime().cancelAllJobs();
        secondHost.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_preserves_ae2_provider_round_robin")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPreservesAe2ProviderRoundRobin(GameTestHelper helper) {
        PendingPatternDetails pattern = new PendingPatternDetails(AEItemKey.of(Items.DIAMOND));
        RecordingCraftingProvider firstProvider = new RecordingCraftingProvider(pattern);
        RecordingCraftingProvider secondProvider = new RecordingCraftingProvider(pattern);
        TestGrid grid = new TestGrid();
        grid.setCraftingProviders(List.of(firstProvider, secondProvider));
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("provider_rr", TrinityDataCoreCpuContribution.of(1024L, 1, 1));
        TrinityDataCoreVirtualCpu coordinator = host.getCpuPartitions().getFirst();
        helper.assertTrue(
                coordinator.submitJob(grid, patternPlan(pattern, 2L), IActionSource.empty(), null).successful(),
                "Provider round-robin job should be accepted");

        TrinityDataCoreVirtualCpu worker = singleBusyWorker(host.getCraftingRuntime());
        worker.tick(grid.energyService(), grid.craftingService(), CraftingDispatchWindow.create());

        helper.assertValueEqual(firstProvider.pushCount(), 1,
                "The first physical submission should use the first AE2 provider");
        helper.assertValueEqual(secondProvider.pushCount(), 1,
                "Stopping the cyclic iterator at acceptance must expose the second provider next");
        host.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_no_capacity_cache_is_pattern_scoped")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuNoCapacityCacheIsPatternScoped(GameTestHelper helper) {
        PendingPatternDetails blockedPattern = new PendingPatternDetails(AEItemKey.of(Items.DIAMOND));
        PendingPatternDetails acceptedPattern = new PendingPatternDetails(AEItemKey.of(Items.EMERALD));
        PatternScopedCapacityProvider provider = new PatternScopedCapacityProvider(blockedPattern, acceptedPattern);
        TestGrid grid = new TestGrid();
        grid.setCraftingProvider(provider);
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("pattern_cache", TrinityDataCoreCpuContribution.of(2048L, 0, 2));
        TrinityDataCoreVirtualCpu coordinator = host.getCpuPartitions().getFirst();
        helper.assertTrue(
                coordinator.submitJob(grid, patternPlan(blockedPattern, 1L), IActionSource.empty(), null).successful(),
                "Blocked-pattern worker should be allocated");
        helper.assertTrue(
                coordinator.submitJob(grid, patternPlan(acceptedPattern, 1L), IActionSource.empty(), null).successful(),
                "Accepted-pattern worker should be allocated");

        host.getCraftingRuntime().tick(
                grid.energyService(),
                grid.craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(provider.blockedPrepareCount(), 1,
                "The no-capacity pattern should be prepared once");
        helper.assertValueEqual(provider.acceptedCommitCount(), 1,
                "A different pattern on the same provider must remain eligible in the shared window");
        host.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_multi_runtime_worker_rotation_avoids_phase_lock")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuMultiRuntimeWorkerRotationAvoidsPhaseLock(GameTestHelper helper) {
        long taskCount = CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER * 4L;
        PendingPatternDetails firstA = new PendingPatternDetails(AEItemKey.of(Items.DIAMOND));
        PendingPatternDetails secondA = new PendingPatternDetails(AEItemKey.of(Items.EMERALD));
        PendingPatternDetails firstB = new PendingPatternDetails(AEItemKey.of(Items.GOLD_INGOT));
        PendingPatternDetails secondB = new PendingPatternDetails(AEItemKey.of(Items.IRON_INGOT));
        SequencedCraftingProvider provider = new SequencedCraftingProvider(List.of(firstA, secondA, firstB, secondB));
        TestGrid grid = new TestGrid();
        grid.setCraftingProvider(provider);
        NetworkedTestHost hostA = new NetworkedTestHost(helper.absolutePos(new BlockPos(1, 1, 1)), grid);
        NetworkedTestHost hostB = new NetworkedTestHost(helper.absolutePos(new BlockPos(3, 1, 1)), grid);
        for (NetworkedTestHost host : List.of(hostA, hostB)) {
            host.setLevel(helper.getLevel());
            host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
            host.setCpuContribution("phase_lock", TrinityDataCoreCpuContribution.of(4096L, Integer.MAX_VALUE, 2));
        }
        TrinityDataCoreVirtualCpu coordinatorA = hostA.getCpuPartitions().getFirst();
        TrinityDataCoreVirtualCpu coordinatorB = hostB.getCpuPartitions().getFirst();
        helper.assertTrue(
                coordinatorA.submitJob(grid, patternPlan(firstA, taskCount), IActionSource.empty(), null).successful(),
                "Runtime A worker 1 should be allocated");
        helper.assertTrue(
                coordinatorA.submitJob(grid, patternPlan(secondA, taskCount), IActionSource.empty(), null).successful(),
                "Runtime A worker 2 should be allocated");
        helper.assertTrue(
                coordinatorB.submitJob(grid, patternPlan(firstB, taskCount), IActionSource.empty(), null).successful(),
                "Runtime B worker 1 should be allocated");
        helper.assertTrue(
                coordinatorB.submitJob(grid, patternPlan(secondB, taskCount), IActionSource.empty(), null).successful(),
                "Runtime B worker 2 should be allocated");

        TrinityDataCoreCraftingRuntime runtimeA = hostA.getCraftingRuntime();
        TrinityDataCoreCraftingRuntime runtimeB = hostB.getCraftingRuntime();
        tickRuntimes(grid, CraftingDispatchWindow.create(), runtimeA, runtimeB);
        tickRuntimes(grid, CraftingDispatchWindow.create(), runtimeB, runtimeA);
        tickRuntimes(grid, CraftingDispatchWindow.create(), runtimeA, runtimeB);
        tickRuntimes(grid, CraftingDispatchWindow.create(), runtimeB, runtimeA);

        for (IPatternDetails pattern : List.of(firstA, secondA, firstB, secondB)) {
            helper.assertValueEqual(
                    provider.pushCount(pattern),
                    (long) CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER,
                    "Every runtime worker must receive one complete physical window without phase-lock starvation");
        }
        runtimeA.cancelAllJobs();
        runtimeB.cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_dispatches_one_counted_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuDispatchesOneCountedBatch(GameTestHelper helper) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.ACCEPT);

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "128 logical crafts must cross the provider boundary once");
        helper.assertValueEqual(fixture.provider().lastBatchCount(), COUNTED_BATCH_SIZE,
                "The single counted provider call must retain all logical crafts");
        helper.assertValueEqual(fixture.provider().lastPrototypeAmount(), 1L,
                "The counted provider call must receive one per-craft input prototype");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                "The accepted counted batch must transfer all planned inputs");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                "The accepted counted batch must account for every expected output");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), 0.0D,
                "The accepted counted batch must consume energy for every logical craft");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_limits_counted_batch_by_provider_capacity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuLimitsCountedBatchByProviderCapacity(GameTestHelper helper) {
        long providerCapacity = 32L;
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.ACCEPT);
        fixture.provider().setMaximumAdmissionCount(providerCapacity);

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "A capacity-limited provider must receive one physical submission");
        helper.assertValueEqual(fixture.provider().lastBatchCount(), providerCapacity,
                "The admission must reduce the logical batch to target capacity");
        helper.assertValueEqual(
                fixture.cpu().getStored(fixture.input()),
                COUNTED_BATCH_SIZE - providerCapacity,
                "Inputs beyond provider capacity must remain owned by the CPU");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), providerCapacity,
                "Only admitted crafts may enter output waiting state");
        helper.assertValueEqual(
                fixture.grid().energyService().getStoredPower(),
                (double) (COUNTED_BATCH_SIZE - providerCapacity),
                "Only admitted crafts may consume energy");
        helper.assertTrue(fixture.cpu().isBusy(), "The capacity-limited task remainder must stay available");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_rejects_zero_capacity_and_invalid_admissions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRejectsZeroCapacityAndInvalidAdmissions(GameTestHelper helper) {
        CountedBatchFixture noCapacity = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.ACCEPT);
        noCapacity.provider().setNoCapacity(true);
        CraftingDispatchWindow sharedWindow = CraftingDispatchWindow.create();

        noCapacity.cpu().tick(
                noCapacity.grid().energyService(),
                noCapacity.grid().craftingService(),
                sharedWindow);
        noCapacity.cpu().tick(
                noCapacity.grid().energyService(),
                noCapacity.grid().craftingService(),
                sharedWindow);

        assertUncommittedAdmissionState(helper, noCapacity, "Zero-capacity");
        helper.assertValueEqual(noCapacity.provider().prepareCount(), 1,
                "A zero-capacity provider must be prepared only once in one dispatch window");

        CountedBatchFixture invalid = countedBatchFixture(
                helper,
                new BlockPos(3, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.ACCEPT);
        invalid.provider().setInvalidAdmissionCount(0L);

        invalid.cpu().tick(
                invalid.grid().energyService(),
                invalid.grid().craftingService(),
                CraftingDispatchWindow.create());

        assertUncommittedAdmissionState(helper, invalid, "Invalid-count");
        helper.assertValueEqual(invalid.provider().prepareCount(), 1,
                "An invalid admission must fail on its first preparation");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_limits_counted_batch_by_energy")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuLimitsCountedBatchByEnergy(GameTestHelper helper) {
        long affordableCrafts = 8L;
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                affordableCrafts,
                BatchPushOutcome.ACCEPT);

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "An exhausted energy source must stop dispatch after one reduced batch");
        helper.assertValueEqual(fixture.provider().lastBatchCount(), affordableCrafts,
                "The counted batch must shrink to the number of affordable crafts");
        helper.assertValueEqual(
                fixture.cpu().getStored(fixture.input()),
                COUNTED_BATCH_SIZE - affordableCrafts,
                "Inputs outside the affordable batch must remain owned by the CPU");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), affordableCrafts,
                "Only the affordable crafts may enter output waiting state");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), 0.0D,
                "The reduced batch must consume exactly the available energy");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_falls_back_after_counted_provider_rejection")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuFallsBackAfterCountedProviderRejection(GameTestHelper helper) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.REJECT);
        RecordingBatchCraftingProvider fallback = new RecordingBatchCraftingProvider(
                fixture.provider().pattern(),
                fixture.input(),
                BatchPushOutcome.ACCEPT);
        fixture.grid().setCraftingProviders(List.of(fixture.provider(), fallback));

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "The first provider must receive one rejected physical attempt");
        helper.assertValueEqual(fallback.batchPushCount(), 1,
                "The second provider must receive the same logical batch in the same tick");
        helper.assertValueEqual(fallback.lastBatchCount(), COUNTED_BATCH_SIZE,
                "Fallback dispatch must preserve the complete logical batch");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                "Fallback acceptance must transfer every prepared input exactly once");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                "Fallback acceptance must record every expected output exactly once");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), 0.0D,
                "Fallback acceptance must retain one complete batch energy charge");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_falls_back_after_counted_provider_exception")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuFallsBackAfterCountedProviderException(GameTestHelper helper) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.THROW);
        RecordingBatchCraftingProvider fallback = new RecordingBatchCraftingProvider(
                fixture.provider().pattern(),
                fixture.input(),
                BatchPushOutcome.ACCEPT);
        fixture.grid().setCraftingProviders(List.of(fixture.provider(), fallback));

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "The throwing provider must consume one physical attempt");
        helper.assertValueEqual(fallback.batchPushCount(), 1,
                "A provider exception before ownership transfer must not abort fallback dispatch");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                "Fallback acceptance must transfer the restored batch exactly once");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                "Fallback acceptance must commit the complete expected output count");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), 0.0D,
                "The failed provider charge must roll back before the fallback charge commits");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_isolates_provider_busy_exception")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuIsolatesProviderBusyException(GameTestHelper helper) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                new BlockPos(1, 1, 1),
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.ACCEPT);
        BusyThrowingCraftingProvider throwingProvider = new BusyThrowingCraftingProvider(fixture.provider().pattern());
        fixture.grid().setCraftingProviders(List.of(throwingProvider, fixture.provider()));

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(throwingProvider.busyCheckCount(), 1,
                "The faulty provider busy state should be checked once");
        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "A busy-state exception must not prevent the next provider from accepting the batch");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                "The accepted fallback must receive every input exactly once");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                "The accepted fallback must retain complete waiting-output accounting");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_rolls_back_uncommitted_counted_batches")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRollsBackUncommittedCountedBatches(GameTestHelper helper) {
        assertCountedBatchRollback(helper, new BlockPos(1, 1, 1), BatchPushOutcome.REJECT);
        assertCountedBatchRollback(helper, new BlockPos(3, 1, 1), BatchPushOutcome.THROW);
        assertModulatedEnergyShortfallRollback(helper, new BlockPos(1, 3, 1));
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_preserves_transferred_counted_batch_ownership")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPreservesTransferredCountedBatchOwnership(GameTestHelper helper) {
        assertTransferredCountedBatchOwnership(
                helper,
                new BlockPos(1, 1, 1),
                BatchPushOutcome.CONSUME_AND_REJECT);
        assertTransferredCountedBatchOwnership(
                helper,
                new BlockPos(3, 1, 1),
                BatchPushOutcome.CONSUME_AND_THROW);
        assertTransferredCountedBatchOwnership(
                helper,
                new BlockPos(1, 3, 1),
                BatchPushOutcome.TRANSFER_AND_THROW);
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
        fixture.runtime().tick(null, null, CraftingDispatchWindow.create());

        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Paused runtime must not process or discard the canceled job");

        fixture.runtime().setPaused(false);
        fixture.runtime().tick(null, null, CraftingDispatchWindow.create());

        helper.assertFalse(fixture.runtime().hasBusyJobs(), "Resumed runtime should process the canceled job on its next tick");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_requested_amount_saturates_across_workers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void requestedAmountSaturatesAcrossWorkers(GameTestHelper helper) {
        helper.assertValueEqual(
                LongAmountMath.saturatingAddNonNegative(Long.MAX_VALUE, 1L),
                Long.MAX_VALUE,
                "MAX plus one must saturate");
        helper.assertValueEqual(
                LongAmountMath.saturatingAddNonNegative(Long.MAX_VALUE, Long.MAX_VALUE),
                Long.MAX_VALUE,
                "Multiple maximum amounts must saturate");
        helper.assertValueEqual(
                LongAmountMath.saturatingAddNonNegative(20L, 22L),
                42L,
                "Representable amounts must retain their exact sum");
        helper.assertValueEqual(
                LongAmountMath.saturatingMultiplyNonNegative(Long.MAX_VALUE, 2L),
                Long.MAX_VALUE,
                "Overflowing products must saturate");
        helper.assertValueEqual(
                LongAmountMath.saturatingMultiplyNonNegative(6L, 7L),
                42L,
                "Representable products must remain exact");

        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        OutputRuntimeFixture saturated = outputRuntime(helper, new BlockPos(1, 1, 1), 3);
        submitWaitingOutputJob(helper, saturated, diamond, Long.MAX_VALUE);
        submitWaitingOutputJob(helper, saturated, diamond, Long.MAX_VALUE);
        submitWaitingOutputJob(helper, saturated, diamond, 1L);
        helper.assertValueEqual(
                saturated.runtime().getRequestedAmount(diamond),
                Long.MAX_VALUE,
                "Requested amount across retained workers must saturate");

        OutputRuntimeFixture exact = outputRuntime(helper, new BlockPos(3, 1, 1), 2);
        submitWaitingOutputJob(helper, exact, diamond, 20L);
        submitWaitingOutputJob(helper, exact, diamond, 22L);
        helper.assertValueEqual(
                exact.runtime().getRequestedAmount(diamond),
                42L,
                "Representable requested amount across retained workers must remain exact");
        saturated.runtime().cancelAllJobs();
        exact.runtime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_waits_for_all_requested_outputs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuWaitsForAllRequestedOutputs(GameTestHelper helper) {
        OutputRuntimeFixture fixture = outputRuntime(helper, new BlockPos(1, 1, 1));
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        AEItemKey bucket = AEItemKey.of(Items.BUCKET);
        TestRequester requester = new TestRequester(Long.MAX_VALUE);
        TrinityDataCoreVirtualCpu cpu = submitOutputJob(
                fixture,
                requester,
                outputPlan(new GenericStack(diamond, 1L), new GenericStack(bucket, 1L)));

        helper.assertValueEqual(
                cpu.insert(diamond, 1L, Actionable.MODULATE),
                1L,
                "Requester should accept the final output");
        helper.assertTrue(cpu.isBusy(), "CPU must remain busy while a container output is still requested");
        helper.assertValueEqual(
                cpu.getWaitingFor(bucket),
                1L,
                "Container output must remain requested after the final output arrives");

        helper.assertValueEqual(
                cpu.insert(bucket, 1L, Actionable.MODULATE),
                1L,
                "CPU should accept the remaining container output");
        helper.assertFalse(cpu.isBusy(), "CPU should finish after every requested output has arrived");
        helper.assertValueEqual(
                cpu.getStored(bucket),
                1L,
                "Container output should be retained in CPU inventory until network storage accepts it");
        helper.assertValueEqual(requester.jobStateChanges(), 1, "Requester should be notified exactly once on completion");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_accounts_only_accepted_final_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuAccountsOnlyAcceptedFinalOutput(GameTestHelper helper) {
        OutputRuntimeFixture fixture = outputRuntime(helper, new BlockPos(1, 1, 1));
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        TestRequester requester = new TestRequester(0L);
        TrinityDataCoreVirtualCpu cpu = submitOutputJob(
                fixture,
                requester,
                outputPlan(new GenericStack(diamond, 5L)));
        KeyCounter statusChanges = new KeyCounter();
        cpu.addListener(what -> statusChanges.add(what, 1L));

        helper.assertValueEqual(
                cpu.insert(diamond, 5L, Actionable.MODULATE),
                0L,
                "A requester with no capacity should reject the final output");
        helper.assertValueEqual(
                cpu.getWaitingFor(diamond),
                5L,
                "Rejected final output must not mutate the requested amount");
        helper.assertTrue(cpu.isBusy(), "Rejected final output must keep the CPU busy");
        helper.assertValueEqual(requester.jobStateChanges(), 0, "Rejected output must not complete the job");
        helper.assertValueEqual(statusChanges.get(diamond), 0L, "Rejected output must not notify CPU status listeners");

        requester.setMaxAccepted(2L, 1L);
        helper.assertValueEqual(
                cpu.insert(diamond, 5L, Actionable.SIMULATE),
                2L,
                "Simulation should report the requester's partial capacity");
        helper.assertValueEqual(
                cpu.getWaitingFor(diamond),
                5L,
                "Simulation must not mutate the requested amount");
        helper.assertValueEqual(statusChanges.get(diamond), 0L, "Simulation must not notify CPU status listeners");
        helper.assertValueEqual(
                cpu.insert(diamond, 5L, Actionable.MODULATE),
                1L,
                "Modulation should report the amount actually accepted by the requester");

        helper.assertTrue(cpu.isBusy(), "Partially delivered final output must keep the CPU busy");
        helper.assertValueEqual(
                cpu.getWaitingFor(diamond),
                4L,
                "Only accepted final output may be removed from waiting state");
        CompoundTag jobTag = cpu
                .logic()
                .writeToTag(helper.getLevel().registryAccess())
                .getCompound("job");
        helper.assertValueEqual(
                jobTag.getLong("remaining_amount"),
                4L,
                "Remaining final output must decrease by the accepted amount only");
        helper.assertValueEqual(
                jobTag.getCompound("time_tracker")
                        .getCompound("completed_work")
                        .getLong(diamond.getType().getId().toString()),
                1L,
                "Progress tracking must count only accepted final output");
        helper.assertValueEqual(
                statusChanges.get(diamond),
                1L,
                "Actually accepted output must notify CPU status listeners exactly once");
        helper.assertValueEqual(requester.jobStateChanges(), 0, "Partial output must not complete the job");

        requester.setMaxAccepted(4L);
        helper.assertValueEqual(
                cpu.insert(diamond, 4L, Actionable.MODULATE),
                4L,
                "Requester should accept the remaining final output");
        helper.assertFalse(cpu.isBusy(), "CPU should finish after the final remainder is delivered");
        helper.assertValueEqual(requester.jobStateChanges(), 1, "Requester should be notified exactly once on completion");
        helper.assertValueEqual(
                cpu.insert(diamond, 1L, Actionable.MODULATE),
                0L,
                "A completed CPU must reject later output");
        helper.assertValueEqual(requester.jobStateChanges(), 1, "Completion notification must not repeat");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_rejects_invalid_requester_acceptance")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRejectsInvalidRequesterAcceptance(GameTestHelper helper) {
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        OutputRuntimeFixture negativeFixture = outputRuntime(helper, new BlockPos(1, 1, 1));
        TestRequester negativeRequester = new TestRequester(1L);
        negativeRequester.returnExactly(-1L, -1L);
        TrinityDataCoreVirtualCpu negativeCpu = submitOutputJob(
                negativeFixture,
                negativeRequester,
                outputPlan(new GenericStack(diamond, 1L)));

        IllegalStateException negativeFailure = assertThrows(
                helper,
                IllegalStateException.class,
                () -> negativeCpu.insert(diamond, 1L, Actionable.SIMULATE),
                "Negative requester acceptance must fail fast");
        assertAcceptanceFailureMessage(helper, negativeFailure, diamond, Actionable.SIMULATE, 1L, -1L);
        helper.assertValueEqual(
                negativeCpu.getWaitingFor(diamond),
                1L,
                "Rejected simulation result must not mutate waiting state");
        helper.assertTrue(negativeCpu.isBusy(), "Rejected simulation result must retain the CPU job");

        OutputRuntimeFixture excessFixture = outputRuntime(helper, new BlockPos(3, 1, 1));
        TestRequester excessRequester = new TestRequester(1L);
        excessRequester.returnExactly(2L, 2L);
        TrinityDataCoreVirtualCpu excessCpu = submitOutputJob(
                excessFixture,
                excessRequester,
                outputPlan(new GenericStack(diamond, 1L)));

        IllegalStateException excessFailure = assertThrows(
                helper,
                IllegalStateException.class,
                () -> excessCpu.insert(diamond, 1L, Actionable.MODULATE),
                "Excess requester acceptance must fail fast");
        assertAcceptanceFailureMessage(helper, excessFailure, diamond, Actionable.MODULATE, 1L, 2L);
        helper.assertValueEqual(
                excessCpu.getWaitingFor(diamond),
                1L,
                "Rejected modulation result must not mutate waiting state");
        helper.assertTrue(excessCpu.isBusy(), "Rejected modulation result must retain the CPU job");
        helper.assertValueEqual(excessRequester.jobStateChanges(), 0, "Rejected result must not complete the CPU job");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_cpu_does_not_finish_with_pending_tasks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuDoesNotFinishWithPendingTasks(GameTestHelper helper) {
        OutputRuntimeFixture fixture = outputRuntime(helper, new BlockPos(1, 1, 1));
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        TestRequester requester = new TestRequester(Long.MAX_VALUE);
        KeyCounter emittedItems = new KeyCounter();
        emittedItems.add(diamond, 1L);
        CraftingPlan plan = new CraftingPlan(
                new GenericStack(diamond, 1L),
                1L,
                false,
                false,
                new KeyCounter(),
                emittedItems,
                new KeyCounter(),
                Map.of(new PendingPatternDetails(diamond), 1L));
        TrinityDataCoreVirtualCpu cpu = submitOutputJob(fixture, requester, plan);

        helper.assertValueEqual(
                cpu.insert(diamond, 1L, Actionable.MODULATE),
                1L,
                "Requester should accept the final output");
        helper.assertValueEqual(cpu.getWaitingFor(diamond), 0L, "Final output should leave no waiting amount");
        helper.assertTrue(cpu.isBusy(), "Pending pattern tasks must prevent job completion");
        helper.assertValueEqual(requester.jobStateChanges(), 0, "Pending tasks must suppress completion notification");
        CompoundTag jobTag = cpu
                .logic()
                .writeToTag(helper.getLevel().registryAccess())
                .getCompound("job");
        helper.assertValueEqual(jobTag.getLong("remaining_amount"), 0L, "Final output should be fully delivered");
        helper.assertValueEqual(jobTag.getList("tasks", 10).size(), 1, "The undispatched pattern task must remain queued");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_standalone_output_returns_to_network")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void standaloneOutputReturnsToNetwork(GameTestHelper helper) {
        OutputRuntimeFixture fixture = outputRuntime(helper, new BlockPos(1, 1, 1));
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        fixture.grid().storage().setMaxAcceptedPerInsert(2L);
        TrinityDataCoreVirtualCpu cpu = submitOutputJob(
                fixture,
                null,
                outputPlan(new GenericStack(diamond, 4L)));

        helper.assertValueEqual(
                cpu.insert(diamond, 4L, Actionable.SIMULATE),
                4L,
                "Standalone CPU should advertise internal capacity for its final output");
        helper.assertValueEqual(
                cpu.getWaitingFor(diamond),
                4L,
                "Standalone simulation must not mutate waiting state");
        helper.assertValueEqual(
                cpu.insert(diamond, 4L, Actionable.MODULATE),
                4L,
                "Standalone CPU should accept its final output into internal inventory");

        helper.assertFalse(cpu.isBusy(), "Standalone job should finish after its output is accepted");
        helper.assertValueEqual(cpu.getWaitingFor(diamond), 0L, "Finished job should have no waiting output");
        helper.assertValueEqual(
                fixture.grid().storage().getStored(diamond),
                2L,
                "Job completion should return the accepted portion to network storage");
        helper.assertValueEqual(
                cpu.getStored(diamond),
                2L,
                "Network remainder must stay in CPU inventory for retry");

        fixture.grid().storage().setMaxAcceptedPerInsert(Long.MAX_VALUE);
        cpu.tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());
        helper.assertValueEqual(cpu.getStored(diamond), 0L, "Idle CPU should retry returning retained output");
        helper.assertValueEqual(
                fixture.grid().storage().getStored(diamond),
                4L,
                "Retried standalone output should fully return to network storage");
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

        helper.assertValueEqual(fixture.runtime().partitions().size(), 2,
                "Recovered structure should publish reserved CPU 0 and its busy worker");
        helper.assertTrue(
                fixture.runtime().partitions().get(1) == originalCpu,
                "Recovered structure should reuse the worker that owns the paused job");
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

        TestHost restoredHost = new TestHost(helper.absolutePos(new BlockPos(2, 1, 1)), new TestGrid());
        restoredHost.setLevel(helper.getLevel());
        restoredHost.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        TrinityDataCoreCraftingRuntime restored = restoredHost.getCraftingRuntime();
        restored.readFromTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(restored.partitions().size(), 0, "Inactive persisted CPU must remain withdrawn after reload");
        helper.assertTrue(restored.hasBusyJobs(), "Inactive persisted CPU must restore its paused job");

        restored.setMainStructureFormed(true);
        restored.setContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));

        helper.assertValueEqual(restored.partitions().size(), 2,
                "Restored runtime should publish reserved CPU 0 and its recovered worker");
        helper.assertTrue(restored.partitions().get(1).isBusy(), "Republished worker should still own the restored job");
        restored.cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_host_root_nbt_round_trips_active_job")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostRootNbtRoundTripsActiveJob(GameTestHelper helper) {
        BusyRuntimeFixture fixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        UUID storageId = fixture.host().getStorageId();
        UUID hostId = fixture.host().getHostId();
        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Source host should own an active CPU job before saving");

        CompoundTag saved = new CompoundTag();
        fixture.host().saveAdditional(saved, helper.getLevel().registryAccess());

        TestHost restored = new TestHost(helper.absolutePos(new BlockPos(3, 1, 1)), new TestGrid());
        restored.setLevel(helper.getLevel());
        restored.loadTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(restored.getStorageId(), storageId, "Root NBT should preserve the storage UUID");
        helper.assertValueEqual(restored.getHostId(), hostId, "Root NBT should preserve the routing UUID");
        helper.assertTrue(restored.isStructureFormed(), "Root NBT should preserve the main structure state");
        helper.assertTrue(restored.isCpuStructureFormed(), "Root NBT should preserve the CPU structure state");
        helper.assertTrue(restored.isCraftingStructureFormed(), "Root NBT should preserve the crafting structure state");
        helper.assertValueEqual(restored.getCpuPartitions().size(), 2,
                "Root NBT should restore reserved CPU 0 and the active worker");
        helper.assertTrue(restored.getCraftingRuntime().hasBusyJobs(), "Root NBT should restore the active CPU job");
        helper.assertTrue(restored.getCpuPartitions().get(1).isBusy(), "Restored worker should own the active job");

        fixture.runtime().cancelAllJobs();
        restored.getCraftingRuntime().cancelAllJobs();
        helper.succeed();
    }

    @TestHolder("trinity_data_core_main_structure_failure_retains_cpu_child_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mainStructureFailureRetainsCpuChildState(GameTestHelper helper) {
        BlockPos hostPos = new BlockPos(1, 1, 1);
        helper.setBlock(hostPos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(hostPos));
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", hostPos);
            return;
        }
        host.loadTag(formedCraftingProfileTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 1, 1));

        helper.assertValueEqual(
                host.getCraftingRuntime().profile().storageBytes(),
                1024L,
                "CPU child contribution should be retained before recheck");
        helper.assertTrue(host.isCraftingStructureFormed(), "Crafting child structure should be active before recheck");
        helper.assertValueEqual(
                host.getCraftingPatternCapacity(),
                704,
                "Crafting child profile should be active before recheck");
        host.serverTick();

        helper.assertFalse(host.isStructureFormed(), "Missing main structure should make the host unformed");
        helper.assertValueEqual(
                host.getCraftingRuntime().profile().storageBytes(),
                1024L,
                "Main structure failure should retain the last valid CPU contribution");
        helper.assertValueEqual(
                host.getCpuPartitions().size(),
                0,
                "Main structure failure should withdraw CPU partitions from AE2 while paused");
        helper.assertFalse(host.isCraftingStructureFormed(), "Main structure failure should withdraw crafting child status");
        helper.assertValueEqual(
                host.getCraftingPatternCapacity(),
                704,
                "Main structure failure should retain the last valid crafting profile");

        host.getCraftingRuntime().setMainStructureFormed(true);

        helper.assertValueEqual(
                host.getCraftingRuntime().profile().storageBytes(),
                1024L,
                "Manually restoring the main runtime flag must preserve the retained CPU contribution");
        helper.assertValueEqual(
                host.getCpuPartitions().size(),
                0,
                "CPU publication must remain withdrawn until the CPU child status is restored");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_crafting_profile_round_trips_through_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void craftingProfileRoundTripsThroughNbt(GameTestHelper helper) {
        TrinityDataCoreBlockEntity original = trinityDataCore(false);
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
        TrinityDataCoreBlockEntity loaded = trinityDataCore(false);
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
        TrinityDataCoreBlockEntity host = trinityDataCore(true);

        CompoundTag runtimeTag = new CompoundTag();
        runtimeTag.putInt("schema_version", LEGACY_RUNTIME_SCHEMA_VERSION);
        runtimeTag.put("contributions", new ListTag());
        ListTag partitionsTag = new ListTag();
        CompoundTag partitionTag = new CompoundTag();
        partitionTag.putInt("index", 0);
        partitionTag.putInt("partition_count", 1);
        partitionTag.putLong("storage_bytes", 1024L);
        partitionTag.putInt("co_processors", 0);
        partitionTag.putString("selection_mode", CpuSelectionMode.ANY.name());
        CompoundTag logicTag = new CompoundTag();
        logicTag.putInt("schema_version", CPU_LOGIC_SCHEMA_VERSION);
        logicTag.put("inventory", new ListTag());
        CompoundTag pendingJob = new CompoundTag();
        pendingJob.putString("pending_marker", "preserve_without_level");
        logicTag.put("job", pendingJob);
        partitionTag.put("logic", logicTag);
        partitionsTag.add(partitionTag);
        runtimeTag.put("partitions", partitionsTag);

        host.getCraftingRuntime().readFromTag(
                runtimeTag,
                HolderLookup.Provider.create(Stream.empty()));
        helper.assertValueEqual(
                host.getCpuPartitions().size(),
                0,
                "Pending partition logic should not create CPU partitions without a child contribution");
        helper.assertTrue(host.getCraftingRuntime().hasBusyJobs(), "Pending legacy job should remain retained before level binding");

        CompoundTag resaved = new CompoundTag();
        host.getCraftingRuntime().writeToTag(resaved, HolderLookup.Provider.create(Stream.empty()));
        helper.assertValueEqual(
                resaved.getInt("schema_version"),
                RUNTIME_SCHEMA_VERSION,
                "Pending legacy runtime should be normalized to schema 2 when resaved");
        ListTag resavedPartitions = resaved.getList("partitions", 10);
        helper.assertValueEqual(resavedPartitions.size(), 1, "Pending worker must survive a save before level binding");
        helper.assertValueEqual(
                resavedPartitions.getCompound(0).getInt("index"),
                1,
                "Legacy partition index 0 must map to worker CPU 1");
        helper.assertTrue(
                resavedPartitions.getCompound(0).getCompound("logic").equals(logicTag),
                "Pending raw CPU logic must be forwarded without decoding or mutation");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_host_rejects_unsupported_schema")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostRejectsUnsupportedSchema(GameTestHelper helper) {
        BusyRuntimeFixture unsupportedFixture = busyRuntime(helper, new BlockPos(1, 1, 1));
        UUID unsupportedStorageId = unsupportedFixture.host().getStorageId();
        UUID unsupportedHostId = unsupportedFixture.host().getHostId();
        CompoundTag unsupportedTag = formedTrinityTag();
        unsupportedTag.putInt("schema_version", HOST_SCHEMA_VERSION + 1);
        unsupportedTag.putUUID("trinity_data_core_storage_id", unsupportedStorageId);
        unsupportedTag.putUUID("trinity_data_core_host_id", unsupportedHostId);

        unsupportedFixture.host().loadTag(unsupportedTag, helper.getLevel().registryAccess());

        assertRejectedHostState(
                helper,
                unsupportedFixture,
                unsupportedStorageId,
                unsupportedHostId,
                "Unsupported root NBT schema");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_id_round_trips_through_item_and_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageIdRoundTripsThroughItemAndNbt(GameTestHelper helper) {
        TrinityDataCoreBlockEntity original = trinityDataCore(false);
        ItemStack stack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        original.saveIdentityToItem(stack);
        UUID storageId = stack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID);

        TrinityDataCoreBlockEntity placed = trinityDataCore(false);
        placed.restoreIdentityFromItem(stack);
        ItemStack placedStack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        placed.saveIdentityToItem(placedStack);
        helper.assertValueEqual(
                placedStack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                "Placed host should restore the storage id from the item component");

        CompoundTag saved = new CompoundTag();
        placed.saveAdditional(saved, HolderLookup.Provider.create(Stream.empty()));
        TrinityDataCoreBlockEntity loaded = trinityDataCore(false);
        loaded.loadTag(saved, HolderLookup.Provider.create(Stream.empty()));
        ItemStack loadedStack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        loaded.saveIdentityToItem(loadedStack);
        helper.assertValueEqual(
                loadedStack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                "Loaded host should keep the storage id from block entity NBT");
        helper.succeed();
    }

    private static TrinityDataCoreBlockEntity trinityDataCore(boolean formed) {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(
                BlockPos.ZERO,
                ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        if (formed) {
            host.loadTag(formedTag(), HolderLookup.Provider.create(Stream.empty()));
        }
        return host;
    }

    private static CompoundTag formedTag() {
        CompoundTag tag = currentHostTag();
        tag.putBoolean("formed", true);
        return tag;
    }

    private static CompoundTag currentHostTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", HOST_SCHEMA_VERSION);
        tag.putUUID("trinity_data_core_storage_id", UUID.randomUUID());
        tag.putUUID("trinity_data_core_host_id", UUID.randomUUID());
        CompoundTag runtimeTag = new CompoundTag();
        runtimeTag.putInt("schema_version", RUNTIME_SCHEMA_VERSION);
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

    private static BusyRuntimeFixture busyRuntime(GameTestHelper helper, BlockPos hostPos) {
        TestGrid grid = new TestGrid();
        TestHost host = new TestHost(helper.absolutePos(hostPos), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));

        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        ICraftingSubmitResult result = reserveCpu.submitJob(
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
        TrinityDataCoreVirtualCpu cpu = singleBusyWorker(host.getCraftingRuntime());
        return new BusyRuntimeFixture(host, host.getCraftingRuntime(), cpu);
    }

    private static OutputRuntimeFixture outputRuntime(GameTestHelper helper, BlockPos hostPos) {
        return outputRuntime(helper, hostPos, 1);
    }

    private static OutputRuntimeFixture outputRuntime(GameTestHelper helper, BlockPos hostPos, int workerCapacity) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(hostPos), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, workerCapacity));
        return new OutputRuntimeFixture(grid, host.getCraftingRuntime(), host.getCpuPartitions().getFirst());
    }

    private static CraftingPlan outputPlan(GenericStack finalOutput, GenericStack... additionalOutputs) {
        KeyCounter emittedItems = new KeyCounter();
        emittedItems.add(finalOutput.what(), finalOutput.amount());
        for (GenericStack output : additionalOutputs) {
            emittedItems.add(output.what(), output.amount());
        }
        return new CraftingPlan(
                finalOutput,
                1L,
                false,
                false,
                new KeyCounter(),
                emittedItems,
                new KeyCounter(),
                Map.of());
    }

    private static CraftingPlan emptyJobPlan() {
        return new CraftingPlan(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1L),
                1L,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
    }

    private static CraftingPlan ingredientPlan(AEKey ingredient, long amount) {
        KeyCounter usedItems = new KeyCounter();
        usedItems.add(ingredient, amount);
        return new CraftingPlan(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1L),
                1L,
                false,
                false,
                usedItems,
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
    }

    private static CountedBatchFixture countedBatchFixture(GameTestHelper helper,
                                                           BlockPos hostPos,
                                                           double availableEnergy,
                                                           BatchPushOutcome outcome) {
        AEItemKey input = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        CountedPatternDetails pattern = new CountedPatternDetails(input, output);
        RecordingBatchCraftingProvider provider = new RecordingBatchCraftingProvider(pattern, input, outcome);
        TestGrid grid = new TestGrid();
        grid.setCraftingProvider(provider);
        grid.energyService().setStoredPower(availableEnergy);
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(hostPos), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution(
                "counted_batch",
                TrinityDataCoreCpuContribution.of(4096L, 0, 1));
        seedStorage(grid.storage(), input, COUNTED_BATCH_SIZE);

        KeyCounter usedItems = new KeyCounter();
        usedItems.add(input, COUNTED_BATCH_SIZE);
        CraftingPlan plan = new CraftingPlan(
                new GenericStack(output, COUNTED_BATCH_SIZE),
                1L,
                false,
                false,
                usedItems,
                new KeyCounter(),
                new KeyCounter(),
                Map.of(pattern, COUNTED_BATCH_SIZE));
        TrinityDataCoreVirtualCpu reserveCpu = host.getCpuPartitions().getFirst();
        ICraftingSubmitResult result = reserveCpu.submitJob(grid, plan, IActionSource.empty(), null);
        helper.assertTrue(result.successful(), "Counted batch fixture must submit its complete crafting plan");
        return new CountedBatchFixture(
                grid,
                singleBusyWorker(host.getCraftingRuntime()),
                provider,
                input,
                output);
    }

    private static void assertCountedBatchRollback(GameTestHelper helper,
                                                   BlockPos hostPos,
                                                   BatchPushOutcome failureOutcome) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                hostPos,
                COUNTED_BATCH_SIZE,
                failureOutcome);

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                failureOutcome + " provider must be attempted exactly once");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), COUNTED_BATCH_SIZE,
                failureOutcome + " provider must return the prototype and every additional input to the CPU");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), 0L,
                failureOutcome + " provider must not commit expected outputs");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), (double) COUNTED_BATCH_SIZE,
                failureOutcome + " provider must not consume energy");
        helper.assertTrue(fixture.cpu().isBusy(), failureOutcome + " provider must leave the task available for retry");

        fixture.provider().setOutcome(BatchPushOutcome.ACCEPT);
        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 2,
                failureOutcome + " provider must permit one later retry");
        helper.assertValueEqual(fixture.provider().lastBatchCount(), COUNTED_BATCH_SIZE,
                failureOutcome + " provider must preserve the complete logical task count for retry");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                failureOutcome + " retry must transfer every restored input");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                failureOutcome + " retry must account for every expected output");
    }

    private static void assertModulatedEnergyShortfallRollback(GameTestHelper helper, BlockPos hostPos) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                hostPos,
                COUNTED_BATCH_SIZE,
                BatchPushOutcome.ACCEPT);
        fixture.grid().energyService().setMaxModulatedExtraction(7.0D);

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 0,
                "An incomplete modulated energy charge must not reach the provider");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), (double) COUNTED_BATCH_SIZE,
                "A partial modulated energy charge must be refunded completely");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), COUNTED_BATCH_SIZE,
                "An incomplete modulated energy charge must restore every prepared input");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), 0L,
                "An incomplete modulated energy charge must not commit waiting outputs");
        helper.assertTrue(fixture.cpu().isBusy(),
                "An incomplete modulated energy charge must preserve the task for retry");

        fixture.grid().energyService().setMaxModulatedExtraction(Double.MAX_VALUE);
        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                "The preserved task must reach the provider after energy recovers");
        helper.assertValueEqual(fixture.provider().lastBatchCount(), COUNTED_BATCH_SIZE,
                "The energy-failed task must retain its complete logical count");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                "The successful retry must transfer every restored input");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                "The successful retry must account for every expected output");
    }

    private static void assertUncommittedAdmissionState(GameTestHelper helper,
                                                        CountedBatchFixture fixture,
                                                        String scenario) {
        helper.assertValueEqual(fixture.provider().batchPushCount(), 0,
                scenario + " admission must not reach commit");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), COUNTED_BATCH_SIZE,
                scenario + " admission must preserve every CPU input");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), 0L,
                scenario + " admission must not add waiting outputs");
        helper.assertValueEqual(
                fixture.grid().energyService().getStoredPower(),
                (double) COUNTED_BATCH_SIZE,
                scenario + " admission must not consume energy");
        helper.assertTrue(fixture.cpu().isBusy(), scenario + " admission must preserve the logical task");
    }

    private static void assertTransferredCountedBatchOwnership(GameTestHelper helper,
                                                               BlockPos hostPos,
                                                               BatchPushOutcome outcome) {
        CountedBatchFixture fixture = countedBatchFixture(
                helper,
                hostPos,
                COUNTED_BATCH_SIZE,
                outcome);

        fixture.cpu().tick(
                fixture.grid().energyService(),
                fixture.grid().craftingService(),
                CraftingDispatchWindow.create());

        helper.assertValueEqual(fixture.provider().batchPushCount(), 1,
                outcome + " provider must be attempted exactly once");
        helper.assertValueEqual(fixture.cpu().getStored(fixture.input()), 0L,
                outcome + " provider must not duplicate inputs back into the CPU");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(fixture.output()), COUNTED_BATCH_SIZE,
                outcome + " provider ownership transfer must retain complete output accounting");
        helper.assertValueEqual(fixture.grid().energyService().getStoredPower(), 0.0D,
                outcome + " provider ownership transfer must retain the complete energy charge");
        helper.assertTrue(fixture.cpu().isBusy(),
                outcome + " provider ownership transfer must leave the CPU waiting for outputs");
    }

    private static void seedStorage(TestStorage storage, AEKey key, long amount) {
        storage.setMaxAcceptedPerInsert(Long.MAX_VALUE);
        long inserted = storage.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
        if (inserted != amount) {
            throw new IllegalStateException("Test storage rejected seeded amount for " + key);
        }
    }

    private static CraftingPlan patternPlan(PendingPatternDetails pattern, long taskCount) {
        return new CraftingPlan(
                new GenericStack(pattern.output(), taskCount),
                1L,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of(pattern, taskCount));
    }

    private static void tickRuntimes(TestGrid grid,
                                     CraftingDispatchWindow dispatchWindow,
                                     TrinityDataCoreCraftingRuntime first,
                                     TrinityDataCoreCraftingRuntime second) {
        first.tick(grid.energyService(), grid.craftingService(), dispatchWindow);
        second.tick(grid.energyService(), grid.craftingService(), dispatchWindow);
    }

    private static void assertOfflineWithoutExtraction(GameTestHelper helper,
                                                       TrinityDataCoreVirtualCpu cpu,
                                                       TestGrid grid,
                                                       CraftingPlan plan,
                                                       AEKey ingredient,
                                                       String scenario) {
        long before = grid.storage().getStored(ingredient);
        ICraftingSubmitResult result = cpu.submitJob(grid, plan, IActionSource.empty(), null);
        helper.assertTrue(result == CraftingSubmitResult.CPU_OFFLINE,
                scenario + " should reject the old CPU reference as offline");
        helper.assertValueEqual(
                grid.storage().getStored(ingredient),
                before,
                scenario + " must reject before extracting any ingredient");
        helper.assertFalse(cpu.isBusy(), scenario + " must not install a CPU job");
    }

    private static TrinityDataCoreVirtualCpu submitOutputJob(OutputRuntimeFixture fixture,
                                                             @Nullable TestRequester requester,
                                                             CraftingPlan plan) {
        ICraftingSubmitResult result = fixture.reserveCpu().submitJob(
                fixture.grid(),
                plan,
                IActionSource.empty(),
                requester);
        if (!result.successful()) {
            throw new IllegalStateException("Test CPU output job submission failed: " + result.errorCode());
        }
        if (requester != null) {
            ICraftingLink link = result.link();
            if (link == null) {
                throw new IllegalStateException("Requester job submission did not return its crafting link");
            }
            requester.track(link);
        }
        return singleBusyWorker(fixture.runtime());
    }

    private static void submitWaitingOutputJob(GameTestHelper helper,
                                               OutputRuntimeFixture fixture,
                                               AEKey what,
                                               long amount) {
        ICraftingSubmitResult result = fixture.reserveCpu().submitJob(
                fixture.grid(),
                outputPlan(new GenericStack(what, amount)),
                IActionSource.empty(),
                null);
        helper.assertTrue(result.successful(), "Requested-amount test worker should accept its output job");
    }

    private static TrinityDataCoreVirtualCpu singleBusyWorker(TrinityDataCoreCraftingRuntime runtime) {
        List<TrinityDataCoreVirtualCpu> busyWorkers = runtime.publishedCpus().stream()
                .filter(TrinityDataCoreVirtualCpu::isBusy)
                .toList();
        if (busyWorkers.size() != 1) {
            throw new IllegalStateException("Expected exactly one busy test worker, found " + busyWorkers.size());
        }
        return busyWorkers.getFirst();
    }

    private static void assertAcceptanceFailureMessage(GameTestHelper helper,
                                                       IllegalStateException failure,
                                                       AEKey what,
                                                       Actionable mode,
                                                       long requested,
                                                       long accepted) {
        String message = failure.getMessage();
        helper.assertTrue(message.contains(what.toString()), "Failure must identify the rejected key");
        helper.assertTrue(message.contains(mode.toString()), "Failure must identify the insertion mode");
        helper.assertTrue(message.contains("requested " + requested), "Failure must identify the requested amount");
        helper.assertTrue(message.contains("accepted " + accepted), "Failure must identify the returned amount");
    }

    private static <T extends Throwable> T assertThrows(GameTestHelper helper,
                                                        Class<T> expectedType,
                                                        Runnable action,
                                                        String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return expectedType.cast(thrown);
            }
            helper.fail(message + ": expected " + expectedType.getSimpleName() + " but caught " + thrown.getClass().getSimpleName() + " (" + thrown.getMessage() + ")");
        }
        helper.fail(message + ": expected " + expectedType.getSimpleName() + " but no exception was thrown");
        throw new IllegalStateException("GameTest failure did not abort execution");
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
        helper.assertFalse(fixture.host().isStructureFormed(), source + " must not restore the main structure state");
        helper.assertFalse(fixture.host().isCpuStructureFormed(), source + " must not restore the CPU child state");
        helper.assertFalse(
                fixture.host().isCraftingStructureFormed(),
                source + " must not restore the crafting child state");
        helper.assertValueEqual(
                fixture.runtime().profile().storageBytes(),
                0L,
                source + " must discard CPU contributions");
        helper.assertFalse(fixture.runtime().hasBusyJobs(), source + " must discard running CPU jobs");
        helper.assertFalse(
                fixture.host().getStorageId().equals(persistedStorageId),
                source + " must not retain the persisted storage identity");
        helper.assertFalse(
                fixture.host().getHostId().equals(persistedHostId),
                source + " must not retain the persisted routing identity");
    }

    private record BusyRuntimeFixture(TestHost host,
                                      TrinityDataCoreCraftingRuntime runtime,
                                      TrinityDataCoreVirtualCpu cpu) {}

    private record OutputRuntimeFixture(TestGrid grid,
                                        TrinityDataCoreCraftingRuntime runtime,
                                        TrinityDataCoreVirtualCpu reserveCpu) {}

    private static final class RuntimeGridNode extends GridNode {

        private static final IGridNodeListener<Object> LISTENER = (owner, node) -> {};

        private final IGrid grid;

        private RuntimeGridNode(IGrid grid) {
            super(null, new Object(), LISTENER, Set.of());
            this.grid = grid;
        }

        @Override
        public IGrid getGrid() {
            return this.grid;
        }
    }

    private static final class TestHost extends TrinityDataCoreBlockEntity {

        @Nullable
        private final IGrid grid;

        private TestHost(BlockPos pos) {
            this(pos, null);
        }

        private TestHost(BlockPos pos, @Nullable IGrid grid) {
            super(pos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
            this.grid = grid;
        }

        @Override
        public boolean hasActiveAccessHatch() {
            return true;
        }

        @Override
        public boolean isCpuProviderAvailable() {
            return true;
        }

        @Override
        public @Nullable IGrid accessGrid() {
            return this.grid;
        }
    }

    private static final class NetworkedTestHost extends TrinityDataCoreBlockEntity {

        private final TestGrid grid;
        private boolean cpuProviderAvailable = true;

        private NetworkedTestHost(BlockPos pos, TestGrid grid) {
            super(pos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
            this.grid = grid;
        }

        @Override
        public boolean hasActiveAccessHatch() {
            return true;
        }

        @Override
        public boolean isCpuProviderAvailable() {
            return this.cpuProviderAvailable;
        }

        private void setCpuProviderAvailable(boolean cpuProviderAvailable) {
            this.cpuProviderAvailable = cpuProviderAvailable;
        }

        @Override
        public IGrid accessGrid() {
            return this.grid;
        }

        @Override
        public IActionSource accessActionSource() {
            return IActionSource.empty();
        }
    }

    private static final class TestGrid implements IGrid {

        private final TestStorage storage = new TestStorage();
        private final IStorageService storageService = new TestStorageService(this.storage);
        private final TestEnergyService energyService = new TestEnergyService();
        private final TestCraftingService craftingService = new TestCraftingService(
                this,
                this.storageService,
                this.energyService);

        private void setCraftingProvider(ICraftingProvider provider) {
            this.craftingService.setProvider(provider);
        }

        private void setCraftingProviders(List<ICraftingProvider> providers) {
            this.craftingService.setProviders(providers);
        }

        private TestStorage storage() {
            return this.storage;
        }

        private TestEnergyService energyService() {
            return this.energyService;
        }

        private CraftingService craftingService() {
            return this.craftingService;
        }

        @Override
        public <C extends IGridService> C getService(Class<C> serviceType) {
            if (serviceType == IStorageService.class) {
                return serviceType.cast(this.storageService);
            }
            if (serviceType == IEnergyService.class) {
                return serviceType.cast(this.energyService);
            }
            if (serviceType == ICraftingService.class) {
                return serviceType.cast(this.craftingService);
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

    private static final class TestCraftingService extends CraftingService {

        private List<ICraftingProvider> providers = List.of();
        private int nextProviderIndex;

        private TestCraftingService(IGrid grid, IStorageService storageService, IEnergyService energyService) {
            super(grid, storageService, energyService);
        }

        private void setProvider(ICraftingProvider provider) {
            setProviders(List.of(provider));
        }

        private void setProviders(List<ICraftingProvider> providers) {
            this.providers = List.copyOf(providers);
            this.nextProviderIndex = 0;
        }

        @Override
        public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) {
            List<ICraftingProvider> matchingProviders = this.providers.stream()
                    .filter(provider -> provider.getAvailablePatterns().stream()
                            .anyMatch(candidate -> candidate == pattern))
                    .toList();
            if (matchingProviders.isEmpty()) {
                return List.of();
            }
            int start = Math.floorMod(this.nextProviderIndex, matchingProviders.size());
            return () -> new Iterator<>() {

                private int offset;

                @Override
                public boolean hasNext() {
                    return this.offset < matchingProviders.size();
                }

                @Override
                public ICraftingProvider next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException("No crafting providers remain in this cycle");
                    }
                    ICraftingProvider provider = matchingProviders.get((start + this.offset) % matchingProviders.size());
                    this.offset++;
                    nextProviderIndex = (start + this.offset) % matchingProviders.size();
                    return provider;
                }
            };
        }
    }

    private static final class TestStorageService implements IStorageService {

        private final TestStorage inventory;
        private final Set<IStorageProvider> globalProviders = new HashSet<>();

        private TestStorageService(TestStorage inventory) {
            this.inventory = inventory;
        }

        @Override
        public MEStorage getInventory() {
            return this.inventory;
        }

        @Override
        public KeyCounter getCachedInventory() {
            KeyCounter cached = new KeyCounter();
            this.inventory.getAvailableStacks(cached);
            return cached;
        }

        @Override
        public void addGlobalStorageProvider(IStorageProvider provider) {
            this.globalProviders.add(provider);
        }

        @Override
        public void removeGlobalStorageProvider(IStorageProvider provider) {
            this.globalProviders.remove(provider);
        }

        @Override
        public void refreshNodeStorageProvider(IGridNode node) {
            throw new UnsupportedOperationException("Test storage does not mount providers");
        }

        @Override
        public void refreshGlobalStorageProvider(IStorageProvider provider) {
            if (!this.globalProviders.contains(provider)) {
                throw new IllegalArgumentException("Test storage provider is not registered");
            }
        }

        @Override
        public void invalidateCache() {}
    }

    private static final class TestStorage implements MEStorage {

        private final KeyCounter contents = new KeyCounter();
        private long maxAcceptedPerInsert;

        private void setMaxAcceptedPerInsert(long maxAcceptedPerInsert) {
            this.maxAcceptedPerInsert = maxAcceptedPerInsert;
        }

        private long getStored(AEKey what) {
            return this.contents.get(what);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, this.maxAcceptedPerInsert);
            if (mode == Actionable.MODULATE) {
                this.contents.add(what, accepted);
            }
            return accepted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, this.contents.get(what));
            if (mode == Actionable.MODULATE) {
                this.contents.remove(what, extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(this.contents);
        }

        @Override
        public Component getDescription() {
            return Component.literal("Trinity CPU test storage");
        }
    }

    private static final class TestEnergyService implements IEnergyService {

        private double storedPower = Double.MAX_VALUE;
        private double maxModulatedExtraction = Double.MAX_VALUE;

        private void setStoredPower(double storedPower) {
            this.storedPower = storedPower;
        }

        private void setMaxModulatedExtraction(double maxModulatedExtraction) {
            this.maxModulatedExtraction = maxModulatedExtraction;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            double extracted = Math.min(amount, this.storedPower);
            if (mode == Actionable.MODULATE) {
                extracted = Math.min(extracted, this.maxModulatedExtraction);
                this.storedPower -= extracted;
            }
            return extracted;
        }

        @Override
        public double getIdlePowerUsage() {
            return 0.0D;
        }

        @Override
        public double getChannelPowerUsage() {
            return 0.0D;
        }

        @Override
        public double getAvgPowerUsage() {
            return 0.0D;
        }

        @Override
        public double getAvgPowerInjection() {
            return 0.0D;
        }

        @Override
        public boolean isNetworkPowered() {
            return true;
        }

        @Override
        public double injectPower(double amount, Actionable mode) {
            double accepted = Math.min(amount, Double.MAX_VALUE - this.storedPower);
            if (mode == Actionable.MODULATE) {
                this.storedPower += accepted;
            }
            return amount - accepted;
        }

        @Override
        public double getStoredPower() {
            return this.storedPower;
        }

        @Override
        public double getMaxStoredPower() {
            return Double.MAX_VALUE;
        }

        @Override
        public double getEnergyDemand(double maxRequired) {
            return 0.0D;
        }
    }

    private static final class RecordingCraftingProvider implements ICraftingProvider {

        private final IPatternDetails pattern;
        @Nullable
        private IPatternDetails pushedPattern;
        private int pushedInputSlots = -1;
        private int pushCount;

        private RecordingCraftingProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        private int pushCount() {
            return this.pushCount;
        }

        @Nullable
        private IPatternDetails pushedPattern() {
            return this.pushedPattern;
        }

        private int pushedInputSlots() {
            return this.pushedInputSlots;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            this.pushedPattern = patternDetails;
            this.pushedInputSlots = inputHolder.length;
            this.pushCount++;
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class SequencedCraftingProvider implements ICraftingProvider {

        private final List<IPatternDetails> patterns;
        private final List<IPatternDetails> pushedPatterns = new ArrayList<>();

        private SequencedCraftingProvider(List<IPatternDetails> patterns) {
            this.patterns = List.copyOf(patterns);
        }

        private long pushCount(IPatternDetails pattern) {
            return this.pushedPatterns.stream().filter(pushed -> pushed == pattern).count();
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return this.patterns;
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            this.pushedPatterns.add(patternDetails);
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class BusyThrowingCraftingProvider implements ICraftingProvider {

        private final IPatternDetails pattern;
        private int busyCheckCount;

        private BusyThrowingCraftingProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        private int busyCheckCount() {
            return this.busyCheckCount;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new AssertionError("A provider whose busy check failed must not receive inputs");
        }

        @Override
        public boolean isBusy() {
            this.busyCheckCount++;
            throw new IllegalStateException("Test provider busy-state failure");
        }
    }

    private static final class PatternScopedCapacityProvider implements CountedCraftingProvider {

        private final IPatternDetails blockedPattern;
        private final IPatternDetails acceptedPattern;
        private int blockedPrepareCount;
        private int acceptedCommitCount;

        private PatternScopedCapacityProvider(IPatternDetails blockedPattern, IPatternDetails acceptedPattern) {
            this.blockedPattern = blockedPattern;
            this.acceptedPattern = acceptedPattern;
        }

        private int blockedPrepareCount() {
            return this.blockedPrepareCount;
        }

        private int acceptedCommitCount() {
            return this.acceptedCommitCount;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.blockedPattern, this.acceptedPattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new AssertionError("Pattern-scoped provider must use counted dispatch");
        }

        @Nullable
        @Override
        public CountedCraftingAdmission prepareBatch(IPatternDetails patternDetails,
                                                     KeyCounter[] prototype,
                                                     long requestedCount) {
            if (patternDetails == this.blockedPattern) {
                this.blockedPrepareCount++;
                return null;
            }
            if (patternDetails != this.acceptedPattern) {
                throw new IllegalArgumentException("Unexpected pattern-scoped capacity test pattern");
            }
            return new CountedCraftingAdmission() {

                @Override
                public long count() {
                    return Math.min(1L, requestedCount);
                }

                @Override
                public boolean commit(KeyCounter[] inputHolder) {
                    acceptedCommitCount++;
                    return true;
                }
            };
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class RecordingBatchCraftingProvider implements CountedCraftingProvider {

        private final IPatternDetails pattern;
        private final AEKey input;
        private BatchPushOutcome outcome;
        private long maximumAdmissionCount = Long.MAX_VALUE;
        @Nullable
        private Long invalidAdmissionCount;
        private boolean noCapacity;
        private int prepareCount;
        private int batchPushCount;
        private long lastBatchCount;
        private long lastPrototypeAmount;

        private RecordingBatchCraftingProvider(IPatternDetails pattern,
                                               AEKey input,
                                               BatchPushOutcome outcome) {
            this.pattern = pattern;
            this.input = input;
            this.outcome = outcome;
        }

        private void setOutcome(BatchPushOutcome outcome) {
            this.outcome = outcome;
        }

        private void setMaximumAdmissionCount(long maximumAdmissionCount) {
            this.maximumAdmissionCount = maximumAdmissionCount;
        }

        private void setInvalidAdmissionCount(long invalidAdmissionCount) {
            this.invalidAdmissionCount = invalidAdmissionCount;
        }

        private void setNoCapacity(boolean noCapacity) {
            this.noCapacity = noCapacity;
        }

        private int prepareCount() {
            return this.prepareCount;
        }

        private int batchPushCount() {
            return this.batchPushCount;
        }

        private long lastBatchCount() {
            return this.lastBatchCount;
        }

        private long lastPrototypeAmount() {
            return this.lastPrototypeAmount;
        }

        private IPatternDetails pattern() {
            return this.pattern;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new IllegalStateException("Counted batch provider must not receive a single-pattern dispatch");
        }

        @Override
        public CountedCraftingAdmission prepareBatch(IPatternDetails patternDetails,
                                                     KeyCounter[] prototype,
                                                     long requestedCount) {
            this.prepareCount++;
            if (this.noCapacity) {
                return null;
            }
            long admittedCount = this.invalidAdmissionCount != null ? this.invalidAdmissionCount : Math.min(requestedCount, this.maximumAdmissionCount);
            return new CountedCraftingAdmission() {

                @Override
                public long count() {
                    return admittedCount;
                }

                @Override
                public boolean hasTransferredInputOwnership() {
                    return outcome == BatchPushOutcome.TRANSFER_AND_THROW;
                }

                @Override
                public boolean commit(KeyCounter[] inputHolder) {
                    batchPushCount++;
                    lastBatchCount = admittedCount;
                    lastPrototypeAmount = inputHolder[0].get(input);
                    if (outcome == BatchPushOutcome.CONSUME_AND_REJECT || outcome == BatchPushOutcome.CONSUME_AND_THROW) {
                        for (KeyCounter counter : inputHolder) {
                            counter.clear();
                        }
                    }
                    if (outcome == BatchPushOutcome.THROW || outcome == BatchPushOutcome.CONSUME_AND_THROW || outcome == BatchPushOutcome.TRANSFER_AND_THROW) {
                        throw new IllegalStateException("Test counted batch provider failure");
                    }
                    return outcome == BatchPushOutcome.ACCEPT;
                }
            };
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class TestRequester implements ICraftingRequester {

        private final Set<ICraftingLink> requestedJobs = new HashSet<>();
        private final KeyCounter received = new KeyCounter();
        private long simulatedAcceptance;
        private long modulatedAcceptance;
        private boolean capAcceptanceAtRequested;
        private int jobStateChanges;

        private TestRequester(long maxAccepted) {
            setMaxAccepted(maxAccepted);
        }

        private void setMaxAccepted(long maxAccepted) {
            setMaxAccepted(maxAccepted, maxAccepted);
        }

        private void setMaxAccepted(long maxSimulatedAccepted, long maxModulatedAccepted) {
            this.simulatedAcceptance = maxSimulatedAccepted;
            this.modulatedAcceptance = maxModulatedAccepted;
            this.capAcceptanceAtRequested = true;
        }

        private void returnExactly(long simulatedAccepted, long modulatedAccepted) {
            this.simulatedAcceptance = simulatedAccepted;
            this.modulatedAcceptance = modulatedAccepted;
            this.capAcceptanceAtRequested = false;
        }

        private void track(ICraftingLink link) {
            this.requestedJobs.add(link);
        }

        private int jobStateChanges() {
            return this.jobStateChanges;
        }

        @Override
        public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return ImmutableSet.copyOf(this.requestedJobs);
        }

        @Override
        public long insertCraftedItems(ICraftingLink link,
                                       AEKey what,
                                       long amount,
                                       Actionable mode) {
            long configuredAcceptance = mode == Actionable.SIMULATE ? this.simulatedAcceptance : this.modulatedAcceptance;
            long accepted = this.capAcceptanceAtRequested ? Math.min(amount, configuredAcceptance) : configuredAcceptance;
            if (mode == Actionable.MODULATE) {
                this.received.add(what, accepted);
            }
            return accepted;
        }

        @Override
        public void jobStateChange(ICraftingLink link) {
            this.jobStateChanges++;
        }

        @Nullable
        @Override
        public IGridNode getActionableNode() {
            return null;
        }
    }

    private record PendingPatternDetails(AEItemKey output) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(this.output, 1L));
        }
    }

    private record CountedPatternDetails(AEItemKey input, AEItemKey output) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new ExactPatternInput(this.input) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(this.output, 1L));
        }
    }

    private record ExactPatternInput(AEKey key) implements IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(this.key, 1L) };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.key.equals(input);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private enum BatchPushOutcome {
        ACCEPT,
        REJECT,
        THROW,
        CONSUME_AND_REJECT,
        CONSUME_AND_THROW,
        TRANSFER_AND_THROW
    }

    private record CountedBatchFixture(TestGrid grid,
                                       TrinityDataCoreVirtualCpu cpu,
                                       RecordingBatchCraftingProvider provider,
                                       AEItemKey input,
                                       AEItemKey output) {}
}
