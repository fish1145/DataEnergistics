package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
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
import appeng.me.service.CraftingService;
import com.google.common.collect.ImmutableSet;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
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
        TrinityDataCoreVirtualCpu cpu = host.getCpuPartitions().getFirst();
        CraftingPlan plan = ingredientPlan(iron, 2L);
        seedStorage(leaseGrid.storage(), iron, 2L);
        seedStorage(wrongGrid.storage(), iron, 2L);

        assertOfflineWithoutExtraction(helper, cpu, wrongGrid, plan, iron, "Wrong grid");

        host.setCpuProviderAvailable(false);
        assertOfflineWithoutExtraction(helper, cpu, leaseGrid, plan, iron, "Unavailable CPU child");

        host.setCpuProviderAvailable(true);
        host.clearCpuContribution("cpu");
        assertOfflineWithoutExtraction(helper, cpu, leaseGrid, plan, iron, "Stale CPU partition");
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
        List<TrinityDataCoreVirtualCpu> cpus = host.getCpuPartitions();
        seedStorage(grid.storage(), iron, 4L);

        for (TrinityDataCoreVirtualCpu cpu : cpus) {
            ICraftingSubmitResult result = cpu.submitJob(
                    grid,
                    ingredientPlan(iron, 2L),
                    IActionSource.empty(),
                    null);
            helper.assertTrue(result.successful(), "Recovery test should submit a job to every retained CPU");
        }
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
        TrinityDataCoreBlockEntity host = trinityDataCore(true);

        host.setCpuContribution("partition", TrinityDataCoreCpuContribution.of(1024L, 2, 2));

        helper.assertValueEqual(host.getCpuPartitions().size(), 2, "Child contribution should add CPU partitions");
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

    @TestHolder("trinity_data_core_cpu_waits_for_all_requested_outputs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuWaitsForAllRequestedOutputs(GameTestHelper helper) {
        OutputRuntimeFixture fixture = outputRuntime(helper, new BlockPos(1, 1, 1));
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        AEItemKey bucket = AEItemKey.of(Items.BUCKET);
        TestRequester requester = new TestRequester(Long.MAX_VALUE);
        submitOutputJob(
                fixture.cpu(),
                fixture.grid(),
                requester,
                outputPlan(new GenericStack(diamond, 1L), new GenericStack(bucket, 1L)));

        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 1L, Actionable.MODULATE),
                1L,
                "Requester should accept the final output");
        helper.assertTrue(fixture.cpu().isBusy(), "CPU must remain busy while a container output is still requested");
        helper.assertValueEqual(
                fixture.cpu().getWaitingFor(bucket),
                1L,
                "Container output must remain requested after the final output arrives");

        helper.assertValueEqual(
                fixture.cpu().insert(bucket, 1L, Actionable.MODULATE),
                1L,
                "CPU should accept the remaining container output");
        helper.assertFalse(fixture.cpu().isBusy(), "CPU should finish after every requested output has arrived");
        helper.assertValueEqual(
                fixture.cpu().getStored(bucket),
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
        submitOutputJob(
                fixture.cpu(),
                fixture.grid(),
                requester,
                outputPlan(new GenericStack(diamond, 5L)));
        KeyCounter statusChanges = new KeyCounter();
        fixture.cpu().addListener(what -> statusChanges.add(what, 1L));

        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 5L, Actionable.MODULATE),
                0L,
                "A requester with no capacity should reject the final output");
        helper.assertValueEqual(
                fixture.cpu().getWaitingFor(diamond),
                5L,
                "Rejected final output must not mutate the requested amount");
        helper.assertTrue(fixture.cpu().isBusy(), "Rejected final output must keep the CPU busy");
        helper.assertValueEqual(requester.jobStateChanges(), 0, "Rejected output must not complete the job");
        helper.assertValueEqual(statusChanges.get(diamond), 0L, "Rejected output must not notify CPU status listeners");

        requester.setMaxAccepted(2L, 1L);
        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 5L, Actionable.SIMULATE),
                2L,
                "Simulation should report the requester's partial capacity");
        helper.assertValueEqual(
                fixture.cpu().getWaitingFor(diamond),
                5L,
                "Simulation must not mutate the requested amount");
        helper.assertValueEqual(statusChanges.get(diamond), 0L, "Simulation must not notify CPU status listeners");
        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 5L, Actionable.MODULATE),
                1L,
                "Modulation should report the amount actually accepted by the requester");

        helper.assertTrue(fixture.cpu().isBusy(), "Partially delivered final output must keep the CPU busy");
        helper.assertValueEqual(
                fixture.cpu().getWaitingFor(diamond),
                4L,
                "Only accepted final output may be removed from waiting state");
        CompoundTag jobTag = fixture.cpu()
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
                fixture.cpu().insert(diamond, 4L, Actionable.MODULATE),
                4L,
                "Requester should accept the remaining final output");
        helper.assertFalse(fixture.cpu().isBusy(), "CPU should finish after the final remainder is delivered");
        helper.assertValueEqual(requester.jobStateChanges(), 1, "Requester should be notified exactly once on completion");
        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 1L, Actionable.MODULATE),
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
        submitOutputJob(
                negativeFixture.cpu(),
                negativeFixture.grid(),
                negativeRequester,
                outputPlan(new GenericStack(diamond, 1L)));

        IllegalStateException negativeFailure = assertThrows(
                helper,
                IllegalStateException.class,
                () -> negativeFixture.cpu().insert(diamond, 1L, Actionable.SIMULATE),
                "Negative requester acceptance must fail fast");
        assertAcceptanceFailureMessage(helper, negativeFailure, diamond, Actionable.SIMULATE, 1L, -1L);
        helper.assertValueEqual(
                negativeFixture.cpu().getWaitingFor(diamond),
                1L,
                "Rejected simulation result must not mutate waiting state");
        helper.assertTrue(negativeFixture.cpu().isBusy(), "Rejected simulation result must retain the CPU job");

        OutputRuntimeFixture excessFixture = outputRuntime(helper, new BlockPos(3, 1, 1));
        TestRequester excessRequester = new TestRequester(1L);
        excessRequester.returnExactly(2L, 2L);
        submitOutputJob(
                excessFixture.cpu(),
                excessFixture.grid(),
                excessRequester,
                outputPlan(new GenericStack(diamond, 1L)));

        IllegalStateException excessFailure = assertThrows(
                helper,
                IllegalStateException.class,
                () -> excessFixture.cpu().insert(diamond, 1L, Actionable.MODULATE),
                "Excess requester acceptance must fail fast");
        assertAcceptanceFailureMessage(helper, excessFailure, diamond, Actionable.MODULATE, 1L, 2L);
        helper.assertValueEqual(
                excessFixture.cpu().getWaitingFor(diamond),
                1L,
                "Rejected modulation result must not mutate waiting state");
        helper.assertTrue(excessFixture.cpu().isBusy(), "Rejected modulation result must retain the CPU job");
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
        submitOutputJob(fixture.cpu(), fixture.grid(), requester, plan);

        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 1L, Actionable.MODULATE),
                1L,
                "Requester should accept the final output");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(diamond), 0L, "Final output should leave no waiting amount");
        helper.assertTrue(fixture.cpu().isBusy(), "Pending pattern tasks must prevent job completion");
        helper.assertValueEqual(requester.jobStateChanges(), 0, "Pending tasks must suppress completion notification");
        CompoundTag jobTag = fixture.cpu()
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
        submitOutputJob(
                fixture.cpu(),
                fixture.grid(),
                null,
                outputPlan(new GenericStack(diamond, 4L)));

        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 4L, Actionable.SIMULATE),
                4L,
                "Standalone CPU should advertise internal capacity for its final output");
        helper.assertValueEqual(
                fixture.cpu().getWaitingFor(diamond),
                4L,
                "Standalone simulation must not mutate waiting state");
        helper.assertValueEqual(
                fixture.cpu().insert(diamond, 4L, Actionable.MODULATE),
                4L,
                "Standalone CPU should accept its final output into internal inventory");

        helper.assertFalse(fixture.cpu().isBusy(), "Standalone job should finish after its output is accepted");
        helper.assertValueEqual(fixture.cpu().getWaitingFor(diamond), 0L, "Finished job should have no waiting output");
        helper.assertValueEqual(
                fixture.grid().storage().getStored(diamond),
                2L,
                "Job completion should return the accepted portion to network storage");
        helper.assertValueEqual(
                fixture.cpu().getStored(diamond),
                2L,
                "Network remainder must stay in CPU inventory for retry");

        fixture.grid().storage().setMaxAcceptedPerInsert(Long.MAX_VALUE);
        fixture.cpu().tick(fixture.grid().energyService(), fixture.grid().craftingService());
        helper.assertValueEqual(fixture.cpu().getStored(diamond), 0L, "Idle CPU should retry returning retained output");
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

        TestHost restoredHost = new TestHost(helper.absolutePos(new BlockPos(2, 1, 1)));
        restoredHost.setLevel(helper.getLevel());
        restoredHost.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        TrinityDataCoreCraftingRuntime restored = restoredHost.getCraftingRuntime();
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
        UUID storageId = fixture.host().getStorageId();
        UUID hostId = fixture.host().getHostId();
        helper.assertTrue(fixture.runtime().hasBusyJobs(), "Source host should own an active CPU job before saving");

        CompoundTag saved = new CompoundTag();
        fixture.host().saveAdditional(saved, helper.getLevel().registryAccess());

        TestHost restored = new TestHost(helper.absolutePos(new BlockPos(3, 1, 1)));
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
        BlockPos hostPos = new BlockPos(1, 1, 1);
        helper.setBlock(hostPos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(hostPos));
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", hostPos);
            return;
        }
        host.loadTag(formedCraftingProfileTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 1, 1));

        helper.assertValueEqual(host.getCpuPartitions().size(), 1, "CPU child contribution should be active before recheck");
        helper.assertTrue(host.isCraftingStructureFormed(), "Crafting child structure should be active before recheck");
        helper.assertValueEqual(
                host.getCraftingPatternCapacity(),
                704,
                "Crafting child profile should be active before recheck");
        TrinityDataCoreVirtualCpu retainedPartition = host.getCpuPartitions().getFirst();

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

        helper.assertValueEqual(host.getCpuPartitions().size(), 1,
                "Recovered main structure should republish the retained CPU partition");
        helper.assertTrue(host.getCpuPartitions().getFirst() == retainedPartition,
                "Recovered main structure should reuse the retained CPU partition and its job state");
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

        host.getCraftingRuntime().readFromTag(
                runtimeTag,
                HolderLookup.Provider.create(Stream.empty()));
        helper.assertValueEqual(
                host.getCpuPartitions().size(),
                0,
                "Pending partition logic should not create CPU partitions without a child contribution");
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
        unsupportedTag.putInt("schema_version", SCHEMA_VERSION + 1);
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
        original.saveStorageIdToItem(stack);
        UUID storageId = stack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID);

        TrinityDataCoreBlockEntity placed = trinityDataCore(false);
        placed.restoreStorageIdFromItem(stack);
        ItemStack placedStack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        placed.saveStorageIdToItem(placedStack);
        helper.assertValueEqual(
                placedStack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                "Placed host should restore the storage id from the item component");

        CompoundTag saved = new CompoundTag();
        placed.saveAdditional(saved, HolderLookup.Provider.create(Stream.empty()));
        TrinityDataCoreBlockEntity loaded = trinityDataCore(false);
        loaded.loadTag(saved, HolderLookup.Provider.create(Stream.empty()));
        ItemStack loadedStack = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        loaded.saveStorageIdToItem(loadedStack);
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

    private static BusyRuntimeFixture busyRuntime(GameTestHelper helper, BlockPos hostPos) {
        TestGrid grid = new TestGrid();
        TestHost host = new TestHost(helper.absolutePos(hostPos), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));

        TrinityDataCoreVirtualCpu cpu = host.getCpuPartitions().getFirst();
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
        return new BusyRuntimeFixture(host, host.getCraftingRuntime(), cpu);
    }

    private static OutputRuntimeFixture outputRuntime(GameTestHelper helper, BlockPos hostPos) {
        TestGrid grid = new TestGrid();
        NetworkedTestHost host = new NetworkedTestHost(helper.absolutePos(hostPos), grid);
        host.setLevel(helper.getLevel());
        host.loadTag(formedTrinityTag(), helper.getLevel().registryAccess());
        host.setCpuContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));
        return new OutputRuntimeFixture(grid, host.getCpuPartitions().getFirst());
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

    private static void seedStorage(TestStorage storage, AEKey key, long amount) {
        storage.setMaxAcceptedPerInsert(Long.MAX_VALUE);
        long inserted = storage.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
        if (inserted != amount) {
            throw new IllegalStateException("Test storage rejected seeded amount for " + key);
        }
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

    private static void submitOutputJob(TrinityDataCoreVirtualCpu cpu,
                                        TestGrid grid,
                                        @Nullable TestRequester requester,
                                        CraftingPlan plan) {
        ICraftingSubmitResult result = cpu.submitJob(grid, plan, IActionSource.empty(), requester);
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

    private record OutputRuntimeFixture(TestGrid grid, TrinityDataCoreVirtualCpu cpu) {}

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
        private final IEnergyService energyService = new TestEnergyService();
        private final CraftingService craftingService = new CraftingService(
                this,
                this.storageService,
                this.energyService);

        private TestStorage storage() {
            return this.storage;
        }

        private IEnergyService energyService() {
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

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            return amount;
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
            return 0.0D;
        }

        @Override
        public double getStoredPower() {
            return Double.MAX_VALUE;
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
}
