package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.VirtualGridBridge;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectorBindResult;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.tower.network.TowerVirtualDeviceState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Result;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.RoutedCraftingPatternDetails;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreReloadEpoch;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternRecipeIdResolvers;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation;
import com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.State;
import com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.Structure;
import com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidationImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityStructureWorldViewFactory;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingSubmitResult;
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
import appeng.crafting.CraftingPlan;
import appeng.helpers.patternprovider.PatternContainer;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CompartmentBlockEntityTest {

    private CompartmentBlockEntityTest() {}

    @TestHolder("trinity_pattern_core_health_check_phase_is_overflow_safe")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternCoreHealthCheckPhaseIsStableAndOverflowSafe(GameTestHelper helper) {
        UUID hostId = new UUID(Long.MIN_VALUE, Long.MAX_VALUE);
        int phase = TrinityDataCoreBlockEntity.patternCoreHealthCheckPhase(hostId);

        helper.assertTrue(phase >= 0 && phase < 100, "Health-check phase must stay inside its interval");
        helper.assertTrue(
                TrinityDataCoreBlockEntity.isPatternCoreHealthCheckDue(phase, hostId),
                "Host must run at its selected phase");
        helper.assertTrue(
                TrinityDataCoreBlockEntity.isPatternCoreHealthCheckDue(phase - 100L, hostId),
                "Negative ticks must preserve the selected phase");
        helper.assertTrue(
                !TrinityDataCoreBlockEntity.isPatternCoreHealthCheckDue(phase + 1L, hostId),
                "Adjacent ticks must not run the same host check");
        helper.assertTrue(
                TrinityDataCoreBlockEntity.isPatternCoreHealthCheckDue(Long.MAX_VALUE, hostId) ==
                        (Math.floorMod(Long.MAX_VALUE, 100L) == phase),
                "Long.MAX_VALUE must be evaluated without overflowing an offset addition");
        helper.succeed();
    }

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
        }

        AtomicReference<TestGridPower> testGridPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(testGridPower));
        AtomicReference<IGrid> selectedGrid = new AtomicReference<>();
        AtomicReference<TrinityPatternCatalog.CoreMount> publishedMount = new AtomicReference<>();
        AtomicReference<PatternRoute> publishedRoute = new AtomicReference<>();
        AtomicReference<PatternRoute> withdrawnRoute = new AtomicReference<>();
        AtomicReference<List<TrinityPatternTerminalPartition>> terminalLayoutBeforePattern = new AtomicReference<>();
        AEItemKey leaseProbe = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey patternOutput = AEItemKey.of(Items.OAK_PLANKS);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    connectAccessHatches(helper, level, boundHatches, testGridPower);
                    host.serverTick();
                    assertHostUsesBoundTrinityAccessGrid(helper, host, boundHatches);
                    assertSingleLeaseOwner(helper, host, boundHatches);
                    IGrid grid = host.accessGrid();
                    helper.assertTrue(grid != null,
                            "Formed Trinity main structure should expose one powered AE grid");
                    helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.connectedGrid() == grid),
                            "Both Trinity access hatches should share one AE grid in this test");
                    helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.boundCraftingRuntime() == null),
                            "Main-only Trinity structure must not publish virtual CPUs");
                    helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.terminalPartitions().isEmpty()),
                            "Main-only Trinity structure must not publish pattern terminal partitions");
                    selectedGrid.set(grid);
                })
                .thenExecute(() -> {
                    long inserted = TrinityDataCoreStorageSavedData.get(level.getServer()).insert(
                            host.getStorageId(),
                            leaseProbe,
                            7L,
                            Actionable.MODULATE);
                    helper.assertValueEqual(inserted, 7L, "Lease probe should enter the host UUID storage");
                    for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                        hatch.refreshTrinityStorageContent();
                    }
                })
                .thenWaitUntil(() -> helper.assertValueEqual(
                        availableAmount(selectedGrid.get(), leaseProbe),
                        7L,
                        "Two hatches on one grid must mount the shared host storage exactly once"))
                .thenExecute(() -> {
                    buildCpuStructure(helper, level, origin);
                    host.requestStructureRecheck();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    helper.assertTrue(host.isCpuStructureFormed(),
                            "Auto-built Trinity CPU child structure should form: " + host.getCpuLastFailureReason());
                    helper.assertTrue(boundHatches.stream().filter(host::isLeaseOwner)
                            .anyMatch(hatch -> hatch.boundCraftingRuntime() == host.getCraftingRuntime()),
                            "CPU child structure must publish the virtual CPU runtime independently");
                    helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.terminalPartitions().isEmpty()),
                            "CPU-only child structure must not publish pattern terminal partitions");
                })
                .thenExecute(() -> {
                    buildCraftingStructure(helper, level, origin);
                    host.requestStructureRecheck();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    IGrid grid = selectedGrid.get();
                    helper.assertTrue(host.isCraftingStructureFormed(),
                            "Auto-built Trinity crafting child structure should form: " +
                                    host.getCraftingLastFailureReason());
                    List<TrinityDataCoreVirtualCpu> cpuPartitions = host.getCpuPartitions();
                    helper.assertValueEqual(
                            publishedCpuCount(grid, cpuPartitions),
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
                    publishedMount.set(host.getPatternCatalog().mountedCores().getFirst());
                    publishedRoute.set(new PatternRoute(
                            host.getHostId(), publishedMount.get().core().coreId(), 0));
                })
                .thenExecute(() -> {
                    TrinityAccessHatchBlockEntity leaseHatch = boundHatches.stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow();
                    terminalLayoutBeforePattern.set(leaseHatch.terminalPartitions());
                    helper.assertTrue(publishedMount.get().core().trySetPattern(0, encodedOakPlanksPattern(helper)),
                            "Single-provider probe should install in the selected physical P-core slot");
                    host.serverTick();
                })
                .thenWaitUntil(() -> {
                    List<TrinityPatternTerminalPartition> previousLayout = terminalLayoutBeforePattern.get();
                    List<TrinityPatternTerminalPartition> currentLayout = boundHatches.stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow()
                            .terminalPartitions();
                    helper.assertValueEqual(currentLayout.size(), previousLayout.size(),
                            "A pattern-content revision must retain the terminal layout size");
                    for (int index = 0; index < currentLayout.size(); index++) {
                        helper.assertTrue(currentLayout.get(index) == previousLayout.get(index),
                                "A pattern-content revision must retain terminal partition " + index);
                    }
                    helper.assertValueEqual(
                            publishedRouteCount(selectedGrid.get(), patternOutput, publishedRoute.get()),
                            1L,
                            "Two hatches on one grid must publish an exact routed pattern only once");
                    for (int refresh = 0; refresh < 128; refresh++) {
                        for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                            helper.assertFalse(
                                    hatch.refreshTrinityPatternPublication(),
                                    "An unchanged host/grid/pattern snapshot must not remount its AE2 provider");
                        }
                    }
                })
                .thenExecute(() -> {
                    TrinityPatternCatalog catalog = host.getPatternCatalog();
                    TrinityAccessHatchBlockEntity leaseHatch = boundHatches.stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow();
                    List<TrinityPatternTerminalPartition> partitionsBeforeReload = leaseHatch.terminalPartitions();
                    List<IPatternDetails> patternsBeforeReload = catalog.getAvailablePatterns();
                    RoutedCraftingPatternDetails routeBeforeReload = (RoutedCraftingPatternDetails) patternsBeforeReload.getFirst();
                    long publicationBeforeReload = catalog.publicationRevision();

                    TrinityPatternCoreReloadEpoch.advance();
                    publishedMount.get().core().ensurePatternCachesCurrent();
                    host.serverTick();

                    helper.assertValueEqual(catalog.publicationRevision(), publicationBeforeReload,
                            "A semantically unchanged reload must retain the host publication revision");
                    helper.assertTrue(catalog.getAvailablePatterns() == patternsBeforeReload,
                            "A semantically unchanged reload must retain the aggregate pattern snapshot");
                    helper.assertTrue(catalog.getAvailablePatterns().getFirst() == routeBeforeReload,
                            "A semantically unchanged reload must retain the routed pattern identity");
                    assertSameTerminalPartitions(
                            helper,
                            partitionsBeforeReload,
                            leaseHatch.terminalPartitions(),
                            "A semantically unchanged reload");
                    for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                        helper.assertFalse(
                                hatch.refreshTrinityPatternPublication(),
                                "A semantically unchanged reload must not remount its AE2 provider");
                    }

                    long publicationBeforeMove = catalog.publicationRevision();
                    InternalInventory patterns = publishedMount.get().core().patternInventory();
                    ItemStack moved = patterns.extractItem(0, 1, false);
                    helper.assertTrue(!moved.isEmpty(), "The published pattern must leave its source slot");
                    helper.assertTrue(patterns.insertItem(1, moved, false).isEmpty(),
                            "The published pattern must enter its target slot atomically");
                    host.serverTick();

                    PatternRoute previousRoute = publishedRoute.get();
                    PatternRoute movedRoute = new PatternRoute(
                            host.getHostId(), publishedMount.get().core().coreId(), 1);
                    helper.assertValueEqual(catalog.publicationRevision(), publicationBeforeMove + 1L,
                            "Two dirty slots from one move must produce one aggregate publication revision");
                    helper.assertValueEqual(catalog.getAvailablePatterns().size(), 1,
                            "Moving one pattern must retain exactly one published route");
                    RoutedCraftingPatternDetails movedDetails = (RoutedCraftingPatternDetails) catalog.getAvailablePatterns().getFirst();
                    helper.assertValueEqual(movedDetails.route(), movedRoute,
                            "The moved pattern must publish its exact target slot");
                    assertSameTerminalPartitions(
                            helper,
                            partitionsBeforeReload,
                            leaseHatch.terminalPartitions(),
                            "A same-core pattern move");
                    withdrawnRoute.set(previousRoute);
                    publishedRoute.set(movedRoute);
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(
                            publishedRouteCount(selectedGrid.get(), patternOutput, withdrawnRoute.get()),
                            0L,
                            "The moved pattern must withdraw its source route");
                    helper.assertValueEqual(
                            publishedRouteCount(selectedGrid.get(), patternOutput, publishedRoute.get()),
                            1L,
                            "The moved pattern must publish its target route exactly once");
                    assertSameTerminalPartitions(
                            helper,
                            terminalLayoutBeforePattern.get(),
                            boundHatches.stream()
                                    .filter(host::isLeaseOwner)
                                    .findFirst()
                                    .orElseThrow()
                                    .terminalPartitions(),
                            "A flushed same-core pattern move");
                    for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                        helper.assertFalse(
                                hatch.refreshTrinityPatternPublication(),
                                "A flushed same-tick move must leave no duplicate provider refresh pending");
                    }
                })
                .thenExecute(() -> {
                    IGrid grid = selectedGrid.get();
                    TrinityAccessHatchBlockEntity leaseHatch = boundHatches.stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow();
                    IGridNode leaseNode = leaseHatch.getMainNode().getNode();
                    helper.assertTrue(leaseNode != null, "The lease owner must retain an initialized grid node");
                    List<TrinityPatternTerminalPartition> terminalLayoutBeforeStorage = leaseHatch.terminalPartitions();
                    List<IPatternDetails> patternSnapshotBeforeStorage = host.getPatternCatalog().getAvailablePatterns();
                    long publicationBeforeStorage = host.getPatternCatalog().publicationRevision();
                    TrinityCraftingRuntimeRegistry registry = runtimeRegistry(grid);
                    helper.assertTrue(!registry.data_energistics$publish(leaseNode, host.getCraftingRuntime()),
                            "The CPU publication must exist before storage feedback");
                    long routeCountBeforeStorage = publishedRouteCount(grid, patternOutput, publishedRoute.get());
                    helper.assertValueEqual(routeCountBeforeStorage, 1L,
                            "The routed pattern must exist before storage feedback");

                    AEItemKey feedbackProbe = AEItemKey.of(Items.DIAMOND);
                    MEStorage networkStorage = grid.getStorageService().getInventory();
                    long inserted = networkStorage.insert(
                            feedbackProbe, 3L, Actionable.MODULATE, host.accessActionSource());
                    helper.assertValueEqual(inserted, 3L,
                            "Grid storage feedback probe must enter the Trinity storage");
                    helper.assertValueEqual(availableAmount(grid, feedbackProbe), 3L,
                            "Inserted grid storage feedback must be immediately visible");
                    long extracted = networkStorage.extract(
                            feedbackProbe, 3L, Actionable.MODULATE, host.accessActionSource());
                    helper.assertValueEqual(extracted, 3L,
                            "Grid storage feedback probe must leave the Trinity storage");
                    helper.assertValueEqual(availableAmount(grid, feedbackProbe), 0L,
                            "Extracted grid storage feedback must be immediately absent");

                    helper.assertTrue(!registry.data_energistics$publish(leaseNode, host.getCraftingRuntime()),
                            "Storage feedback must retain the exact CPU registry publication");
                    helper.assertValueEqual(
                            publishedCpuCount(grid, host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Storage feedback must retain every Trinity CPU exactly once");
                    List<TrinityPatternTerminalPartition> terminalLayoutAfterStorage = leaseHatch.terminalPartitions();
                    helper.assertValueEqual(terminalLayoutAfterStorage.size(), terminalLayoutBeforeStorage.size(),
                            "Storage feedback must retain the terminal layout size");
                    for (int index = 0; index < terminalLayoutAfterStorage.size(); index++) {
                        helper.assertTrue(terminalLayoutAfterStorage.get(index) == terminalLayoutBeforeStorage.get(index),
                                "Storage feedback must retain terminal partition " + index);
                    }
                    helper.assertValueEqual(
                            publishedRouteCount(grid, patternOutput, publishedRoute.get()),
                            routeCountBeforeStorage,
                            "Storage feedback must retain the routed pattern publication");
                    helper.assertTrue(host.getPatternCatalog().getAvailablePatterns() == patternSnapshotBeforeStorage,
                            "Storage feedback must retain the aggregate pattern snapshot identity");
                    helper.assertValueEqual(
                            host.getPatternCatalog().publicationRevision(),
                            publicationBeforeStorage,
                            "Storage feedback must retain the pattern publication revision");
                    for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                        helper.assertFalse(
                                hatch.refreshTrinityPatternPublication(),
                                "Storage feedback must not remount an unchanged pattern snapshot");
                    }
                })
                .thenExecute(() -> {
                    TrinityPatternCatalog.CoreMount mount = host.getPatternCatalog().mountedCores().getFirst();
                    TrinityPatternCoreImpl restoredState = new TrinityPatternCoreImpl(
                            mount.blockCapacity(),
                            UUID.randomUUID(),
                            stack -> null,
                            TrinityPatternRecipeIdResolvers.global(),
                            change -> {});
                    CompoundTag restoredTag = new CompoundTag();
                    restoredState.writeToTag(restoredTag, level.registryAccess());
                    mount.core().readFromTag(restoredTag, level.registryAccess());
                })
                .thenExecute(() -> {
                    IGrid grid = selectedGrid.get();
                    helper.assertTrue(!host.getPatternCatalog().layoutSnapshot().active(),
                            "A mounted P-core identity change must invalidate the authoritative catalog layout");
                    helper.assertTrue(!host.isPatternProviderAvailable(),
                            "Catalog self-invalidation must withdraw pattern capabilities");
                    helper.assertTrue(host.accessGrid() == grid,
                            "Catalog self-invalidation must retain the host storage grid");
                    helper.assertValueEqual(availableAmount(grid, leaseProbe), 7L,
                            "Catalog self-invalidation must retain the single storage mount");
                    helper.assertValueEqual(
                            publishedCpuCount(grid, host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Catalog self-invalidation must retain every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(grid, patternOutput, publishedRoute.get()),
                            0L,
                            "Catalog self-invalidation must withdraw its stale routed pattern");
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
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    IGrid grid = selectedGrid.get();
                    helper.assertTrue(host.getPatternCatalog().layoutSnapshot().active(),
                            "Requested structure recheck must rebuild the catalog with the new P-core identity");
                    helper.assertTrue(host.isPatternProviderAvailable(),
                            "Rebuilt catalog must restore pattern-provider availability");
                    helper.assertValueEqual(availableAmount(grid, leaseProbe), 7L,
                            "Catalog rebuild must retain the single storage mount");
                    helper.assertValueEqual(
                            publishedCpuCount(grid, host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Catalog rebuild must retain every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(grid, patternOutput, publishedRoute.get()),
                            0L,
                            "Catalog rebuild must not restore the stale pre-identity-change route");
                    helper.assertTrue(boundHatches.stream()
                            .filter(host::isLeaseOwner)
                            .flatMap(hatch -> hatch.terminalPartitions().stream())
                            .allMatch(partition -> partition.isAttachedTo(grid)),
                            "Catalog rebuild must reattach terminal partitions to the lease grid");
                })
                .thenExecute(() -> destroyGridPower(testGridPower))
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_exposes_online_trinity_access_hatch_storage")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void dataDistributionTowerExposesOnlineTrinityAccessHatchStorage(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        TrinityAccessHatchBlockEntity hatch = fixture.accessHatches().getFirst();
        DataDistributionTowerBlockEntity tower = placeTowerNearAccessHatch(helper, hatch.getBlockPos());
        AtomicReference<TestGridPower> towerPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(towerPower));
        AEItemKey probe = AEItemKey.of(Items.EMERALD);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenWaitUntil(() -> {
                    IGridNode towerNode = tower.getMainNode().getNode();
                    if (towerNode == null) {
                        throw new GameTestAssertException("Data distribution tower node is still initializing");
                    }
                    if (towerPower.get() == null) {
                        TestGridPower power = new TestGridPower(helper.getLevel());
                        power.connect(towerNode);
                        towerPower.set(power);
                    }
                    helper.assertTrue(towerNode.isActive(), "Powered data distribution tower must be active");
                    helper.assertTrue(hatch.getMainNode().getNode().isOnline(),
                            "Trinity access hatch must already be online on its original grid");
                })
                .thenExecute(() -> {
                    long inserted = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer()).insert(
                            host.getStorageId(),
                            probe,
                            11L,
                            Actionable.MODULATE);
                    helper.assertValueEqual(inserted, 11L, "Tower bridge probe must enter Trinity storage");
                    hatch.refreshTrinityStorageContent();

                    ConnectorBindResult result = tower.bindTargetFromConnector(hatch.getBlockPos());
                    helper.assertTrue(result.success(), "Data distribution tower must bind the ME access hatch");
                    helper.assertTrue(result.aeSupported(), "ME access hatch binding must expose an AE node");
                })
                .thenWaitUntil(() -> {
                    hatch.serverTick();
                    host.serverTick();
                    IGridNode towerNode = tower.getMainNode().getNode();
                    IGridNode hatchNode = hatch.getMainNode().getNode();
                    IGrid towerGrid = towerNode.getGrid();
                    TargetTransferInfo transferInfo = tower.getTargetTransferInfo(hatch.getBlockPos());
                    int expectedChannels = hatchNode.hasFlag(GridFlags.REQUIRE_CHANNEL) ? 1 : 0;
                    helper.assertValueEqual(
                            transferInfo.requestedChannels(),
                            expectedChannels,
                            "The ME access hatch must request its exact virtual channel cost");
                    helper.assertValueEqual(
                            transferInfo.channelConnections(),
                            expectedChannels,
                            "The ME access hatch must receive every requested virtual channel lease");
                    helper.assertValueEqual(
                            transferInfo.state(),
                            TowerVirtualDeviceState.ALLOCATED,
                            "The ME access hatch must be allocated through the virtual bridge");
                    helper.assertTrue(towerGrid != hatchNode.getGrid(),
                            "Tower and ME access hatch must retain distinct physical Grid identities");
                    helper.assertTrue(
                            ((VirtualGridBridge) towerGrid).containsIncomingVirtualMember(hatchNode),
                            "The ME access hatch must be registered as a primary-grid virtual member");
                    helper.assertValueEqual(
                            availableAmount(towerGrid, probe),
                            11L,
                            "A terminal on the tower grid must see Trinity storage through the ME access hatch");
                })
                .thenExecute(() -> destroyGridPower(towerPower))
                .thenSucceed();
    }

    @TestHolder("trinity_access_hatch_lifecycle_withdraws_local_publications_synchronously")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void trinityAccessHatchLifecycleWithdrawsLocalPublicationsSynchronously(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        TrinityPatternCatalog.CoreMount mount = host.getPatternCatalog().mountedCores().getFirst();
        PatternRoute route = new PatternRoute(host.getHostId(), mount.core().coreId(), 0);
        AEItemKey patternOutput = AEItemKey.of(Items.OAK_PLANKS);
        AEItemKey storageProbe = AEItemKey.of(Items.COPPER_INGOT);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(mount.core().trySetPattern(0, encodedOakPlanksPattern(helper)),
                            "Hatch lifecycle test should install one stable routed pattern");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    long inserted = fixture.grid().getStorageService().getInventory().insert(
                            storageProbe, 9L, Actionable.MODULATE, host.accessActionSource());
                    helper.assertValueEqual(inserted, 9L,
                            "Hatch lifecycle probe must enter Trinity storage through the owner grid");
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    IGrid grid = fixture.grid();
                    helper.assertValueEqual(availableAmount(grid, storageProbe), 9L,
                            "Lease grid must mount Trinity storage exactly once before owner unload");
                    helper.assertValueEqual(
                            publishedCpuCount(grid, host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Lease grid must publish every Trinity CPU exactly once before owner unload");
                    helper.assertValueEqual(
                            publishedRouteCount(grid, patternOutput, route),
                            1L,
                            "Lease grid must publish the routed pattern exactly once before owner unload");
                })
                .thenExecute(() -> {
                    TrinityAccessHatchBlockEntity unloadedOwner = fixture.accessHatches().stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow();
                    IGridNode unloadedNode = unloadedOwner.getMainNode().getNode();
                    IGrid unloadedGrid = unloadedOwner.connectedGrid();
                    helper.assertTrue(unloadedNode != null && unloadedGrid != null,
                            "The lease owner must have an initialized grid node");
                    TrinityCraftingRuntimeRegistry unloadedRegistry = runtimeRegistry(unloadedGrid);
                    helper.assertTrue(!unloadedRegistry.data_energistics$publish(unloadedNode, host.getCraftingRuntime()),
                            "The lease owner runtime must be published before chunk unload");
                    helper.assertTrue(!unloadedOwner.terminalPartitions().isEmpty(),
                            "The lease owner must publish terminal partitions before chunk unload");

                    unloadedOwner.onChunkUnloaded();

                    helper.assertTrue(!unloadedRegistry.data_energistics$withdraw(unloadedNode),
                            "Chunk unload must withdraw the local CPU publication before returning");
                    helper.assertTrue(unloadedOwner.terminalPartitions().isEmpty(),
                            "Chunk unload must detach local terminal partitions before returning");
                    helper.assertValueEqual(availableAmount(unloadedGrid, storageProbe), 0L,
                            "Unloading the sole access hatch must withdraw Trinity storage");
                    helper.assertValueEqual(
                            publishedCpuCount(unloadedGrid, host.getCpuPartitions()),
                            0L,
                            "Unloading the sole access hatch must withdraw every Trinity CPU");
                    helper.assertValueEqual(
                            publishedRouteCount(unloadedGrid, patternOutput, route),
                            0L,
                            "Unloading the sole access hatch must withdraw the routed pattern");
                    helper.assertTrue(host.accessGrid() == null,
                            "Unloading the sole access hatch must clear the host access grid");
                    helper.assertValueEqual(
                            fixture.accessHatches().stream().filter(host::isLeaseOwner).count(),
                            0L,
                            "Unloading the sole access hatch must clear the host lease");
                })
                .thenSucceed();
    }

    @TestHolder("trinity_structure_validation_rechecks_only_resumed_domain_once")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void trinityStructureValidationRechecksOnlyResumedDomainOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localOrigin = validationTestLocalOrigin(helper);
        BlockPos origin = helper.absolutePos(localOrigin);
        helper.setBlock(localOrigin, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));

        BlockState hostState = level.getBlockState(origin);
        level.removeBlockEntity(origin);
        CountingStructureWorldViewFactory worldViews = new CountingStructureWorldViewFactory();
        CountingStructureValidation validation = new CountingStructureValidation();
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(
                origin,
                hostState,
                validation,
                worldViews);
        level.setBlockEntity(host);
        host.onLoad();

        buildMainStructure(helper, level, origin);
        buildCpuStructure(helper, level, origin);
        buildCraftingStructure(helper, level, origin);
        host.serverTick();

        helper.assertValueEqual(worldViews.scanCount(), 3,
                "Initial lifecycle validation must scan each Trinity structure exactly once");
        assertAllStructureDomainsAvailable(helper, host,
                "Complete Trinity structures must expose all three capability domains");

        for (Structure structure : Structure.values()) {
            BlockPos waitingPosition = requireUnloadedWaitingPosition(level, origin, structure.ordinal());
            assertDeferredStructureDomain(helper, level, host, worldViews, structure, waitingPosition);
            assertInvalidStructureDomain(helper, host, worldViews, structure);
        }

        BlockPos timedWaitingPosition = requireUnloadedWaitingPosition(level, origin, Structure.values().length + 1);
        AtomicInteger craftingValidationsWhileWaiting = new AtomicInteger();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    host.serverTick();
                    assertAllStructureDomainsAvailable(helper, host,
                            "Delayed block-entity initialization must leave all structure domains valid");
                    beginDeferredStructureDomain(
                            helper,
                            host,
                            worldViews,
                            Structure.CRAFTING,
                            timedWaitingPosition);
                    craftingValidationsWhileWaiting.set(validation.validationCount(Structure.CRAFTING));
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertFalse(level.isLoaded(timedWaitingPosition),
                            "Deferred CRAFTING waiting chunk must remain unloaded throughout the 200-tick window");
                    helper.assertValueEqual(
                            validation.validationCount(Structure.CRAFTING),
                            craftingValidationsWhileWaiting.get(),
                            "Deferred CRAFTING validation must not repeat during 200 real server ticks");
                    loadWaitingPosition(helper, level, timedWaitingPosition);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            host.structureValidationStatus(Structure.CRAFTING).state(),
                            State.VALID,
                            "CRAFTING validation must recover on the first tick after its waiting position loads");
                    helper.assertValueEqual(
                            validation.validationCount(Structure.CRAFTING),
                            craftingValidationsWhileWaiting.get() + 1,
                            "Resumed CRAFTING validation must complete exactly once");
                    assertAllStructureDomainsAvailable(helper, host,
                            "CRAFTING recovery must restore all three capability domains");
                })
                .thenSucceed();
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
        helper.assertValueEqual(hatches.size(), 1, "Main structure must bind exactly one access hatch");
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
        buildCpuStructure(helper, level, origin);
        buildCraftingStructure(helper, level, origin);
        host.requestStructureRecheck();
        host.serverTick();
        helper.assertTrue(host.isCraftingStructureFormed(),
                "Idle switch test requires a formed Trinity crafting child structure");
        TrinityPatternCatalog.CoreMount patternMount = host.getPatternCatalog().mountedCores().getFirst();
        helper.assertTrue(patternMount.core().trySetPattern(0, encodedOakPlanksPattern(helper)),
                "Idle switch test should install a routed pattern before either grid connects");
        host.serverTick();
        PatternRoute patternRoute = new PatternRoute(host.getHostId(), patternMount.core().coreId(), 0);
        AEItemKey patternOutput = AEItemKey.of(Items.OAK_PLANKS);
        List<TrinityAccessHatchBlockEntity> structureHatches = boundTrinityAccessHatches(host);
        helper.assertValueEqual(structureHatches.size(), 1,
                "Main structure must bind exactly one access hatch");
        TrinityAccessHatchBlockEntity testCompetitor = placeAdditionalBoundAccessHatch(level, origin, host);
        List<TrinityAccessHatchBlockEntity> hatches = List.of(structureHatches.getFirst(), testCompetitor);
        TrinityAccessHatchBlockEntity initialOwner = structureHatches.getFirst();
        TrinityAccessHatchBlockEntity competitor = testCompetitor;
        AtomicReference<TestGridPower> ownerPower = new AtomicReference<>();
        AtomicReference<TestGridPower> competitorPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(ownerPower, competitorPower));
        AtomicReference<IGrid> ownerGrid = new AtomicReference<>();
        AtomicReference<IGrid> competitorGrid = new AtomicReference<>();
        AtomicReference<IGridNode> ownerNode = new AtomicReference<>();
        AtomicReference<IGridNode> competitorNode = new AtomicReference<>();
        AEItemKey leaseProbe = AEItemKey.of(Items.IRON_INGOT);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    IGridNode initializedOwnerNode = initialOwner.getMainNode().getNode();
                    helper.assertTrue(initializedOwnerNode != null,
                            "The initial owner access node must finish AE initialization");
                    ownerNode.set(initializedOwnerNode);
                    if (ownerPower.get() == null) {
                        TestGridPower power = new TestGridPower(level);
                        power.connect(initializedOwnerNode);
                        ownerPower.set(power);
                    }
                    host.serverTick();
                    IGrid selectedGrid = initialOwner.connectedGrid();
                    helper.assertTrue(selectedGrid != null,
                            "The initial owner hatch must join its independently powered grid");
                    ownerGrid.set(selectedGrid);
                    initialOwner.refreshTrinityPatternPublication();
                    helper.assertTrue(host.isLeaseOwner(initialOwner),
                            "The first online hatch should receive the initial lease");
                    TrinityCraftingRuntimeRegistry ownerRegistry = runtimeRegistry(selectedGrid);
                    helper.assertTrue(!ownerRegistry.data_energistics$publish(initializedOwnerNode, host.getCraftingRuntime()),
                            "The selected owner node must already publish its runtime synchronously");
                    List<TrinityDataCoreVirtualCpu> cpuPartitions = host.getCpuPartitions();
                    helper.assertValueEqual(
                            publishedCpuCount(selectedGrid, cpuPartitions),
                            (long) cpuPartitions.size(),
                            "The initial owner grid must publish every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(selectedGrid, patternOutput, patternRoute),
                            1L,
                            "The initial owner grid must publish the routed pattern exactly once");
                    helper.assertTrue(!initialOwner.terminalPartitions().isEmpty() &&
                            initialOwner.terminalPartitions().stream()
                                    .allMatch(partition -> partition.isAttachedTo(selectedGrid)),
                            "The initial owner must attach its terminal partitions to its selected grid");
                })
                .thenExecute(() -> {
                    long inserted = ownerGrid.get().getStorageService().getInventory().insert(
                            leaseProbe, 5L, Actionable.MODULATE, host.accessActionSource());
                    helper.assertValueEqual(inserted, 5L,
                            "Idle switch probe should enter Trinity storage through the owner grid");
                })
                .thenWaitUntil(() -> {
                    IGridNode initializedCompetitorNode = competitor.getMainNode().getNode();
                    helper.assertTrue(initializedCompetitorNode != null,
                            "The competing access node must finish AE initialization");
                    competitorNode.set(initializedCompetitorNode);
                    if (competitorPower.get() == null) {
                        TestGridPower power = new TestGridPower(level);
                        power.connect(initializedCompetitorNode);
                        competitorPower.set(power);
                    }
                    competitor.compartment$bindToHost(mainStructureName(), host);
                    host.serverTick();
                    IGrid otherGrid = competitor.connectedGrid();
                    helper.assertTrue(otherGrid != null && ownerGrid.get() != otherGrid,
                            "Idle switch test requires two independent AE grids");
                    competitorGrid.set(otherGrid);
                    helper.assertTrue(host.isLeaseOwner(initialOwner),
                            "A later lower-coordinate grid must not replace an online sticky lease");
                    helper.assertTrue(!host.isLeaseOwner(competitor),
                            "The second online grid must remain outside the sticky lease");
                    TrinityCraftingRuntimeRegistry competitorRegistry = runtimeRegistry(otherGrid);
                    helper.assertTrue(!competitorRegistry.data_energistics$withdraw(initializedCompetitorNode),
                            "A non-owning node must have no runtime publication");
                    helper.assertValueEqual(availableAmount(ownerGrid.get(), leaseProbe), 5L,
                            "Sticky owner grid should expose the host storage once");
                    helper.assertValueEqual(availableAmount(otherGrid, leaseProbe), 0L,
                            "Non-owning grid must not expose the host storage");
                    helper.assertValueEqual(
                            publishedCpuCount(ownerGrid.get(), host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Sticky owner grid must retain every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedCpuCount(otherGrid, host.getCpuPartitions()),
                            0L,
                            "The non-owning grid must not publish Trinity CPUs");
                    helper.assertValueEqual(
                            publishedRouteCount(ownerGrid.get(), patternOutput, patternRoute),
                            1L,
                            "Sticky owner grid must retain the routed pattern exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(otherGrid, patternOutput, patternRoute),
                            0L,
                            "The non-owning grid must not publish the routed pattern");
                    helper.assertTrue(competitor.terminalPartitions().isEmpty(),
                            "The non-owning hatch must not attach terminal partitions");
                })
                .thenExecute(() -> destroyGridPower(ownerPower))
                .thenWaitUntil(() -> {
                    competitor.compartment$bindToHost(mainStructureName(), host);
                    host.requestAccessLeaseReevaluation();
                    host.serverTick();
                    competitor.refreshTrinityPatternPublication();
                    helper.assertTrue(host.isLeaseOwner(competitor),
                            "An idle host should switch after its owner grid goes offline");
                    helper.assertTrue(!runtimeRegistry(ownerGrid.get()).data_energistics$withdraw(ownerNode.get()),
                            "The old owner publication must be absent when lease reevaluation returns");
                    helper.assertTrue(!runtimeRegistry(competitorGrid.get()).data_energistics$publish(
                            competitorNode.get(), host.getCraftingRuntime()),
                            "The replacement owner publication must exist when lease reevaluation returns");
                    helper.assertTrue(host.accessGrid() == competitorGrid.get(),
                            "The switched idle lease should expose only the competitor grid");
                    helper.assertValueEqual(availableAmount(ownerGrid.get(), leaseProbe), 0L,
                            "Offline former owner grid must withdraw the host storage mount");
                    helper.assertValueEqual(availableAmount(competitorGrid.get(), leaseProbe), 5L,
                            "New owner grid should mount the host storage exactly once");
                    helper.assertValueEqual(
                            publishedCpuCount(ownerGrid.get(), host.getCpuPartitions()),
                            0L,
                            "Former owner grid must withdraw every Trinity CPU");
                    helper.assertValueEqual(
                            publishedCpuCount(competitorGrid.get(), host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "New owner grid must publish every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(ownerGrid.get(), patternOutput, patternRoute),
                            0L,
                            "Former owner grid must withdraw the routed pattern");
                    helper.assertValueEqual(
                            publishedRouteCount(competitorGrid.get(), patternOutput, patternRoute),
                            1L,
                            "New owner grid must publish the routed pattern exactly once");
                    helper.assertTrue(initialOwner.terminalPartitions().isEmpty(),
                            "Former owner hatch must detach its terminal partitions");
                    helper.assertTrue(!competitor.terminalPartitions().isEmpty() &&
                            competitor.terminalPartitions().stream()
                                    .allMatch(partition -> partition.isAttachedTo(competitorGrid.get())),
                            "New owner hatch must attach terminal partitions only to the replacement grid");
                })
                .thenExecute(() -> destroyGridPower(competitorPower))
                .thenSucceed();
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
                mount.core().appendPendingOutputs(
                        route,
                        List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND))));

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
        helper.assertTrue(host.isCpuStructureFormed() && host.isCraftingStructureFormed(),
                "Busy reconstruction test requires both Trinity child structures");
        TrinityPatternCatalog.CoreMount persistentMount = host.getPatternCatalog().mountedCores().getFirst();
        ItemStack stablePattern = encodedOakPlanksPattern(helper);
        helper.assertTrue(persistentMount.core().trySetPattern(0, stablePattern),
                "Busy reconstruction test should install a stable routed pattern");
        int suspendedSlot = 1;
        ItemStack suspendedPattern = encodedOakPlanksPattern(helper);
        PatternRoute suspendedRoute = new PatternRoute(
                host.getHostId(), persistentMount.core().coreId(), suspendedSlot);
        host.serverTick();
        PatternRoute persistentRoute = new PatternRoute(host.getHostId(), persistentMount.core().coreId(), 0);
        AEItemKey patternOutput = AEItemKey.of(Items.OAK_PLANKS);
        AEItemKey storageProbe = AEItemKey.of(Items.REDSTONE);
        AEItemKey jobOutput = AEItemKey.of(Items.DIAMOND);
        long jobOutputAmount = 3L;

        helper.assertValueEqual(hatches.size(), 1,
                "Complete Trinity main structure must bind exactly one access hatch");
        TrinityAccessHatchBlockEntity testCompetitor = placeAdditionalBoundAccessHatch(level, origin, host);
        hatches = List.of(hatches.getFirst(), testCompetitor);
        TrinityAccessHatchBlockEntity intendedOwner = hatches.getFirst();
        TrinityAccessHatchBlockEntity intendedCompetitor = testCompetitor;
        AtomicReference<TestGridPower> ownerPower = new AtomicReference<>();
        AtomicReference<TestGridPower> competitorPower = new AtomicReference<>();
        registerGridPowerCleanup(helper, List.of(ownerPower, competitorPower));
        AtomicReference<TrinityDataCoreBlockEntity> currentHost = new AtomicReference<>(host);
        AtomicReference<IGrid> originalLeaseGrid = new AtomicReference<>();
        AtomicReference<IGrid> competingGrid = new AtomicReference<>();
        AtomicReference<List<TrinityDataCoreVirtualCpu>> originalCpuPartitions = new AtomicReference<>();
        AtomicReference<IGrid> reboundGrid = new AtomicReference<>();
        AtomicReference<Integer> jobCpuNumber = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    IGridNode ownerNode = intendedOwner.getMainNode().getNode();
                    helper.assertTrue(ownerNode != null,
                            "The intended lease owner node must finish AE initialization");
                    if (ownerPower.get() == null) {
                        TestGridPower power = new TestGridPower(level);
                        power.connect(ownerNode);
                        ownerPower.set(power);
                    }
                    host.serverTick();
                    IGrid ownerGrid = intendedOwner.connectedGrid();
                    helper.assertTrue(ownerGrid != null,
                            "The intended lease owner must join its independently powered grid");
                    originalLeaseGrid.set(ownerGrid);
                    intendedOwner.refreshTrinityPatternPublication();
                    helper.assertTrue(host.isLeaseOwner(intendedOwner),
                            "The only online hatch must receive the initial lease");
                    helper.assertValueEqual(
                            publishedCpuCount(ownerGrid, host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Initial busy lease grid must publish every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(ownerGrid, patternOutput, persistentRoute),
                            1L,
                            "Initial busy lease grid must publish the routed pattern exactly once");
                    helper.assertTrue(!intendedOwner.terminalPartitions().isEmpty() &&
                            intendedOwner.terminalPartitions().stream()
                                    .allMatch(partition -> partition.isAttachedTo(ownerGrid)),
                            "Initial busy lease owner must attach its terminal partitions");
                })
                .thenExecute(() -> {
                    helper.assertTrue(persistentMount.core().trySetPattern(suspendedSlot, suspendedPattern),
                            "Busy reconstruction test should install the pattern used by its suspended queue");
                    helper.assertTrue(persistentMount.core().enqueueBatch(
                            suspendedRoute,
                            suspendedPattern,
                            List.of(
                                    new ItemStack(Items.OAK_LOG),
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY,
                                    ItemStack.EMPTY),
                            level.getGameTime()),
                            "Busy reconstruction test should enqueue one real routed crafting batch");
                    helper.assertTrue(persistentMount.core().trySetPattern(suspendedSlot, ItemStack.EMPTY),
                            "Clearing the suspended slot should retain its queued definition snapshot");
                    helper.assertValueEqual(
                            persistentMount.core().queuedBatchCount(suspendedSlot),
                            1,
                            "Cleared suspended slot must retain exactly one dormant queue group");
                    helper.assertTrue(host.getPatternCatalog().hasWork(),
                            "The dormant queue must make the already leased host busy");
                    ICraftingSubmitResult result = host.getCpuPartitions().getFirst().submitJob(
                            originalLeaseGrid.get(),
                            waitingOutputPlan(jobOutput, jobOutputAmount),
                            host.accessActionSource(),
                            null);
                    helper.assertTrue(result.successful(),
                            "Busy reconstruction test must submit one persistent CPU job");
                    TrinityDataCoreVirtualCpu worker = requireSingleBusyCpu(helper, host);
                    jobCpuNumber.set(worker.number());
                    helper.assertValueEqual(worker.getWaitingFor(jobOutput), jobOutputAmount,
                            "Submitted CPU job must wait for its complete final output");
                })
                .thenExecute(() -> {
                    MEStorage storage = originalLeaseGrid.get().getStorageService().getInventory();
                    long inserted = storage.insert(
                            storageProbe, 6L, Actionable.MODULATE, host.accessActionSource());
                    helper.assertValueEqual(inserted, 6L,
                            "Busy reconstruction probe must enter storage through the lease grid");
                    long extracted = storage.extract(
                            storageProbe, 2L, Actionable.MODULATE, host.accessActionSource());
                    helper.assertValueEqual(extracted, 2L,
                            "Busy reconstruction probe must leave storage through the lease grid");
                    helper.assertValueEqual(availableAmount(originalLeaseGrid.get(), storageProbe), 4L,
                            "Busy reconstruction storage probe must retain its exact remainder");
                })
                .thenWaitUntil(() -> {
                    IGridNode competitorNode = intendedCompetitor.getMainNode().getNode();
                    helper.assertTrue(competitorNode != null,
                            "The competing access node must finish AE initialization");
                    if (competitorPower.get() == null) {
                        TestGridPower power = new TestGridPower(level);
                        power.connect(competitorNode);
                        competitorPower.set(power);
                    }
                    intendedCompetitor.compartment$bindToHost(mainStructureName(), host);
                    host.serverTick();
                    IGrid otherGrid = intendedCompetitor.connectedGrid();
                    helper.assertTrue(otherGrid != null && otherGrid != originalLeaseGrid.get(),
                            "Lease reconstruction test requires two independently powered AE grids");
                    competingGrid.set(otherGrid);
                    helper.assertTrue(host.isLeaseOwner(intendedOwner) && !host.isLeaseOwner(intendedCompetitor),
                            "A later lower-coordinate hatch must not replace the active busy lease");
                    helper.assertValueEqual(availableAmount(originalLeaseGrid.get(), storageProbe), 4L,
                            "Busy owner grid must expose the storage probe exactly once");
                    helper.assertValueEqual(availableAmount(otherGrid, storageProbe), 0L,
                            "Busy competing grid must not expose Trinity storage");
                    helper.assertValueEqual(
                            publishedCpuCount(originalLeaseGrid.get(), host.getCpuPartitions()),
                            (long) host.getCpuPartitions().size(),
                            "Busy owner grid must publish every Trinity CPU exactly once");
                    helper.assertValueEqual(publishedCpuCount(otherGrid, host.getCpuPartitions()), 0L,
                            "Busy competing grid must not publish Trinity CPUs");
                    helper.assertValueEqual(
                            publishedRouteCount(originalLeaseGrid.get(), patternOutput, persistentRoute),
                            1L,
                            "Busy owner grid must publish the routed pattern exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(otherGrid, patternOutput, persistentRoute),
                            0L,
                            "Busy competing grid must not publish the routed pattern");
                    helper.assertTrue(intendedCompetitor.terminalPartitions().isEmpty(),
                            "Busy competing hatch must not attach terminal partitions");
                    originalCpuPartitions.set(List.copyOf(host.getCpuPartitions()));
                })
                .thenExecute(() -> {
                    helper.assertTrue(host.getPatternCatalog().hasWork(),
                            "Suspended P-core queue must lock the non-default lease before host reconstruction");
                    helper.assertValueEqual(
                            persistentMount.core().queuedBatchCount(suspendedSlot),
                            1,
                            "Suspended queue must survive online ticks before host reconstruction");
                    CompoundTag saved = new CompoundTag();
                    host.saveAdditional(saved, level.registryAccess());
                    host.onChunkUnloaded();
                    level.removeBlockEntity(origin);

                    BlockState hostState = level.getBlockState(origin);
                    TrinityDataCoreBlockEntity loadedHost = new TrinityDataCoreBlockEntity(
                            origin,
                            hostState,
                            new TrinityStructureValidationImpl(),
                            new OneShotUnloadedWorldViewFactory());
                    loadedHost.loadTag(saved, level.registryAccess());
                    level.setBlockEntity(loadedHost);
                    loadedHost.onLoad();
                    loadedHost.serverTick();
                    intendedOwner.compartment$bindToHost(mainStructureName(), loadedHost);
                    intendedCompetitor.compartment$bindToHost(mainStructureName(), loadedHost);
                    helper.assertValueEqual(
                            loadedHost.structureValidationStatus(Structure.MAIN).state(),
                            State.DEFERRED,
                            "Reconstructed host must defer an unloaded main-structure position");
                    helper.assertTrue(loadedHost.multiBlock$isFormed() && loadedHost.isCpuStructureFormed() &&
                            loadedHost.isCraftingStructureFormed(),
                            "Deferred validation must retain every persisted formation snapshot");
                    helper.assertTrue(!loadedHost.isStorageAvailable() && !loadedHost.isCpuProviderAvailable() &&
                            !loadedHost.isPatternProviderAvailable(),
                            "Deferred main validation must withdraw all three capability domains");
                    helper.assertValueEqual(
                            persistentMount.core().queuedBatchCount(suspendedSlot),
                            1,
                            "Deferred reconstruction must retain the suspended P-core queue");
                    assertRetainedBusyCpuPersisted(
                            helper,
                            loadedHost,
                            jobCpuNumber.get(),
                            jobOutputAmount);
                    currentHost.set(loadedHost);
                })
                .thenWaitUntil(() -> {
                    TrinityDataCoreBlockEntity restoredHost = currentHost.get();
                    restoredHost.serverTick();
                    intendedOwner.refreshTrinityPatternPublication();
                    helper.assertTrue(restoredHost != host,
                            "Host reconstruction must exercise a newly created block entity instance");
                    helper.assertTrue(restoredHost.getPatternCatalog().layoutSnapshot().active(),
                            "Reconstructed host must rebuild its authoritative pattern layout");
                    helper.assertTrue(restoredHost.isStorageAvailable() && restoredHost.isCpuProviderAvailable() &&
                            restoredHost.isPatternProviderAvailable(),
                            "Reconstructed complete host must restore all independently gated capabilities");
                    TrinityDataCoreVirtualCpu restoredWorker = requireSingleBusyCpu(helper, restoredHost);
                    helper.assertValueEqual(restoredWorker.number(), jobCpuNumber.get(),
                            "Reconstructed host must restore the original CPU worker");
                    helper.assertValueEqual(restoredWorker.getWaitingFor(jobOutput), jobOutputAmount,
                            "Reconstructed host must retain the original CPU job until output arrives");
                    helper.assertTrue(restoredHost.isLeaseOwner(intendedOwner) &&
                            !restoredHost.isLeaseOwner(intendedCompetitor),
                            "Reconstructed busy host must restore only the persisted hatch identity");
                    helper.assertTrue(restoredHost.accessGrid() == originalLeaseGrid.get() &&
                            restoredHost.accessGrid() != competingGrid.get(),
                            "Reconstructed busy host must bind only to the persisted hatch grid");
                    helper.assertValueEqual(availableAmount(originalLeaseGrid.get(), storageProbe), 4L,
                            "Reconstructed busy host must restore its exact storage contents");
                    helper.assertValueEqual(availableAmount(competingGrid.get(), storageProbe), 0L,
                            "Competing grid must remain without Trinity storage after reconstruction");
                    helper.assertValueEqual(
                            publishedCpuCount(originalLeaseGrid.get(), restoredHost.getCpuPartitions()),
                            (long) restoredHost.getCpuPartitions().size(),
                            "Reconstructed owner grid must publish every restored CPU exactly once");
                    helper.assertValueEqual(
                            publishedCpuCount(competingGrid.get(), restoredHost.getCpuPartitions()),
                            0L,
                            "Competing grid must not publish reconstructed Trinity CPUs");
                    helper.assertValueEqual(
                            originalLeaseGrid.get().getCraftingService().getCpus().stream()
                                    .filter(originalCpuPartitions.get()::contains)
                                    .count(),
                            0L,
                            "Host reconstruction must withdraw every stale virtual CPU instance");
                    helper.assertValueEqual(
                            publishedRouteCount(originalLeaseGrid.get(), patternOutput, persistentRoute),
                            1L,
                            "Reconstructed owner grid must publish the routed pattern exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(competingGrid.get(), patternOutput, persistentRoute),
                            0L,
                            "Competing grid must not publish the reconstructed routed pattern");
                    helper.assertTrue(!intendedOwner.terminalPartitions().isEmpty() &&
                            intendedOwner.terminalPartitions().stream()
                                    .allMatch(partition -> partition.isAttachedTo(originalLeaseGrid.get())),
                            "Reconstructed owner must restore terminal partitions on its persisted grid");
                    helper.assertTrue(intendedCompetitor.terminalPartitions().isEmpty(),
                            "Competing hatch must remain without reconstructed terminal partitions");
                })
                .thenExecute(() -> {
                    TrinityDataCoreBlockEntity restoredHost = currentHost.get();
                    MEStorage storage = originalLeaseGrid.get().getStorageService().getInventory();
                    long inserted = storage.insert(
                            storageProbe, 3L, Actionable.MODULATE, restoredHost.accessActionSource());
                    helper.assertValueEqual(inserted, 3L,
                            "Reconstructed busy storage must accept a real network insertion");
                    long extracted = storage.extract(
                            storageProbe, 1L, Actionable.MODULATE, restoredHost.accessActionSource());
                    helper.assertValueEqual(extracted, 1L,
                            "Reconstructed busy storage must accept a real network extraction");
                    helper.assertValueEqual(availableAmount(originalLeaseGrid.get(), storageProbe), 6L,
                            "Reconstructed busy storage must expose the exact post-I/O amount");
                    TrinityPatternCatalog.CoreMount mount = restoredHost.getPatternCatalog().mountedCores().getFirst();
                    helper.assertValueEqual(
                            mount.core().queuedBatchCount(suspendedSlot),
                            1,
                            "Reconstructed host must retain the suspended queue before owner disconnection");
                    helper.assertTrue(restoredHost.getPatternCatalog().hasWork(),
                            "Suspended P-core queue must lock the busy lease before owner disconnection");
                    destroyGridPower(ownerPower);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        !intendedOwner.isCandidateOnline(),
                        "Waiting for the persisted owner node to become fully inactive"))
                .thenWaitUntil(() -> {
                    TrinityDataCoreBlockEntity restoredHost = currentHost.get();
                    restoredHost.serverTick();
                    helper.assertTrue(restoredHost.getPatternCatalog().hasWork(),
                            "Offline lease owner must leave its P-core work pending");
                    helper.assertValueEqual(
                            restoredHost.getPatternCatalog().mountedCores().getFirst().core()
                                    .queuedBatchCount(suspendedSlot),
                            1,
                            "Offline busy lease must retain its suspended queue group");
                    helper.assertTrue(restoredHost.accessGrid() == null,
                            "Busy host must stay offline while its persisted hatch is unavailable");
                    helper.assertTrue(!restoredHost.isLeaseOwner(intendedOwner) &&
                            !restoredHost.isLeaseOwner(intendedCompetitor),
                            "Competing online hatch must not take over an unavailable busy lease");
                    helper.assertTrue(intendedCompetitor.accessGrid() == null,
                            "Competing online hatch must expose no storage while busy work waits");
                    helper.assertValueEqual(availableAmount(originalLeaseGrid.get(), storageProbe), 0L,
                            "Offline persisted grid must withdraw Trinity storage");
                    helper.assertValueEqual(availableAmount(competingGrid.get(), storageProbe), 0L,
                            "Competing grid must remain without Trinity storage while work waits");
                    helper.assertValueEqual(
                            publishedCpuCount(originalLeaseGrid.get(), restoredHost.getCpuPartitions()),
                            0L,
                            "Offline persisted grid must withdraw Trinity CPUs");
                    helper.assertValueEqual(
                            publishedCpuCount(competingGrid.get(), restoredHost.getCpuPartitions()),
                            0L,
                            "Competing grid must not take over Trinity CPUs while work waits");
                    helper.assertValueEqual(
                            publishedRouteCount(originalLeaseGrid.get(), patternOutput, persistentRoute),
                            0L,
                            "Offline persisted grid must withdraw the routed pattern");
                    helper.assertValueEqual(
                            publishedRouteCount(competingGrid.get(), patternOutput, persistentRoute),
                            0L,
                            "Competing grid must not take over the routed pattern while work waits");
                    helper.assertTrue(intendedOwner.terminalPartitions().isEmpty() &&
                            intendedCompetitor.terminalPartitions().isEmpty(),
                            "Both hatches must expose no terminal partitions while the busy owner is offline");
                })
                .thenWaitUntil(() -> {
                    IGridNode ownerNode = intendedOwner.getMainNode().getNode();
                    helper.assertTrue(ownerNode != null,
                            "Persisted hatch node must survive its temporary grid outage");
                    if (ownerPower.get() == null) {
                        TestGridPower replacementOwnerPower = new TestGridPower(level);
                        replacementOwnerPower.connect(ownerNode);
                        ownerPower.set(replacementOwnerPower);
                    }
                    TrinityDataCoreBlockEntity restoredHost = currentHost.get();
                    restoredHost.serverTick();
                    IGrid recoveredGrid = intendedOwner.connectedGrid();
                    helper.assertTrue(recoveredGrid != null && recoveredGrid != competingGrid.get(),
                            "Recovered persisted hatch must bind to its own re-created runtime grid");
                    reboundGrid.set(recoveredGrid);
                    intendedOwner.refreshTrinityPatternPublication();
                    helper.assertTrue(restoredHost.isLeaseOwner(intendedOwner) &&
                            !restoredHost.isLeaseOwner(intendedCompetitor),
                            "Recovered persisted hatch must regain the exclusive busy lease");
                    helper.assertTrue(restoredHost.accessGrid() == recoveredGrid,
                            "Recovered busy host must expose only the persisted hatch grid");
                    helper.assertValueEqual(availableAmount(recoveredGrid, storageProbe), 6L,
                            "Recovered grid must restore the exact busy storage amount");
                    helper.assertValueEqual(
                            publishedCpuCount(recoveredGrid, restoredHost.getCpuPartitions()),
                            (long) restoredHost.getCpuPartitions().size(),
                            "Recovered grid must publish every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(recoveredGrid, patternOutput, persistentRoute),
                            1L,
                            "Recovered grid must publish the routed pattern exactly once");
                    helper.assertTrue(intendedOwner.boundCraftingRuntime() == restoredHost.getCraftingRuntime(),
                            "Recovered persisted hatch must expose the reconstructed crafting runtime");
                    helper.assertTrue(intendedCompetitor.boundCraftingRuntime() == null,
                            "Competing hatch must not expose the reconstructed crafting runtime");
                    helper.assertTrue(!intendedOwner.terminalPartitions().isEmpty() &&
                            intendedOwner.terminalPartitions().stream()
                                    .allMatch(partition -> partition.isAttachedTo(recoveredGrid)),
                            "Recovered terminal partitions must attach only to the persisted hatch grid");
                    helper.assertTrue(intendedCompetitor.terminalPartitions().isEmpty(),
                            "Competing hatch must not publish duplicate terminal partitions");
                })
                .thenExecute(() -> {
                    TrinityDataCoreBlockEntity restoredHost = currentHost.get();
                    MEStorage storage = reboundGrid.get().getStorageService().getInventory();
                    TrinityDataCoreVirtualCpu restoredWorker = requireSingleBusyCpu(helper, restoredHost);
                    helper.assertValueEqual(
                            restoredWorker.insert(jobOutput, jobOutputAmount, Actionable.MODULATE),
                            jobOutputAmount,
                            "Recovered CPU worker must accept the original job output");
                    helper.assertFalse(restoredWorker.isBusy(),
                            "Recovered CPU worker must complete the original job");
                    helper.assertValueEqual(availableAmount(reboundGrid.get(), jobOutput), jobOutputAmount,
                            "Completed original job output must enter recovered Trinity storage once");
                    long extracted = storage.extract(
                            storageProbe, 2L, Actionable.MODULATE, restoredHost.accessActionSource());
                    helper.assertValueEqual(extracted, 2L,
                            "Recovered busy storage must support a real network extraction");
                    long inserted = storage.insert(
                            storageProbe, 2L, Actionable.MODULATE, restoredHost.accessActionSource());
                    helper.assertValueEqual(inserted, 2L,
                            "Recovered busy storage must support a real network insertion");
                })
                .thenWaitUntil(() -> {
                    TrinityDataCoreBlockEntity restoredHost = currentHost.get();
                    restoredHost.serverTick();
                    intendedOwner.refreshTrinityPatternPublication();
                    helper.assertValueEqual(availableAmount(reboundGrid.get(), storageProbe), 6L,
                            "Recovered storage I/O must retain the exact busy storage amount");
                    helper.assertValueEqual(
                            publishedCpuCount(reboundGrid.get(), restoredHost.getCpuPartitions()),
                            (long) restoredHost.getCpuPartitions().size(),
                            "Recovered storage I/O must retain every Trinity CPU exactly once");
                    helper.assertValueEqual(
                            publishedRouteCount(reboundGrid.get(), patternOutput, persistentRoute),
                            1L,
                            "Recovered storage I/O must retain the routed pattern exactly once");
                    helper.assertValueEqual(
                            restoredHost.getPatternCatalog().mountedCores().getFirst().core()
                                    .queuedBatchCount(suspendedSlot),
                            1,
                            "Recovered busy lease must retain its suspended queue group");
                    helper.assertValueEqual(availableAmount(competingGrid.get(), storageProbe), 0L,
                            "Competing grid must remain without Trinity storage after recovery");
                    helper.assertValueEqual(
                            publishedCpuCount(competingGrid.get(), restoredHost.getCpuPartitions()),
                            0L,
                            "Competing grid must remain without Trinity CPUs after recovery");
                    helper.assertValueEqual(
                            publishedRouteCount(competingGrid.get(), patternOutput, persistentRoute),
                            0L,
                            "Competing grid must remain without the routed pattern after recovery");
                })
                .thenExecute(() -> {
                    destroyGridPower(ownerPower);
                    destroyGridPower(competitorPower);
                })
                .thenSucceed();
    }

    private static CraftingPlan waitingOutputPlan(AEKey output, long amount) {
        KeyCounter emittedItems = new KeyCounter();
        emittedItems.add(output, amount);
        return new CraftingPlan(
                new GenericStack(output, amount),
                1L,
                false,
                false,
                new KeyCounter(),
                emittedItems,
                new KeyCounter(),
                Map.of());
    }

    private static TrinityDataCoreVirtualCpu requireSingleBusyCpu(GameTestHelper helper,
                                                                  TrinityDataCoreBlockEntity host) {
        List<TrinityDataCoreVirtualCpu> busyCpus = host.getCraftingRuntime().publishedCpus().stream()
                .filter(TrinityDataCoreVirtualCpu::isBusy)
                .toList();
        helper.assertValueEqual(busyCpus.size(), 1,
                "Busy reconstruction test must retain exactly one active CPU job");
        return busyCpus.getFirst();
    }

    private static void assertRetainedBusyCpuPersisted(GameTestHelper helper,
                                                       TrinityDataCoreBlockEntity host,
                                                       int expectedWorkerNumber,
                                                       long expectedRemainingOutput) {
        helper.assertTrue(host.getCraftingRuntime().hasBusyJobs(),
                "Deferred reconstruction must retain its active CPU job while publication is paused");
        CompoundTag runtimeTag = new CompoundTag();
        host.getCraftingRuntime().writeToTag(runtimeTag, host.getLevel().registryAccess());
        ListTag partitions = runtimeTag.getList("partitions", Tag.TAG_COMPOUND);
        helper.assertValueEqual(partitions.size(), 1,
                "Deferred reconstruction must persist exactly one retained CPU worker");
        CompoundTag partition = partitions.getCompound(0);
        helper.assertValueEqual(partition.getInt("index"), expectedWorkerNumber,
                "Deferred reconstruction must retain the original CPU worker number");
        CompoundTag job = partition.getCompound("logic").getCompound("job");
        helper.assertValueEqual(job.getLong("remaining_amount"), expectedRemainingOutput,
                "Deferred reconstruction must retain the original CPU job output request");
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

    private static BlockPos validationTestLocalOrigin(GameTestHelper helper) {
        for (int x = 23; x <= 27; x++) {
            BlockPos candidate = new BlockPos(x, 4, 25);
            BlockPos absoluteCandidate = helper.absolutePos(candidate);
            long phase = Math.floorMod(
                    helper.getLevel().getGameTime() + absoluteCandidate.asLong(),
                    1_200L);
            long ticksUntilPeriodicMainRecheck = Math.floorMod(-phase, 1_200L);
            if (ticksUntilPeriodicMainRecheck > 450L) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not select a Trinity validation origin outside the 400-tick test window");
    }

    private static BlockPos requireUnloadedWaitingPosition(ServerLevel level, BlockPos origin, int domainIndex) {
        int baseChunkX = origin.getX() >> 4;
        int baseChunkZ = origin.getZ() >> 4;
        int firstOffset = 32 + domainIndex * 32;
        for (int offset = firstOffset; offset < firstOffset + 16; offset++) {
            BlockPos candidate = new BlockPos(
                    ((baseChunkX + offset) << 4) + 8,
                    origin.getY(),
                    ((baseChunkZ + offset) << 4) + 8);
            if (!level.isLoaded(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find an unloaded chunk for Trinity deferred validation");
    }

    private static int beginDeferredStructureDomain(GameTestHelper helper,
                                                    TrinityDataCoreBlockEntity host,
                                                    CountingStructureWorldViewFactory worldViews,
                                                    Structure structure,
                                                    BlockPos waitingPosition) {
        int scansBeforeDeferral = worldViews.scanCount();
        worldViews.deferNextScan(waitingPosition);
        requestStructureRecheck(host, structure);
        host.serverTick();

        helper.assertValueEqual(
                host.structureValidationStatus(structure).state(),
                State.DEFERRED,
                structure + " validation must defer at its tracked unloaded position");
        helper.assertValueEqual(
                host.structureValidationStatus(structure).waitingPosition(),
                waitingPosition,
                structure + " validation must retain the tracking view's unloaded position");
        helper.assertValueEqual(worldViews.scanCount(), scansBeforeDeferral + 1,
                structure + " deferral must consume exactly one full validation scan");
        assertOtherStructureStatuses(helper, host, structure, State.VALID);
        assertOnlyStructureDomainUnavailable(helper, host, structure, "Deferred");
        return worldViews.scanCount();
    }

    private static void assertDeferredStructureDomain(GameTestHelper helper,
                                                      ServerLevel level,
                                                      TrinityDataCoreBlockEntity host,
                                                      CountingStructureWorldViewFactory worldViews,
                                                      Structure structure,
                                                      BlockPos waitingPosition) {
        int scansWhileWaiting = beginDeferredStructureDomain(
                helper,
                host,
                worldViews,
                structure,
                waitingPosition);
        loadWaitingPosition(helper, level, waitingPosition);
        host.serverTick();
        helper.assertValueEqual(
                host.structureValidationStatus(structure).state(),
                State.VALID,
                structure + " validation must recover after its waiting position loads");
        helper.assertValueEqual(worldViews.scanCount(), scansWhileWaiting + 1,
                structure + " must run exactly one full validation scan after resuming");
        assertAllStructureDomainsAvailable(helper, host,
                structure + " recovery must restore all three capability domains");
    }

    private static void loadWaitingPosition(GameTestHelper helper,
                                            ServerLevel level,
                                            BlockPos waitingPosition) {
        level.getChunk(waitingPosition.getX() >> 4, waitingPosition.getZ() >> 4);
        helper.assertTrue(level.isLoaded(waitingPosition),
                "Deferred validation waiting chunk must be loaded before the resume tick");
    }

    private static void assertInvalidStructureDomain(GameTestHelper helper,
                                                     TrinityDataCoreBlockEntity host,
                                                     CountingStructureWorldViewFactory worldViews,
                                                     Structure structure) {
        int scansBeforeInvalidation = worldViews.scanCount();
        worldViews.invalidateNextScan();
        requestStructureRecheck(host, structure);
        host.serverTick();

        helper.assertValueEqual(
                host.structureValidationStatus(structure).state(),
                State.INVALID,
                structure + " validation must reject a loaded structural mismatch");
        helper.assertValueEqual(worldViews.scanCount(), scansBeforeInvalidation + 1,
                structure + " invalidation must consume exactly one full validation scan");
        if (structure == Structure.MAIN) {
            helper.assertValueEqual(host.structureValidationStatus(Structure.CPU).state(), State.PENDING,
                    "Invalid main structure must leave CPU validation pending behind its prerequisite");
            helper.assertValueEqual(host.structureValidationStatus(Structure.CRAFTING).state(), State.PENDING,
                    "Invalid main structure must leave crafting validation pending behind its prerequisite");
        } else {
            assertOtherStructureStatuses(helper, host, structure, State.VALID);
        }
        assertOnlyStructureDomainUnavailable(helper, host, structure, "Invalid");

        int scansBeforeRecovery = worldViews.scanCount();
        requestStructureRecheck(host, structure);
        host.serverTick();
        int expectedRecoveryScans = structure == Structure.MAIN ? 3 : 1;
        helper.assertValueEqual(worldViews.scanCount(), scansBeforeRecovery + expectedRecoveryScans,
                structure + " recovery must scan only itself and prerequisite-invalidated children");
        assertAllStructureDomainsAvailable(helper, host,
                structure + " recovery must restore all three capability domains");
    }

    private static void requestStructureRecheck(TrinityDataCoreBlockEntity host, Structure structure) {
        switch (structure) {
            case MAIN -> host.requestMainStructureRecheck();
            case CPU -> host.requestCpuStructureRecheck();
            case CRAFTING -> host.requestCraftingStructureRecheck();
        }
    }

    private static void assertOtherStructureStatuses(GameTestHelper helper,
                                                     TrinityDataCoreBlockEntity host,
                                                     Structure selected,
                                                     State expected) {
        for (Structure structure : Structure.values()) {
            if (structure != selected) {
                helper.assertValueEqual(
                        host.structureValidationStatus(structure).state(),
                        expected,
                        selected + " validation must not alter the " + structure + " validation state");
            }
        }
    }

    private static void assertOnlyStructureDomainUnavailable(GameTestHelper helper,
                                                             TrinityDataCoreBlockEntity host,
                                                             Structure structure,
                                                             String transition) {
        switch (structure) {
            case MAIN -> helper.assertTrue(
                    !host.isStorageAvailable() && !host.isCpuProviderAvailable() &&
                            !host.isPatternProviderAvailable(),
                    transition + " main prerequisite must withdraw storage and both dependent providers");
            case CPU -> helper.assertTrue(
                    host.isStorageAvailable() && !host.isCpuProviderAvailable() &&
                            host.isPatternProviderAvailable(),
                    transition + " CPU validation must withdraw only the CPU provider");
            case CRAFTING -> helper.assertTrue(
                    host.isStorageAvailable() && host.isCpuProviderAvailable() &&
                            !host.isPatternProviderAvailable(),
                    transition + " crafting validation must withdraw only the pattern provider");
        }
    }

    private static void assertAllStructureDomainsAvailable(GameTestHelper helper,
                                                           TrinityDataCoreBlockEntity host,
                                                           String message) {
        for (Structure structure : Structure.values()) {
            helper.assertValueEqual(
                    host.structureValidationStatus(structure).state(),
                    State.VALID,
                    message + ": " + structure + " validation must be valid");
        }
        helper.assertTrue(
                host.isStorageAvailable() && host.isCpuProviderAvailable() && host.isPatternProviderAvailable(),
                message);
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

    private static DataDistributionTowerBlockEntity placeTowerNearAccessHatch(
                                                                              GameTestHelper helper,
                                                                              BlockPos hatchPos) {
        ServerLevel level = helper.getLevel();
        BlockPos towerPos = null;
        for (int radius = 2; radius <= 7 && towerPos == null; radius++) {
            for (int offsetX = -radius; offsetX <= radius && towerPos == null; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }
                    BlockPos candidate = hatchPos.offset(offsetX, 0, offsetZ);
                    if (level.getBlockState(candidate).isAir() &&
                            level.getBlockState(candidate.above()).isAir() &&
                            level.getBlockState(candidate.above(2)).isAir()) {
                        towerPos = candidate.immutable();
                        break;
                    }
                }
            }
        }
        if (towerPos == null) {
            throw new IllegalStateException("No clear tower position exists near ME access hatch " + hatchPos);
        }

        for (int part = 2; part >= 0; part--) {
            BlockState state = ModBlocks.DATA_DISTRIBUTION_TOWER.get()
                    .defaultBlockState()
                    .setValue(DataDistributionTowerBlock.PART, part)
                    .setValue(DataDistributionTowerBlock.FACING, Direction.NORTH)
                    .setValue(DataDistributionTowerBlock.ACTIVE, false);
            level.setBlock(towerPos.above(part), state, Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(towerPos) instanceof DataDistributionTowerBlockEntity tower) {
            return tower;
        }
        throw new IllegalStateException("Placed data distribution tower has no block entity at " + towerPos);
    }

    /** Adds a structure-external candidate only for exercising multi-grid lease arbitration. */
    private static TrinityAccessHatchBlockEntity placeAdditionalBoundAccessHatch(
                                                                                 ServerLevel level,
                                                                                 BlockPos origin,
                                                                                 TrinityDataCoreBlockEntity host) {
        BlockPos hatchPos = origin.offset(16, 0, 0);
        level.setBlock(hatchPos, ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity blockEntity = level.getBlockEntity(hatchPos);
        if (!(blockEntity instanceof TrinityAccessHatchBlockEntity hatch)) {
            throw new IllegalStateException("Missing test-only Trinity access hatch at " + hatchPos);
        }
        hatch.compartment$bindToHost(mainStructureName(), host);
        return hatch;
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
        helper.assertValueEqual(hatches.size(), 1, "Trinity main structure should bind exactly one access hatch");
        IGridNode accessNode = hatches.getFirst().getMainNode().getNode();
        helper.assertTrue(accessNode != null, "The Trinity access node must finish AE initialization");
        TestGridPower power = new TestGridPower(level);
        power.connect(accessNode);
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

    private static long publishedCpuCount(IGrid grid, Collection<TrinityDataCoreVirtualCpu> cpuPartitions) {
        return grid.getCraftingService().getCpus().stream().filter(cpuPartitions::contains).count();
    }

    private static void assertSameTerminalPartitions(GameTestHelper helper,
                                                     List<TrinityPatternTerminalPartition> expected,
                                                     List<TrinityPatternTerminalPartition> actual,
                                                     String operation) {
        helper.assertValueEqual(actual.size(), expected.size(), operation + " must retain the terminal layout size");
        for (int index = 0; index < actual.size(); index++) {
            helper.assertTrue(actual.get(index) == expected.get(index),
                    operation + " must retain terminal partition " + index);
        }
    }

    private static long publishedRouteCount(IGrid grid, AEKey output, PatternRoute route) {
        return grid.getCraftingService().getCraftingFor(output).stream()
                .filter(details -> details instanceof RoutedCraftingPatternDetails routed &&
                        routed.route().equals(route))
                .count();
    }

    private static TrinityCraftingRuntimeRegistry runtimeRegistry(IGrid grid) {
        if (!(grid.getCraftingService() instanceof TrinityCraftingRuntimeRegistry registry)) {
            throw new IllegalStateException("AE2 crafting service has no Trinity runtime registry");
        }
        return registry;
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

    private enum StructureScanFault {
        NONE,
        DEFERRED,
        INVALID
    }

    private static final class CountingStructureValidation implements TrinityStructureValidation {

        private final TrinityStructureValidation delegate = new TrinityStructureValidationImpl();
        private final EnumMap<Structure, Integer> validationCounts = new EnumMap<>(Structure.class);

        private CountingStructureValidation() {
            for (Structure structure : Structure.values()) {
                this.validationCounts.put(structure, 0);
            }
        }

        private int validationCount(Structure structure) {
            return this.validationCounts.get(structure);
        }

        private void recordValidation(Structure structure) {
            this.validationCounts.compute(structure, (ignored, count) -> count + 1);
        }

        @Override
        public Status status(Structure structure) {
            return this.delegate.status(structure);
        }

        @Override
        public boolean isValid(Structure structure) {
            return this.delegate.isValid(structure);
        }

        @Override
        public void markPending(Structure structure) {
            this.delegate.markPending(structure);
        }

        @Override
        public void markValid(Structure structure) {
            this.delegate.markValid(structure);
            recordValidation(structure);
        }

        @Override
        public void markInvalid(Structure structure) {
            this.delegate.markInvalid(structure);
            recordValidation(structure);
        }

        @Override
        public boolean deferIfUnloaded(Structure structure,
                                       @Nullable PatternDiagnostic diagnostic,
                                       @Nullable BlockPos observedUnloadedPosition) {
            boolean deferred = this.delegate.deferIfUnloaded(structure, diagnostic, observedUnloadedPosition);
            if (deferred) {
                recordValidation(structure);
            }
            return deferred;
        }

        @Override
        public boolean resumeIfLoaded(Structure structure, Predicate<BlockPos> isLoaded) {
            return this.delegate.resumeIfLoaded(structure, isLoaded);
        }

        @Override
        public void reset() {
            this.delegate.reset();
        }
    }

    private static final class CountingStructureWorldViewFactory implements TrinityStructureWorldViewFactory {

        private final AtomicInteger scanCount = new AtomicInteger();
        private StructureScanFault nextFault = StructureScanFault.NONE;
        private BlockPos nextWaitingPosition;

        private int scanCount() {
            return this.scanCount.get();
        }

        private void deferNextScan(BlockPos waitingPosition) {
            prepareNextScan(StructureScanFault.DEFERRED, waitingPosition);
        }

        private void invalidateNextScan() {
            prepareNextScan(StructureScanFault.INVALID, null);
        }

        private void prepareNextScan(StructureScanFault fault, BlockPos waitingPosition) {
            if (this.nextFault != StructureScanFault.NONE) {
                throw new IllegalStateException("A Trinity validation scan fault is already pending");
            }
            this.nextFault = fault;
            this.nextWaitingPosition = waitingPosition == null ? null : waitingPosition.immutable();
        }

        @Override
        public View create(Level level) {
            this.scanCount.incrementAndGet();
            StructureScanFault fault = this.nextFault;
            BlockPos waitingPosition = this.nextWaitingPosition;
            this.nextFault = StructureScanFault.NONE;
            this.nextWaitingPosition = null;
            return new CountingStructureWorldView(level, fault, waitingPosition);
        }
    }

    private static final class CountingStructureWorldView implements TrinityStructureWorldViewFactory.View {

        private final Level level;
        private final StructureScanFault fault;
        private final BlockPos waitingPosition;

        private CountingStructureWorldView(Level level,
                                           StructureScanFault fault,
                                           BlockPos waitingPosition) {
            this.level = level;
            this.fault = fault;
            this.waitingPosition = waitingPosition;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return this.level.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.fault == StructureScanFault.NONE ? this.level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.fault == StructureScanFault.NONE ? this.level.getBlockEntity(pos) : null;
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }

        @Override
        public BlockPos firstUnloadedPosition() {
            return this.fault == StructureScanFault.DEFERRED ? this.waitingPosition : null;
        }
    }

    private static final class OneShotUnloadedWorldViewFactory implements TrinityStructureWorldViewFactory {

        private final AtomicBoolean deferNextView = new AtomicBoolean(true);

        @Override
        public View create(Level level) {
            return new OneShotWorldView(level, this.deferNextView.getAndSet(false));
        }
    }

    private static final class OneShotWorldView implements TrinityStructureWorldViewFactory.View {

        private final Level level;
        private final boolean reportUnloaded;
        private BlockPos firstUnloadedPosition;

        private OneShotWorldView(Level level, boolean reportUnloaded) {
            this.level = level;
            this.reportUnloaded = reportUnloaded;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            boolean loaded = !this.reportUnloaded && this.level.isLoaded(pos);
            if (!loaded && this.firstUnloadedPosition == null) {
                this.firstUnloadedPosition = pos.immutable();
            }
            return loaded;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }

        @Override
        public BlockPos firstUnloadedPosition() {
            return this.firstUnloadedPosition;
        }
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
