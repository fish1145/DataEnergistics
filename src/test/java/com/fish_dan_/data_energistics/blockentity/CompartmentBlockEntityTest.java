package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Result;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.RoutedCraftingPatternDetails;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.helpers.patternprovider.PatternContainer;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CompartmentBlockEntityTest {

    private CompartmentBlockEntityTest() {}

    @TestHolder("compartment_block_entity_me_input_pulls_marked_keys_from_storage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void meInputPullsMarkedKeysFromStorage(GameTestHelper helper) {
        MeCompositeInputWarehouseBlockEntity meInput = new MeCompositeInputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        SimpleMEStorage network = new SimpleMEStorage();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey wrappedIron = AEItemKey.of(GenericStack.wrapInItemStack(iron, 5000L));

        meInput.markerInventory().setStack(0, new GenericStack(wrappedIron, 1L));
        network.insert(iron, 5000L, Actionable.MODULATE, IActionSource.empty());

        meInput.pullMarkedKeysFromNetwork(network);

        helper.assertValueEqual(meInput.meInputBuffer().getKey(0), iron, "ME input should pull the unwrapped marker key");
        helper.assertValueEqual(
                meInput.meInputBuffer().getAmount(0),
                4000L,
                "ME input should pull the per-tick long transfer amount");
        helper.assertValueEqual(network.amount(iron), 1000L, "Network storage should lose the pulled amount");

        meInput.pullMarkedKeysFromNetwork(network);

        helper.assertValueEqual(
                meInput.meInputBuffer().getAmount(0),
                5000L,
                "ME input should accumulate additional pulled amounts beyond an ItemStack limit");
        helper.assertValueEqual(network.amount(iron), 0L, "Network storage should be drained after the second pull");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_me_output_provider_mounts_only_when_bound")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void meOutputProviderMountsOnlyWhenBound(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        MeCompositeOutputWarehouseBlockEntity meOutput = meOutputWarehouse();
        RecordingStorageMounts mounts = new RecordingStorageMounts();

        meOutput.outputStorageProvider().mountInventories(mounts);

        helper.assertValueEqual(mounts.mountCount(), 0, "Unbound ME output should not mount AE storage");

        meOutput.compartment$bindToHost("main", host);
        MEStorage boundStorage = meOutput.outputStorage();
        if (boundStorage == null) {
            helper.fail("Bound ME output should expose output storage before mounting");
            return;
        }
        meOutput.outputStorageProvider().mountInventories(mounts);

        helper.assertValueEqual(mounts.mountCount(), 1, "Bound ME output should mount one AE storage");
        helper.assertTrue(mounts.mountedStorage() == boundStorage, "Mounted storage should be the ME output buffer");
        helper.assertValueEqual(
                mounts.priority(),
                IStorageMounts.DEFAULT_PRIORITY,
                "ME output buffer should mount with the default AE priority");

        meOutput.compartment$unbindFromHost("main", host);
        RecordingStorageMounts unboundMounts = new RecordingStorageMounts();

        meOutput.outputStorageProvider().mountInventories(unboundMounts);

        helper.assertValueEqual(unboundMounts.mountCount(), 0, "Unbound ME output should stop mounting AE storage");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_me_output_requests_storage_updates")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void meOutputRequestsStorageUpdates(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        UpdateCountingMeOutputWarehouse meOutput = updateCountingMeOutputWarehouse();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        helper.assertValueEqual(meOutput.storageUpdateRequests(), 0, "Fresh ME output should not request updates");

        meOutput.compartment$bindToHost("main", host);

        helper.assertValueEqual(
                meOutput.storageUpdateRequests(),
                1,
                "Binding ME output should request an AE storage update");

        MEStorage outputStorage = meOutput.outputStorage();
        if (outputStorage == null) {
            helper.fail("Bound ME output should expose output storage for content mutation");
            return;
        }

        helper.assertValueEqual(
                outputStorage.insert(iron, 3L, Actionable.MODULATE, IActionSource.empty()),
                3L,
                "Bound ME output storage should accept inserted contents");
        helper.assertValueEqual(
                meOutput.storageUpdateRequests(),
                2,
                "Changing ME output contents should request an AE storage update");

        meOutput.compartment$unbindFromHost("main", host);

        helper.assertValueEqual(
                meOutput.storageUpdateRequests(),
                3,
                "Unbinding ME output should request an AE storage update");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_server_tick_updates_active_state_for_all_types")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentServerTickUpdatesActiveStateForAllTypes(GameTestHelper helper) {
        assertServerTickActiveState(
                helper,
                new BlockPos(1, 1, 1),
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState(),
                blockEntity -> blockEntity instanceof CompositeWarehouseBlockEntity,
                "plain input warehouse");
        assertServerTickActiveState(
                helper,
                new BlockPos(2, 1, 1),
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState(),
                blockEntity -> blockEntity instanceof CompositeWarehouseBlockEntity,
                "plain output warehouse");
        assertServerTickActiveState(
                helper,
                new BlockPos(3, 1, 1),
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState(),
                blockEntity -> blockEntity instanceof MeCompositeInputWarehouseBlockEntity,
                "ME input warehouse");
        assertServerTickActiveState(
                helper,
                new BlockPos(1, 1, 2),
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState(),
                blockEntity -> blockEntity instanceof MeCompositeOutputWarehouseBlockEntity,
                "ME output warehouse");
        assertServerTickActiveState(
                helper,
                new BlockPos(2, 1, 2),
                ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState(),
                blockEntity -> blockEntity instanceof MePatternBufferBlockEntity,
                "ME pattern buffer");
        assertTrinityAccessTickerClearsInactiveState(helper, new BlockPos(3, 1, 2));
        helper.succeed();
    }

    @TestHolder("trinity_access_hatch_exposes_grid_only_for_formed_bound_host")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void trinityAccessHatchExposesGridOnlyForFormedBoundHost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localOrigin = new BlockPos(25, 4, 25);
        BlockPos origin = helper.absolutePos(localOrigin);
        helper.setBlock(localOrigin, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));

        BlockEntity hostBlockEntity = level.getBlockEntity(origin);
        if (!(hostBlockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        helper.assertTrue(host.accessGrid() == null, "Unformed Trinity Data Core should not expose an AE grid");

        buildMainStructure(helper, level, origin);
        List<TrinityAccessHatchBlockEntity> builtHatches = requireBuiltTrinityAccessHatches(helper, level, origin);

        for (TrinityAccessHatchBlockEntity hatch : builtHatches) {
            helper.assertTrue(
                    hatch.accessGrid() == null,
                    "Unbound Trinity access hatch should not expose an AE grid before host recheck");
            assertTrinityAccessCableConnections(helper, hatch);
        }

        host.serverTick();
        helper.assertTrue(host.isStructureFormed(), "Auto-built Trinity Data Core main structure should form: " +
                host.getLastFailureReason() + " at " + host.getLastFailurePosition());
        List<TrinityAccessHatchBlockEntity> boundHatches = boundTrinityAccessHatches(host);
        helper.assertTrue(!boundHatches.isEmpty(), "Formed Trinity Data Core should bind Trinity access hatches");
        helper.assertTrue(
                boundHatches.stream().anyMatch(builtHatches::contains),
                "Formed Trinity Data Core should bind an auto-built Trinity access hatch from the main structure");
        for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
            assertTrinityAccessCableConnections(helper, hatch);
            hatch.refreshTrinityAccess();
        }

        AtomicReference<TestGridPower> testGridPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(testGridPower));
        AtomicBoolean storageSeeded = new AtomicBoolean();
        AtomicBoolean storageMountChecked = new AtomicBoolean();
        AtomicBoolean mainChecked = new AtomicBoolean();
        AtomicBoolean cpuChecked = new AtomicBoolean();
        AtomicBoolean patternInstalled = new AtomicBoolean();
        AtomicBoolean providerMountChecked = new AtomicBoolean();
        AtomicBoolean catalogInvalidated = new AtomicBoolean();
        helper.succeedWhen(() -> {
            connectAccessHatches(helper, level, boundHatches, testGridPower);
            host.serverTick();
            for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                hatch.refreshTrinityAccess();
            }
            assertHostUsesBoundTrinityAccessGrid(helper, host, boundHatches);
            assertSingleLeaseOwner(helper, host, boundHatches);
            IGrid grid = host.accessGrid();
            helper.assertTrue(grid != null, "Formed Trinity main structure should expose one powered AE grid");
            helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.connectedGrid() == grid),
                    "Both Trinity access hatches should share one AE grid in this test");

            AEItemKey leaseProbe = AEItemKey.of(Items.GOLD_INGOT);
            if (!storageSeeded.get()) {
                long inserted = TrinityDataCoreStorageSavedData.get(level.getServer()).insert(
                        host.getStorageId(),
                        leaseProbe,
                        7L,
                        Actionable.MODULATE);
                helper.assertValueEqual(inserted, 7L, "Lease probe should enter the host UUID storage");
                storageSeeded.set(true);
                for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                    hatch.refreshTrinityAccess();
                }
                return;
            }
            if (!storageMountChecked.get()) {
                helper.assertValueEqual(
                        availableAmount(grid, leaseProbe),
                        7L,
                        "Two hatches on one grid must mount the shared host storage exactly once");
                storageMountChecked.set(true);
            }

            if (!mainChecked.get()) {
                helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.boundCraftingRuntime() == null),
                        "Main-only Trinity structure must not publish virtual CPUs");
                helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.terminalPartitions().isEmpty()),
                        "Main-only Trinity structure must not publish pattern terminal partitions");
                buildCpuStructure(helper, level, origin);
                host.requestStructureRecheck();
                mainChecked.set(true);
                return;
            }

            if (!cpuChecked.get()) {
                helper.assertTrue(host.isCpuStructureFormed(),
                        "Auto-built Trinity CPU child structure should form: " + host.getCpuLastFailureReason());
                helper.assertTrue(boundHatches.stream().filter(host::isLeaseOwner)
                        .anyMatch(hatch -> hatch.boundCraftingRuntime() == host.getCraftingRuntime()),
                        "CPU child structure must publish the virtual CPU runtime independently");
                helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.terminalPartitions().isEmpty()),
                        "CPU-only child structure must not publish pattern terminal partitions");
                buildCraftingStructure(helper, level, origin);
                host.requestStructureRecheck();
                cpuChecked.set(true);
                return;
            }

            helper.assertTrue(host.isCraftingStructureFormed(),
                    "Auto-built Trinity crafting child structure should form: " + host.getCraftingLastFailureReason());
            List<TrinityDataCoreVirtualCpu> cpuPartitions = host.getCpuPartitions();
            helper.assertValueEqual(
                    grid.getCraftingService().getCpus().stream().filter(cpuPartitions::contains).count(),
                    (long) cpuPartitions.size(),
                    "Two hatches on one grid must publish each Trinity CPU partition exactly once");
            helper.assertTrue(boundHatches.stream()
                    .filter(host::isLeaseOwner)
                    .flatMap(hatch -> hatch.terminalPartitions().stream())
                    .allMatch(partition -> partition.isAttachedTo(grid)),
                    "The lease owner should attach every terminal partition to its selected grid");
            helper.assertTrue(boundHatches.stream().filter(host::isLeaseOwner)
                    .anyMatch(hatch -> !hatch.terminalPartitions().isEmpty()),
                    "The lease owner should publish at least one terminal partition");
            helper.assertValueEqual(
                    boundHatches.stream().filter(hatch -> !host.isLeaseOwner(hatch))
                            .mapToInt(hatch -> hatch.terminalPartitions().size()).sum(),
                    0,
                    "Non-owning access hatches must not mount duplicate terminal partitions");

            TrinityPatternCatalog.CoreMount publishedMount = host.getPatternCatalog().mountedCores().getFirst();
            if (!patternInstalled.get()) {
                helper.assertTrue(publishedMount.core().trySetPattern(0, encodedOakPlanksPattern(helper)),
                        "Single-provider probe should install in the selected physical P-core slot");
                host.serverTick();
                for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                    hatch.refreshTrinityAccess();
                }
                patternInstalled.set(true);
                return;
            }
            if (!providerMountChecked.get()) {
                PatternRoute route = new PatternRoute(host.getHostId(), publishedMount.core().coreId(), 0);
                long routeCount = grid.getCraftingService().getCraftingFor(AEItemKey.of(Items.OAK_PLANKS)).stream()
                        .filter(details -> details instanceof RoutedCraftingPatternDetails routed &&
                                routed.route().equals(route))
                        .count();
                helper.assertValueEqual(routeCount, 1L,
                        "Two hatches on one grid must publish an exact routed pattern only once");
                providerMountChecked.set(true);
            }

            if (!catalogInvalidated.get()) {
                helper.assertTrue(
                        Math.floorMod(level.getGameTime() + origin.asLong(), 100) != 0,
                        "Waiting for a non-periodic host tick to exercise catalog self-invalidation");
                TrinityPatternCatalog.CoreMount mount = host.getPatternCatalog().mountedCores().getFirst();
                TrinityPatternCoreImpl restoredState = new TrinityPatternCoreImpl(
                        mount.blockCapacity(),
                        UUID.randomUUID(),
                        stack -> null,
                        () -> {});
                CompoundTag restoredTag = new CompoundTag();
                restoredState.writeToTag(restoredTag, level.registryAccess());
                mount.core().readFromTag(restoredTag, level.registryAccess());
                host.serverTick();
                catalogInvalidated.set(true);
            }

            helper.assertTrue(!host.getPatternCatalog().layoutSnapshot().active(),
                    "A mounted P-core identity change must invalidate the authoritative catalog layout");
            helper.assertTrue(!host.isPatternProviderAvailable(),
                    "Catalog self-invalidation must withdraw pattern capabilities");
            helper.assertTrue(host.accessGrid() == grid,
                    "Catalog self-invalidation must retain the host storage grid");
            for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                if (host.isLeaseOwner(hatch)) {
                    helper.assertTrue(hatch.accessGrid() == grid,
                            "Catalog self-invalidation must retain hatch storage access");
                    helper.assertTrue(hatch.boundCraftingRuntime() == host.getCraftingRuntime(),
                            "Catalog self-invalidation must retain hatch virtual CPUs");
                }
                helper.assertTrue(hatch.terminalPartitions().isEmpty(),
                        "Catalog self-invalidation must detach every terminal partition");
            }
            destroyGridPower(testGridPower);
        });
    }

    @TestHolder("trinity_access_hatch_partitions_512_core_on_real_grid")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void trinityAccessHatchPartitions512CoreOnRealGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localOrigin = new BlockPos(25, 4, 25);
        BlockPos origin = helper.absolutePos(localOrigin);
        helper.setBlock(localOrigin, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        buildMainStructure(helper, level, origin);
        host.serverTick();
        List<TrinityAccessHatchBlockEntity> hatches = boundTrinityAccessHatches(host);
        helper.assertValueEqual(hatches.size(), 2, "Main structure must bind exactly two access hatches");
        buildCraftingStructure(helper, level, origin);
        host.requestStructureRecheck();
        host.serverTick();
        helper.assertTrue(host.isCraftingStructureFormed(),
                "Crafting structure should form before terminal partition integration");

        TrinityPatternCatalog.CoreMount originalMount = host.getPatternCatalog().mountedCores().getFirst();
        BlockPos upgradedCorePosition = originalMount.position();
        level.setBlock(
                upgradedCorePosition,
                ModBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        host.requestStructureRecheck();
        host.serverTick();
        TrinityPatternCatalog.CoreMount upgradedMount = host.getPatternCatalog().mountedCores().stream()
                .filter(mount -> mount.position().equals(upgradedCorePosition))
                .findFirst()
                .orElseThrow();
        helper.assertValueEqual(upgradedMount.blockCapacity(), 512,
                "Selected physical P core should expose the tier-3 capacity");
        if (!(upgradedMount.core() instanceof TrinityPatternCoreBlockEntity physicalCore)) {
            helper.fail("Expected the upgraded terminal mount to use a physical Trinity P-core block entity");
            return;
        }

        AtomicReference<TestGridPower> testGridPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(testGridPower));
        AtomicBoolean patternsWritten = new AtomicBoolean();
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        int[] partitionIndexes = { 0, 0, 1, 3 };
        int[] partitionSlots = { 0, 127, 0, 127 };
        int[] physicalSlots = { 0, 127, 128, 511 };

        helper.succeedWhen(() -> {
            connectAccessHatches(helper, level, hatches, testGridPower);
            host.serverTick();
            for (TrinityAccessHatchBlockEntity hatch : hatches) {
                hatch.refreshTrinityAccess();
            }
            assertSingleLeaseOwner(helper, host, hatches);
            TrinityAccessHatchBlockEntity leaseHatch = hatches.stream()
                    .filter(host::isLeaseOwner)
                    .findFirst()
                    .orElseThrow();
            IGrid grid = host.accessGrid();
            helper.assertTrue(grid != null, "Lease hatch should expose its powered AE grid");

            List<TrinityPatternTerminalPartition> corePartitions = new ArrayList<>();
            for (var partition : leaseHatch.terminalPartitions()) {
                if (partition.key().coreId().equals(physicalCore.coreId())) {
                    corePartitions.add(partition);
                }
            }
            helper.assertValueEqual(corePartitions.size(), 4,
                    "One 512-slot P core must publish exactly four PatternContainers");
            for (int partitionIndex = 0; partitionIndex < corePartitions.size(); partitionIndex++) {
                TrinityPatternTerminalPartition partition = corePartitions.get(partitionIndex);
                helper.assertValueEqual(partition.key().partitionIndex(), partitionIndex,
                        "Tier-3 P-core terminal partitions must retain stable indexes");
                helper.assertValueEqual(partition.firstCoreSlot(), partitionIndex * 128,
                        "Tier-3 P-core terminal partitions must cover contiguous physical ranges");
            }
            List<PatternContainer> coreContainers = new ArrayList<>(corePartitions);
            var terminalGroup = coreContainers.getFirst().getTerminalGroup();
            for (PatternContainer container : coreContainers) {
                helper.assertValueEqual(container.getTerminalPatternInventory().size(), 128,
                        "Every tier-3 P-core terminal partition must expose exactly 128 slots");
                helper.assertTrue(container.getGrid() == grid,
                        "Every published PatternContainer must belong to the lease grid");
                helper.assertTrue(container.isVisibleInTerminal(),
                        "Every current PatternContainer must be visible to the pattern terminal");
                helper.assertTrue(container.getTerminalGroup() == terminalGroup,
                        "All partitions of one P core must share one terminal group");
            }

            if (patternsWritten.compareAndSet(false, true)) {
                for (int index = 0; index < physicalSlots.length; index++) {
                    ItemStack remainder = coreContainers.get(partitionIndexes[index])
                            .getTerminalPatternInventory()
                            .insertItem(partitionSlots[index], encodedPattern.copy(), false);
                    helper.assertTrue(remainder.isEmpty(),
                            "Terminal partition should accept boundary write for physical slot " + physicalSlots[index]);
                }
            }

            int installedPatternCount = 0;
            for (int slot = 0; slot < physicalCore.patternCapacity(); slot++) {
                ItemStack installed = physicalCore.pattern(slot);
                if (!installed.isEmpty()) {
                    installedPatternCount++;
                }
            }
            helper.assertValueEqual(installedPatternCount, 4,
                    "Four terminal boundary writes must reach four distinct physical slots");
            for (int physicalSlot : physicalSlots) {
                ItemStack installed = physicalCore.pattern(physicalSlot);
                helper.assertTrue(
                        ItemStack.isSameItemSameComponents(installed, encodedPattern) &&
                                installed.getCount() == encodedPattern.getCount(),
                        "Terminal partition boundary must map to physical P-core slot " + physicalSlot);
            }
            destroyGridPower(testGridPower);
        });
    }

    @TestHolder("trinity_access_hatch_idle_host_switches_only_after_owner_grid_goes_offline")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void idleHostSwitchesOnlyAfterOwnerGridGoesOffline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localOrigin = new BlockPos(25, 4, 25);
        BlockPos origin = helper.absolutePos(localOrigin);
        helper.setBlock(localOrigin, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        buildMainStructure(helper, level, origin);
        host.serverTick();
        List<TrinityAccessHatchBlockEntity> hatches = boundTrinityAccessHatches(host);
        helper.assertValueEqual(hatches.size(), 2, "Main structure must bind exactly two access hatches");
        TrinityAccessHatchBlockEntity initialOwner = hatches.stream()
                .max((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .orElseThrow();
        TrinityAccessHatchBlockEntity competitor = hatches.stream()
                .min((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .orElseThrow();
        AtomicReference<TestGridPower> ownerPower = new AtomicReference<>();
        AtomicReference<TestGridPower> competitorPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(ownerPower, competitorPower));
        AtomicReference<IGrid> ownerGrid = new AtomicReference<>();
        AtomicReference<IGrid> competitorGrid = new AtomicReference<>();
        AtomicBoolean ownerConnected = new AtomicBoolean();
        AtomicBoolean competitorConnected = new AtomicBoolean();
        AtomicBoolean storageSeeded = new AtomicBoolean();
        AtomicBoolean ownerDisconnected = new AtomicBoolean();
        AEItemKey leaseProbe = AEItemKey.of(Items.IRON_INGOT);

        helper.succeedWhen(() -> {
            IGridNode ownerNode = initialOwner.getMainNode().getNode();
            IGridNode competitorNode = competitor.getMainNode().getNode();
            helper.assertTrue(ownerNode != null && competitorNode != null,
                    "Both independent-grid access nodes must finish AE initialization");

            if (!ownerConnected.get()) {
                TestGridPower power = new TestGridPower(level);
                power.connect(ownerNode);
                ownerPower.set(power);
                ownerConnected.set(true);
                return;
            }
            host.serverTick();
            for (TrinityAccessHatchBlockEntity hatch : hatches) {
                hatch.refreshTrinityAccess();
            }
            helper.assertTrue(host.isLeaseOwner(initialOwner),
                    "The first online hatch should receive the initial lease");

            if (!competitorConnected.get()) {
                ownerGrid.set(initialOwner.connectedGrid());
                TestGridPower power = new TestGridPower(level);
                power.connect(competitorNode);
                competitorPower.set(power);
                competitorConnected.set(true);
                return;
            }
            host.serverTick();
            for (TrinityAccessHatchBlockEntity hatch : hatches) {
                hatch.refreshTrinityAccess();
            }
            competitorGrid.set(competitor.connectedGrid());
            helper.assertTrue(ownerGrid.get() != null && competitorGrid.get() != null &&
                    ownerGrid.get() != competitorGrid.get(),
                    "Idle switch test requires two independent AE grids");
            helper.assertTrue(host.isLeaseOwner(initialOwner),
                    "A later lower-coordinate grid must not replace an online sticky lease");
            helper.assertTrue(!host.isLeaseOwner(competitor),
                    "The second online grid must remain outside the sticky lease");

            if (!storageSeeded.get()) {
                long inserted = TrinityDataCoreStorageSavedData.get(level.getServer()).insert(
                        host.getStorageId(),
                        leaseProbe,
                        5L,
                        Actionable.MODULATE);
                helper.assertValueEqual(inserted, 5L, "Idle switch probe should enter host UUID storage");
                storageSeeded.set(true);
                for (TrinityAccessHatchBlockEntity hatch : hatches) {
                    hatch.refreshTrinityAccess();
                }
                return;
            }

            if (!ownerDisconnected.get()) {
                helper.assertValueEqual(availableAmount(ownerGrid.get(), leaseProbe), 5L,
                        "Sticky owner grid should expose the host storage once");
                helper.assertValueEqual(availableAmount(competitorGrid.get(), leaseProbe), 0L,
                        "Non-owning grid must not expose the host storage");
                ownerPower.get().destroy();
                ownerPower.set(null);
                ownerDisconnected.set(true);
                return;
            }

            host.serverTick();
            for (TrinityAccessHatchBlockEntity hatch : hatches) {
                hatch.refreshTrinityAccess();
            }
            helper.assertTrue(host.isLeaseOwner(competitor),
                    "An idle host should switch to the remaining online hatch after its owner grid goes offline");
            helper.assertTrue(host.accessGrid() == competitorGrid.get(),
                    "The switched idle lease should expose only the competitor grid");
            helper.assertValueEqual(availableAmount(ownerGrid.get(), leaseProbe), 0L,
                    "Offline former owner grid must withdraw the host storage mount");
            helper.assertValueEqual(availableAmount(competitorGrid.get(), leaseProbe), 5L,
                    "New owner grid should mount the host storage exactly once");
            destroyGridPower(competitorPower);
        });
    }

    @TestHolder("trinity_access_hatch_withdraws_only_pattern_capabilities_on_crafting_failure")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void trinityAccessHatchWithdrawsCapabilitiesButRetainsWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localOrigin = new BlockPos(25, 4, 25);
        BlockPos origin = helper.absolutePos(localOrigin);
        helper.setBlock(localOrigin, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        buildMainStructure(helper, level, origin);
        host.serverTick();
        List<TrinityAccessHatchBlockEntity> hatches = boundTrinityAccessHatches(host);
        helper.assertTrue(!hatches.isEmpty(), "Complete Trinity structure should bind at least one access hatch");
        buildCpuStructure(helper, level, origin);
        buildCraftingStructure(helper, level, origin);
        host.requestStructureRecheck();
        host.serverTick();
        for (TrinityAccessHatchBlockEntity hatch : hatches) {
            hatch.refreshTrinityAccess();
        }
        AtomicReference<TestGridPower> testGridPower = new AtomicReference<>();
        AtomicBoolean invalidated = new AtomicBoolean();
        AtomicReference<List<TrinityDataCoreVirtualCpu>> retainedCpuPartitions = new AtomicReference<>();
        AtomicReference<TrinityPatternCatalog.CoreMount> retainedPatternMount = new AtomicReference<>();
        AtomicReference<InternalInventory> staleTerminalInventory = new AtomicReference<>();
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        helper.succeedWhen(() -> {
            if (!invalidated.get()) {
                connectAccessHatches(helper, level, hatches, testGridPower);
                host.serverTick();
                for (TrinityAccessHatchBlockEntity hatch : hatches) {
                    hatch.refreshTrinityAccess();
                }
                helper.assertTrue(host.accessGrid() != null,
                        "Complete Trinity structure should expose its owner grid before invalidation");
                assertSingleLeaseOwner(helper, host, hatches);
                retainedCpuPartitions.set(host.getCraftingRuntime().partitions());
                helper.assertTrue(!retainedCpuPartitions.get().isEmpty(),
                        "Formed CPU structure should expose at least one virtual CPU partition");
                helper.assertTrue(hatches.stream().filter(host::isLeaseOwner)
                        .flatMap(hatch -> hatch.terminalPartitions().stream())
                        .allMatch(partition -> partition.isAttachedTo(host.accessGrid())),
                        "Terminal partitions should be mounted before capability invalidation");
                TrinityPatternCatalog.CoreMount mount = host.getPatternCatalog().mountedCores().getFirst();
                retainedPatternMount.set(mount);
                TrinityAccessHatchBlockEntity leaseHatch = hatches.stream()
                        .filter(host::isLeaseOwner)
                        .findFirst()
                        .orElseThrow();
                InternalInventory partitionInventory = leaseHatch.terminalPartitions().stream()
                        .filter(partition -> partition.key().coreId().equals(mount.core().coreId()))
                        .findFirst()
                        .orElseThrow()
                        .getTerminalPatternInventory();
                staleTerminalInventory.set(partitionInventory);
                helper.assertTrue(partitionInventory.insertItem(0, encodedPattern, false).isEmpty(),
                        "Current terminal partition should write through to its exact physical P-core slot");
                helper.assertTrue(ItemStack.isSameItemSameComponents(mount.core().pattern(0), encodedPattern),
                        "Current terminal write should install the encoded pattern in its routed core");
                PatternRoute route = new PatternRoute(host.getHostId(), mount.core().coreId(), 0);
                mount.core().appendPendingOutputs(route, List.of(new ItemStack(Items.DIAMOND)));

                TrinityPatternCatalog.CoreMount duplicateTarget = host.getPatternCatalog().mountedCores().stream()
                        .filter(candidate -> candidate.core() != mount.core() &&
                                candidate.blockCapacity() == mount.blockCapacity())
                        .findFirst()
                        .orElseThrow();
                CompoundTag duplicateState = new CompoundTag();
                mount.core().writeToTag(duplicateState, level.registryAccess());
                duplicateTarget.core().readFromTag(duplicateState, level.registryAccess());
                host.requestStructureRecheck();
                host.serverTick();
                invalidated.set(true);
            }

            TrinityPatternCatalog.CoreMount mount = retainedPatternMount.get();
            PatternRoute route = new PatternRoute(host.getHostId(), mount.core().coreId(), 0);
            helper.assertTrue(host.isCpuStructureFormed(),
                    "Crafting catalog scan rejection must not discard the valid CPU child profile");
            helper.assertTrue(!host.isCraftingStructureFormed(),
                    "Duplicate P-core UUIDs must reject the crafting child structure scan");
            helper.assertTrue(host.getCraftingLastFailureReason().contains("Duplicate Trinity pattern core UUID"),
                    "Crafting scan rejection must preserve its duplicate P-core UUID diagnostic");
            helper.assertTrue(!host.isPatternProviderAvailable(),
                    "Pattern provider must be unavailable while its crafting structure is invalid");
            helper.assertTrue(host.accessGrid() != null,
                    "Crafting structure failure must retain storage access");
            helper.assertTrue(host.getPatternCatalog().hasWork(),
                    "Invalidation must retain route-owned work for lease locking");
            helper.assertTrue(host.getPatternCatalog().mountedCores().isEmpty(),
                    "Invalidation must immediately hide every public catalog mount");
            helper.assertValueEqual(mount.core().pendingOutputs(route).size(), 1,
                    "Invalidation must retain pending route outputs");
            InternalInventory staleInventory = staleTerminalInventory.get();
            helper.assertTrue(staleInventory.getStackInSlot(0).isEmpty(),
                    "A captured terminal inventory must read empty immediately after layout invalidation");
            helper.assertTrue(staleInventory.extractItem(0, 1, false).isEmpty(),
                    "A captured terminal inventory must reject extraction after layout invalidation");
            ItemStack rejected = staleInventory.insertItem(0, encodedPattern, false);
            helper.assertTrue(ItemStack.isSameItemSameComponents(rejected, encodedPattern) &&
                    rejected.getCount() == encodedPattern.getCount(),
                    "A captured terminal inventory must return the complete offered pattern after invalidation");
            staleInventory.setItemDirect(0, ItemStack.EMPTY);
            helper.assertTrue(ItemStack.isSameItemSameComponents(mount.core().pattern(0), encodedPattern),
                    "A captured terminal inventory must not clear its former physical core after invalidation");
            List<TrinityDataCoreVirtualCpu> currentCpuPartitions = host.getCraftingRuntime().partitions();
            helper.assertValueEqual(currentCpuPartitions.size(), retainedCpuPartitions.get().size(),
                    "Temporary CPU structure failure must retain every virtual CPU partition");
            for (int index = 0; index < currentCpuPartitions.size(); index++) {
                helper.assertTrue(currentCpuPartitions.get(index) == retainedCpuPartitions.get().get(index),
                        "Temporary CPU structure failure must not rebuild or cancel CPU partition " + index);
            }
            helper.assertValueEqual(hatches.stream().filter(host::isLeaseOwner).count(), 1L,
                    "Pending work must keep the original grid lease owner");
            for (TrinityAccessHatchBlockEntity hatch : hatches) {
                if (host.isLeaseOwner(hatch)) {
                    helper.assertTrue(hatch.accessGrid() == host.accessGrid(),
                            "Crafting structure failure must retain lease-owner storage access");
                    helper.assertTrue(hatch.boundCraftingRuntime() == host.getCraftingRuntime(),
                            "Crafting structure failure must retain virtual crafting CPUs");
                }
                helper.assertTrue(hatch.terminalPartitions().isEmpty(),
                        "Crafting structure failure must detach pattern terminal partitions");
            }
            testGridPower.get().destroy();
        });
    }

    @TestHolder("trinity_access_hatch_busy_host_nbt_rebuild_keeps_non_default_grid_lease")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void busyHostNbtRebuildKeepsNonDefaultGridLease(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localOrigin = new BlockPos(25, 4, 25);
        BlockPos origin = helper.absolutePos(localOrigin);
        helper.setBlock(localOrigin, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        buildMainStructure(helper, level, origin);
        host.serverTick();
        List<TrinityAccessHatchBlockEntity> hatches = boundTrinityAccessHatches(host);
        helper.assertTrue(!hatches.isEmpty(), "Complete Trinity structure should bind at least one access hatch");
        buildCpuStructure(helper, level, origin);
        buildCraftingStructure(helper, level, origin);
        host.requestStructureRecheck();
        host.serverTick();
        for (TrinityAccessHatchBlockEntity hatch : hatches) {
            hatch.refreshTrinityAccess();
        }

        helper.assertValueEqual(hatches.size(), 2, "Complete Trinity main structure must bind exactly two access hatches");
        TrinityAccessHatchBlockEntity intendedOwner = hatches.stream()
                .max((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .orElseThrow();
        TrinityAccessHatchBlockEntity intendedCompetitor = hatches.stream()
                .min((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .orElseThrow();
        AtomicReference<TestGridPower> ownerPower = new AtomicReference<>();
        AtomicReference<TestGridPower> competitorPower = new AtomicReference<>();
        AtomicReference<TrinityDataCoreBlockEntity> currentHost = new AtomicReference<>(host);
        AtomicReference<TrinityAccessHatchBlockEntity> originalLeaseHatch = new AtomicReference<>();
        AtomicReference<TrinityAccessHatchBlockEntity> competingHatch = new AtomicReference<>();
        AtomicReference<IGrid> originalLeaseGrid = new AtomicReference<>();
        AtomicReference<IGrid> competingGrid = new AtomicReference<>();
        AtomicBoolean leasePrepared = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        AtomicBoolean ownerOffline = new AtomicBoolean();
        AtomicBoolean ownerReconnected = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (!leasePrepared.get()) {
                IGridNode ownerNode = intendedOwner.getMainNode().getNode();
                IGridNode competitorNode = intendedCompetitor.getMainNode().getNode();
                helper.assertTrue(ownerNode != null && competitorNode != null,
                        "Both Trinity access nodes must finish AE initialization");

                if (ownerPower.get() == null) {
                    TestGridPower power = new TestGridPower(level);
                    power.connect(ownerNode);
                    ownerPower.set(power);
                    return;
                }
                TrinityDataCoreBlockEntity activeHost = currentHost.get();
                activeHost.serverTick();
                for (TrinityAccessHatchBlockEntity hatch : hatches) {
                    hatch.refreshTrinityAccess();
                }
                helper.assertTrue(activeHost.isLeaseOwner(intendedOwner),
                        "The only online hatch must receive the initial lease");

                if (competitorPower.get() == null) {
                    TestGridPower power = new TestGridPower(level);
                    power.connect(competitorNode);
                    competitorPower.set(power);
                    return;
                }
                activeHost.serverTick();
                for (TrinityAccessHatchBlockEntity hatch : hatches) {
                    hatch.refreshTrinityAccess();
                }
                helper.assertTrue(activeHost.isLeaseOwner(intendedOwner),
                        "A later lower-coordinate hatch must not replace the active lease");
                helper.assertTrue(!activeHost.isLeaseOwner(intendedCompetitor),
                        "The later competing hatch must remain outside the active lease");
                IGrid ownerGrid = intendedOwner.connectedGrid();
                IGrid otherGrid = intendedCompetitor.connectedGrid();
                helper.assertTrue(ownerGrid != null && otherGrid != null && ownerGrid != otherGrid,
                        "Lease reconstruction test requires two independently powered AE grids");
                helper.assertTrue(!activeHost.getPatternCatalog().mountedCores().isEmpty(),
                        "Complete Trinity structure must publish a P-core before host reconstruction");
                TrinityPatternCatalog.CoreMount mount = activeHost.getPatternCatalog().mountedCores().getFirst();
                PatternRoute route = new PatternRoute(activeHost.getHostId(), mount.core().coreId(), 0);
                mount.core().appendPendingOutputs(route, List.of(new ItemStack(Items.DIAMOND)));
                helper.assertTrue(activeHost.getPatternCatalog().hasWork(),
                        "P-core work must lock the non-default lease before host reconstruction");
                originalLeaseHatch.set(intendedOwner);
                competingHatch.set(intendedCompetitor);
                originalLeaseGrid.set(ownerGrid);
                competingGrid.set(otherGrid);
                CompoundTag saved = new CompoundTag();
                activeHost.saveAdditional(saved, level.registryAccess());
                activeHost.onChunkUnloaded();
                level.removeBlockEntity(origin);

                BlockState hostState = level.getBlockState(origin);
                TrinityDataCoreBlockEntity loadedHost = new TrinityDataCoreBlockEntity(origin, hostState);
                loadedHost.loadTag(saved, level.registryAccess());
                level.setBlockEntity(loadedHost);
                loadedHost.onLoad();
                loadedHost.serverTick();
                currentHost.set(loadedHost);
                leasePrepared.set(true);
                restored.set(true);
                return;
            }

            TrinityDataCoreBlockEntity restoredHost = currentHost.get();
            TrinityAccessHatchBlockEntity owner = originalLeaseHatch.get();
            TrinityAccessHatchBlockEntity competitor = competingHatch.get();
            IGrid ownerGrid = originalLeaseGrid.get();
            IGrid otherGrid = competingGrid.get();
            if (!ownerOffline.get()) {
                helper.assertTrue(restoredHost != host,
                        "Host reconstruction must exercise a newly created block entity instance");
                helper.assertTrue(restoredHost.getPatternCatalog().layoutSnapshot().active(),
                        "Reconstructed host must rebuild its authoritative pattern layout");
                helper.assertTrue(restoredHost.isStorageAvailable() && restoredHost.isCpuProviderAvailable() &&
                        restoredHost.isPatternProviderAvailable(),
                        "Reconstructed complete host must restore all independently gated capabilities");
                helper.assertTrue(restoredHost.isLeaseOwner(owner),
                        "Reconstructed busy host must restore the persisted non-default hatch identity");
                helper.assertTrue(!restoredHost.isLeaseOwner(competitor),
                        "Reconstructed busy host must not elect the lower-coordinate online hatch");
                helper.assertTrue(restoredHost.accessGrid() == ownerGrid,
                        "Reconstructed busy host must bind only to the persisted hatch's grid");
                helper.assertTrue(restoredHost.accessGrid() != otherGrid,
                        "Reconstructed busy host must never migrate retained work to the competing grid");

                TrinityPatternCatalog.CoreMount mount = restoredHost.getPatternCatalog().mountedCores().getFirst();
                PatternRoute route = new PatternRoute(restoredHost.getHostId(), mount.core().coreId(), 0);
                mount.core().appendPendingOutputs(route, List.of(new ItemStack(Items.EMERALD)));
                ownerPower.get().destroy();
                ownerPower.set(null);
                ownerOffline.set(true);
                return;
            }

            if (!ownerReconnected.get()) {
                restoredHost.serverTick();
                helper.assertTrue(restoredHost.getPatternCatalog().hasWork(),
                        "Offline lease owner must leave its P-core work pending");
                helper.assertTrue(restoredHost.accessGrid() == null,
                        "Busy host must stay offline while its persisted hatch is unavailable");
                helper.assertTrue(!restoredHost.isLeaseOwner(owner) && !restoredHost.isLeaseOwner(competitor),
                        "Competing online hatch must not take over an unavailable busy lease");
                helper.assertTrue(competitor.accessGrid() == null,
                        "Competing online hatch must expose no storage while busy work waits for the persisted hatch");

                IGridNode ownerNode = owner.getMainNode().getNode();
                helper.assertTrue(ownerNode != null, "Persisted hatch node must survive its temporary grid outage");
                TestGridPower replacementOwnerPower = new TestGridPower(level);
                replacementOwnerPower.connect(ownerNode);
                ownerPower.set(replacementOwnerPower);
                ownerReconnected.set(true);
                return;
            }

            restoredHost.serverTick();
            IGrid reboundGrid = owner.connectedGrid();
            helper.assertTrue(reboundGrid != null && reboundGrid != otherGrid,
                    "Recovered persisted hatch must bind to its own re-created runtime grid");
            helper.assertTrue(restoredHost.isLeaseOwner(owner),
                    "Recovered persisted hatch must regain the busy lease");
            helper.assertTrue(!restoredHost.isLeaseOwner(competitor),
                    "Lower-coordinate competing hatch must remain outside the recovered busy lease");
            helper.assertTrue(restoredHost.accessGrid() == reboundGrid,
                    "Recovered busy host must expose only the persisted hatch's new runtime grid");
            helper.assertTrue(owner.boundCraftingRuntime() == restoredHost.getCraftingRuntime(),
                    "Recovered persisted hatch must expose the reconstructed crafting runtime");
            helper.assertTrue(competitor.boundCraftingRuntime() == null,
                    "Competing hatch must not expose the reconstructed crafting runtime");
            helper.assertTrue(!owner.terminalPartitions().isEmpty(),
                    "Recovered persisted hatch must republish terminal partitions");
            helper.assertTrue(owner.terminalPartitions().stream().allMatch(partition -> partition.isAttachedTo(reboundGrid)),
                    "Recovered terminal partitions must attach only to the persisted hatch's current grid");
            helper.assertTrue(competitor.terminalPartitions().isEmpty(),
                    "Competing hatch must not publish duplicate terminal partitions");
            ownerPower.get().destroy();
            competitorPower.get().destroy();
        });
    }

    private static ItemStack encodedOakPlanksPattern(GameTestHelper helper) {
        RecipeHolder<?> recipe = helper.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("oak_planks"))
                .orElseThrow();
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            throw new IllegalStateException("Expected minecraft:oak_planks to be a crafting recipe");
        }
        RecipeHolder<CraftingRecipe> craftingRecipeHolder = new RecipeHolder<>(recipe.id(), craftingRecipe);
        ItemStack[] inputs = new ItemStack[9];
        inputs[0] = new ItemStack(Items.OAK_LOG);
        for (int slot = 1; slot < inputs.length; slot++) {
            inputs[slot] = ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeCraftingPattern(
                craftingRecipeHolder,
                inputs,
                new ItemStack(Items.OAK_PLANKS, 4),
                false,
                false);
    }

    private static void assertServerTickActiveState(GameTestHelper helper,
                                                    BlockPos relativePos,
                                                    BlockState state,
                                                    Predicate<BlockEntity> expectedBlockEntity,
                                                    String compartmentName) {
        helper.setBlock(relativePos, state);
        BlockPos levelPos = helper.absolutePos(relativePos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(levelPos);
        if (!expectedBlockEntity.test(blockEntity)) {
            helper.fail("Expected a " + compartmentName + " block entity", relativePos);
            return;
        }
        if (!(blockEntity instanceof CompartmentBlockEntity compartment)) {
            helper.fail("Expected " + compartmentName + " to be a compartment block entity", relativePos);
            return;
        }

        compartment.serverTick();
        assertActiveState(helper, levelPos, false, "Unbound " + compartmentName + " should stay inactive");

        TestCompartmentHost host = new TestCompartmentHost();
        compartment.compartment$bindToHost("main", host);
        compartment.serverTick();
        assertActiveState(helper, levelPos, true, "Bound " + compartmentName + " should become active");

        compartment.compartment$unbindFromHost("main", host);
        compartment.serverTick();
        assertActiveState(helper, levelPos, false, "Unbound " + compartmentName + " should become inactive again");
    }

    private static void assertTrinityAccessTickerClearsInactiveState(GameTestHelper helper, BlockPos relativePos) {
        BlockState state = ModBlocks.TRINITY_ACCESS_HATCH.get()
                .defaultBlockState()
                .setValue(CompartmentBlock.ACTIVE, true);
        helper.setBlock(relativePos, state);
        BlockPos levelPos = helper.absolutePos(relativePos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(levelPos);
        if (!(blockEntity instanceof TrinityAccessHatchBlockEntity hatch)) {
            helper.fail("Expected a Trinity access hatch block entity", relativePos);
            return;
        }

        CompartmentBlock block = (CompartmentBlock) state.getBlock();
        var ticker = block.getTicker(
                helper.getLevel(),
                state,
                ModBlockEntities.TRINITY_ACCESS_HATCH_BLOCK_ENTITY.get());
        ticker.tick(helper.getLevel(), levelPos, state, hatch);

        assertActiveState(helper, levelPos, false, "Unbound Trinity access hatch ticker should clear ACTIVE state");
    }

    private static void assertActiveState(GameTestHelper helper, BlockPos levelPos, boolean expected, String message) {
        boolean active = helper.getLevel().getBlockState(levelPos).getValue(CompartmentBlock.ACTIVE);
        helper.assertValueEqual(active, expected, message);
    }

    private static void buildMainStructure(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        buildStructure(
                helper,
                level,
                origin,
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 1),
                "main");
    }

    private static void buildCpuStructure(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        buildStructure(
                helper,
                level,
                origin,
                TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1),
                "CPU");
    }

    private static void buildCraftingStructure(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        buildStructure(
                helper,
                level,
                origin,
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1),
                "crafting");
    }

    private static void buildStructure(GameTestHelper helper,
                                       ServerLevel level,
                                       BlockPos origin,
                                       int structureIndex,
                                       Map<String, Integer> tierSelections,
                                       String structureName) {
        Result result = TrinityDataCoreBlockEntity.executeAutoBuild(
                level,
                helper.makeMockPlayer(GameType.CREATIVE),
                origin,
                Direction.SOUTH,
                false,
                new TrinityAutoBuildRequest(
                        structureIndex,
                        new TrinityAutoBuildOptions(true, 1, tierSelections)));
        helper.assertTrue(result.success(), "Trinity " + structureName + " auto-build should commit: " + result.failure());
        helper.assertTrue(result.placed() > 0, "Trinity " + structureName + " auto-build should place structure blocks");
    }

    private static BlockPos findCpuCore(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-32, -8, -32), origin.offset(32, 32, 32))) {
            if (level.getBlockState(pos).getBlock() instanceof TrinityCoreComponent component &&
                    component.kind() == TrinityCoreKind.PARALLEL_CPU) {
                return pos.immutable();
            }
        }
        throw new IllegalStateException("Auto-built Trinity CPU structure has no parallel CPU core");
    }

    private static List<TrinityAccessHatchBlockEntity> requireBuiltTrinityAccessHatches(GameTestHelper helper,
                                                                                        ServerLevel level,
                                                                                        BlockPos origin) {
        List<TrinityAccessHatchBlockEntity> hatches = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-32, -8, -32), origin.offset(32, 32, 32))) {
            if (level.getBlockEntity(pos) instanceof TrinityAccessHatchBlockEntity hatch) {
                hatches.add(hatch);
            }
        }
        if (!hatches.isEmpty()) {
            return List.copyOf(hatches);
        }
        helper.fail("Auto-built Trinity Data Core main structure should contain a Trinity access hatch");
        throw new IllegalStateException("Auto-built Trinity Data Core main structure did not place a Trinity access hatch");
    }

    private static List<TrinityAccessHatchBlockEntity> boundTrinityAccessHatches(TrinityDataCoreBlockEntity host) {
        List<TrinityAccessHatchBlockEntity> hatches = new ArrayList<>();
        for (CompartmentPart part : host.compartmentHost$getCompartments(mainStructureName())) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch) {
                hatches.add(hatch);
            }
        }
        return List.copyOf(hatches);
    }

    private static void assertTrinityAccessCableConnections(GameTestHelper helper, TrinityAccessHatchBlockEntity hatch) {
        var connectableSides = hatch.getGridConnectableSides(BlockOrientation.get(hatch.getBlockState()));
        for (Direction direction : Direction.values()) {
            helper.assertTrue(
                    connectableSides.contains(direction),
                    "Trinity access hatch should expose AE grid connection on " + direction);
            helper.assertValueEqual(
                    hatch.getCableConnectionType(direction),
                    AECableType.COVERED,
                    "Trinity access hatch cable connection should be covered on " + direction);
        }
    }

    private static void connectAccessHatches(GameTestHelper helper,
                                             ServerLevel level,
                                             List<TrinityAccessHatchBlockEntity> hatches,
                                             AtomicReference<TestGridPower> testGridPower) {
        if (testGridPower.get() != null) {
            return;
        }
        helper.assertValueEqual(hatches.size(), 2, "Trinity main structure should bind exactly two access hatches");
        IGridNode first = hatches.getFirst().getMainNode().getNode();
        IGridNode second = hatches.getLast().getMainNode().getNode();
        helper.assertTrue(first != null && second != null, "Both Trinity access nodes must finish AE initialization");
        TestGridPower power = new TestGridPower(level);
        power.connect(first);
        power.connect(second);
        testGridPower.set(power);
    }

    private static void assertSingleLeaseOwner(GameTestHelper helper,
                                               TrinityDataCoreBlockEntity host,
                                               List<TrinityAccessHatchBlockEntity> hatches) {
        TrinityAccessHatchBlockEntity expected = hatches.stream()
                .min((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .orElseThrow();
        helper.assertValueEqual(hatches.stream().filter(host::isLeaseOwner).count(), 1L,
                "Exactly one Trinity access hatch may own the network lease");
        helper.assertTrue(host.isLeaseOwner(expected),
                "Simultaneously online Trinity access hatches must elect the lowest coordinate");
    }

    private static long availableAmount(IGrid grid, AEKey key) {
        KeyCounter available = new KeyCounter();
        grid.getStorageService().getInventory().getAvailableStacks(available);
        return available.get(key);
    }

    private static void registerGridPowerCleanup(GameTestHelper helper,
                                                 List<AtomicReference<TestGridPower>> powerReferences) {
        helper.testInfo.addListener(new GameTestListener() {

            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
                // Cleanup is registered after the test structure has loaded.
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                destroyGridPowers(powerReferences);
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                destroyGridPowers(powerReferences);
            }

            @Override
            public void testAddedForRerun(GameTestInfo testInfo,
                                          GameTestInfo rerunTestInfo,
                                          GameTestRunner runner) {
                destroyGridPowers(powerReferences);
            }
        });
    }

    private static void destroyGridPowers(List<AtomicReference<TestGridPower>> powerReferences) {
        for (AtomicReference<TestGridPower> powerReference : powerReferences) {
            destroyGridPower(powerReference);
        }
    }

    private static void destroyGridPower(AtomicReference<TestGridPower> powerReference) {
        TestGridPower power = powerReference.getAndSet(null);
        if (power != null) {
            power.destroy();
        }
    }

    private static void assertHostUsesBoundTrinityAccessGrid(GameTestHelper helper,
                                                             TrinityDataCoreBlockEntity host,
                                                             List<TrinityAccessHatchBlockEntity> boundHatches) {
        IGrid hostGrid = host.accessGrid();
        if (hostGrid == null) {
            helper.fail("Formed Trinity Data Core should expose an AE grid through a bound Trinity access hatch");
            return;
        }
        for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
            if (hostGrid == hatch.accessGrid()) {
                return;
            }
        }
        helper.fail("Formed host should use the AE grid from one of its bound Trinity access hatches");
    }

    private static String mainStructureName() {
        return TrinityDataCoreBlockEntity.autoBuildStructureName(TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX);
    }

    private static final class SimpleMEStorage implements MEStorage {

        private final CompartmentStorage storage = new CompartmentStorageImpl(() -> {});

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            return this.storage.insert(what, amount, mode == Actionable.SIMULATE);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            return this.storage.extract(what, amount, mode == Actionable.SIMULATE);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (Object2LongMap.Entry<AEKey> entry : this.storage.entries().object2LongEntrySet()) {
                if (entry.getKey() != null && entry.getLongValue() > 0L) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("test ME storage");
        }

        private long amount(AEKey key) {
            return this.storage.amount(key);
        }
    }

    private static MeCompositeOutputWarehouseBlockEntity meOutputWarehouse() {
        return new MeCompositeOutputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static UpdateCountingMeOutputWarehouse updateCountingMeOutputWarehouse() {
        return new UpdateCountingMeOutputWarehouse(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static final class RecordingStorageMounts implements IStorageMounts {

        private int mountCount;
        private MEStorage mountedStorage;
        private int priority;

        @Override
        public void mount(MEStorage storage, int priority) {
            this.mountCount++;
            this.mountedStorage = storage;
            this.priority = priority;
        }

        private int mountCount() {
            return this.mountCount;
        }

        private MEStorage mountedStorage() {
            return this.mountedStorage;
        }

        private int priority() {
            return this.priority;
        }
    }

    private static final class UpdateCountingMeOutputWarehouse extends MeCompositeOutputWarehouseBlockEntity {

        private int storageUpdateRequests;

        private UpdateCountingMeOutputWarehouse(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void requestStorageUpdate() {
            this.storageUpdateRequests++;
        }

        private int storageUpdateRequests() {
            return this.storageUpdateRequests;
        }
    }

    private static final class TestGridPower implements IAEPowerStorage {

        private static final IGridNodeListener<TestGridPower> NODE_LISTENER = (owner, node) -> {};

        private final IManagedGridNode managedNode;

        private TestGridPower(ServerLevel level) {
            this.managedNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                    .setInWorldNode(false)
                    .setIdlePowerUsage(0.0D)
                    .addService(IAEPowerStorage.class, this);
            this.managedNode.create(level, null);
        }

        private void connect(IGridNode target) {
            IGridNode powerNode = this.managedNode.getNode();
            if (powerNode == null) {
                throw new IllegalStateException("Test AE power node was not created");
            }
            GridHelper.createConnection(powerNode, target);
        }

        private void destroy() {
            this.managedNode.destroy();
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            return 0.0D;
        }

        @Override
        public double getAEMaxPower() {
            return Long.MAX_VALUE / 10_000.0D;
        }

        @Override
        public double getAECurrentPower() {
            return Long.MAX_VALUE / 10_000.0D;
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return true;
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return AccessRestriction.READ_WRITE;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            return amount;
        }

        @Override
        public int getPriority() {
            return Integer.MAX_VALUE;
        }
    }

    private static final class TestCompartmentHost implements CompartmentHost {

        private final CompartmentHostState compartments = new CompartmentHostState();

        @Override
        public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
            this.compartments.addCompartment(structureName, part);
        }

        @Override
        public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
            this.compartments.removeCompartment(structureName, part);
        }

        @Override
        public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
            return this.compartments.compartments(structureName);
        }
    }
}
