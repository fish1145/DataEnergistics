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
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.network.DigitalConstructFlowerAutoBuildTarget;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

        BlockEntity flowerBlockEntity = level.getBlockEntity(origin);
        if (!(flowerBlockEntity instanceof DigitalConstructFlowerBlockEntity flower)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        helper.assertTrue(flower.accessGrid() == null, "Unformed Trinity Data Core should not expose an AE grid");

        buildMainStructure(helper, level, origin);
        List<TrinityAccessHatchBlockEntity> builtHatches = requireBuiltTrinityAccessHatches(helper, level, origin);

        for (TrinityAccessHatchBlockEntity hatch : builtHatches) {
            helper.assertTrue(
                    hatch.accessGrid() == null,
                    "Unbound Trinity access hatch should not expose an AE grid before host recheck");
            assertTrinityAccessCableConnections(helper, hatch);
        }

        flower.serverTick();
        helper.assertTrue(flower.isStructureFormed(), "Auto-built Trinity Data Core main structure should form: " +
                flower.getLastFailureReason() + " at " + flower.getLastFailurePosition());
        List<TrinityAccessHatchBlockEntity> boundHatches = boundTrinityAccessHatches(flower);
        helper.assertTrue(!boundHatches.isEmpty(), "Formed Trinity Data Core should bind Trinity access hatches");
        helper.assertTrue(
                boundHatches.stream().anyMatch(builtHatches::contains),
                "Formed Trinity Data Core should bind an auto-built Trinity access hatch from the main structure");
        for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
            assertTrinityAccessCableConnections(helper, hatch);
            hatch.refreshTrinityAccess();
        }

        helper.assertTrue(
                flower.accessGrid() == null,
                "Trinity access must stay offline until the CPU and crafting child structures are also formed");
        buildChildStructure(helper, level, origin, DigitalConstructFlowerAutoBuildTarget.CPU);
        buildChildStructure(helper, level, origin, DigitalConstructFlowerAutoBuildTarget.CRAFTING);
        flower.requestStructureRecheck();
        flower.serverTick();
        helper.assertTrue(flower.isCpuStructureFormed(),
                "Auto-built Trinity CPU child structure should form: " + flower.getCpuLastFailureReason());
        helper.assertTrue(flower.isCraftingStructureFormed(),
                "Auto-built Trinity crafting child structure should form: " + flower.getCraftingLastFailureReason());
        for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
            hatch.refreshTrinityAccess();
        }
        AtomicReference<TestGridPower> testGridPower = new AtomicReference<>();
        helper.succeedWhen(() -> {
            connectAccessHatches(helper, level, boundHatches, testGridPower);
            flower.serverTick();
            for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
                hatch.refreshTrinityAccess();
            }
            assertFlowerUsesBoundTrinityAccessGrid(helper, flower, boundHatches);
            assertSingleLeaseOwner(helper, flower, boundHatches);
            IGrid grid = flower.accessGrid();
            helper.assertTrue(grid != null, "Complete Trinity structure should expose one powered AE grid");
            helper.assertTrue(boundHatches.stream().allMatch(hatch -> hatch.connectedGrid() == grid),
                    "Both Trinity access hatches should share one AE grid in this test");
            helper.assertTrue(boundHatches.stream()
                    .filter(flower::isLeaseOwner)
                    .flatMap(hatch -> hatch.terminalPartitions().stream())
                    .allMatch(partition -> partition.isAttachedTo(grid)),
                    "The lease owner should attach every terminal partition to its selected grid");
            helper.assertTrue(boundHatches.stream().filter(flower::isLeaseOwner)
                    .anyMatch(hatch -> !hatch.terminalPartitions().isEmpty()),
                    "The lease owner should publish at least one terminal partition");
            helper.assertValueEqual(
                    boundHatches.stream().filter(hatch -> !flower.isLeaseOwner(hatch))
                            .mapToInt(hatch -> hatch.terminalPartitions().size()).sum(),
                    0,
                    "Non-owning access hatches must not mount duplicate terminal partitions");
            testGridPower.get().destroy();
        });
    }

    @TestHolder("trinity_access_hatch_withdraws_all_capabilities_on_child_failure")
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
        if (!(blockEntity instanceof DigitalConstructFlowerBlockEntity flower)) {
            helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
            return;
        }

        buildMainStructure(helper, level, origin);
        flower.serverTick();
        List<TrinityAccessHatchBlockEntity> hatches = boundTrinityAccessHatches(flower);
        helper.assertTrue(!hatches.isEmpty(), "Complete Trinity structure should bind at least one access hatch");
        buildChildStructure(helper, level, origin, DigitalConstructFlowerAutoBuildTarget.CPU);
        buildChildStructure(helper, level, origin, DigitalConstructFlowerAutoBuildTarget.CRAFTING);
        flower.requestStructureRecheck();
        flower.serverTick();
        for (TrinityAccessHatchBlockEntity hatch : hatches) {
            hatch.refreshTrinityAccess();
        }
        AtomicReference<TestGridPower> testGridPower = new AtomicReference<>();
        AtomicBoolean invalidated = new AtomicBoolean();
        AtomicReference<List<TrinityDataCoreVirtualCpu>> retainedCpuPartitions = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (!invalidated.get()) {
                connectAccessHatches(helper, level, hatches, testGridPower);
                flower.serverTick();
                for (TrinityAccessHatchBlockEntity hatch : hatches) {
                    hatch.refreshTrinityAccess();
                }
                helper.assertTrue(flower.accessGrid() != null,
                        "Complete Trinity structure should expose its owner grid before invalidation");
                assertSingleLeaseOwner(helper, flower, hatches);
                retainedCpuPartitions.set(flower.getCraftingRuntime().partitions());
                helper.assertTrue(!retainedCpuPartitions.get().isEmpty(),
                        "Formed CPU structure should expose at least one virtual CPU partition");
                helper.assertTrue(hatches.stream().filter(flower::isLeaseOwner)
                        .flatMap(hatch -> hatch.terminalPartitions().stream())
                        .allMatch(partition -> partition.isAttachedTo(flower.accessGrid())),
                        "Terminal partitions should be mounted before capability invalidation");
                TrinityPatternCatalog.CoreMount mount = flower.getPatternCatalog().mountedCores().getFirst();
                PatternRoute route = new PatternRoute(flower.getHostId(), mount.core().coreId(), 0);
                mount.core().appendPendingOutputs(route, List.of(new ItemStack(Items.DIAMOND)));

                BlockPos cpuCorePosition = findCpuCore(level, origin);
                level.setBlock(cpuCorePosition, Blocks.AIR.defaultBlockState(), 3);
                flower.requestStructureRecheck();
                flower.serverTick();
                for (TrinityAccessHatchBlockEntity hatch : hatches) {
                    hatch.refreshTrinityAccess();
                }
                invalidated.set(true);
            }

            TrinityPatternCatalog.CoreMount mount = flower.getPatternCatalog().mountedCores().getFirst();
            PatternRoute route = new PatternRoute(flower.getHostId(), mount.core().coreId(), 0);
            helper.assertTrue(!flower.isCpuStructureFormed(), "Broken CPU child structure must invalidate capabilities");
            helper.assertTrue(!flower.canExposeTrinityCapabilities(),
                    "A Trinity host with one invalid child structure must withdraw all capabilities");
            helper.assertTrue(!flower.isPatternProviderAvailable(),
                    "Pattern provider must be unavailable while any Trinity structure is invalid");
            helper.assertTrue(flower.accessGrid() == null,
                    "Storage access must be unavailable while any Trinity structure is invalid");
            helper.assertTrue(flower.getPatternCatalog().hasWork(),
                    "Invalidation must retain route-owned work for lease locking");
            helper.assertValueEqual(mount.core().pendingOutputs(route).size(), 1,
                    "Invalidation must retain pending route outputs");
            List<TrinityDataCoreVirtualCpu> currentCpuPartitions = flower.getCraftingRuntime().partitions();
            helper.assertValueEqual(currentCpuPartitions.size(), retainedCpuPartitions.get().size(),
                    "Temporary CPU structure failure must retain every virtual CPU partition");
            for (int index = 0; index < currentCpuPartitions.size(); index++) {
                helper.assertTrue(currentCpuPartitions.get(index) == retainedCpuPartitions.get().get(index),
                        "Temporary CPU structure failure must not rebuild or cancel CPU partition " + index);
            }
            helper.assertValueEqual(hatches.stream().filter(flower::isLeaseOwner).count(), 1L,
                    "Pending work must keep the original grid lease owner");
            for (TrinityAccessHatchBlockEntity hatch : hatches) {
                helper.assertTrue(hatch.accessGrid() == null,
                        "Invalid Trinity structure must withdraw hatch storage access");
                helper.assertTrue(hatch.boundCraftingRuntime() == null,
                        "Invalid Trinity structure must withdraw virtual crafting CPUs");
                helper.assertTrue(hatch.terminalPartitions().isEmpty(),
                        "Invalid Trinity structure must detach pattern terminal partitions");
            }
            testGridPower.get().destroy();
        });
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
        DigitalConstructFlowerAutoBuild.Stats stats = DigitalConstructFlowerAutoBuild.buildPattern(
                level,
                helper.makeMockPlayer(GameType.CREATIVE),
                world(level),
                definition(DigitalConstructFlowerAutoBuildTarget.MAIN).pattern(),
                origin,
                DigitalConstructFlowerBlockEntity.autoBuildStructureName(DigitalConstructFlowerAutoBuildTarget.MAIN),
                Direction.SOUTH,
                false);
        helper.assertTrue(stats.placed() > 0, "Trinity Data Core main auto-build should place structure blocks");
        helper.assertValueEqual(stats.missing(), 0, "Trinity Data Core main auto-build should have all creative candidates");
        helper.assertValueEqual(stats.blocked(), 0, "Trinity Data Core main auto-build should not hit blocked targets");
        helper.assertValueEqual(stats.unloaded(), 0, "Trinity Data Core main auto-build should not target unloaded blocks");
        helper.assertValueEqual(stats.placeFailed(), 0, "Trinity Data Core main auto-build should place every candidate");
    }

    private static void buildChildStructure(GameTestHelper helper,
                                            ServerLevel level,
                                            BlockPos origin,
                                            DigitalConstructFlowerAutoBuildTarget target) {
        DigitalConstructFlowerAutoBuild.Stats stats = DigitalConstructFlowerAutoBuild.buildPattern(
                level,
                helper.makeMockPlayer(GameType.CREATIVE),
                world(level),
                definition(target).pattern(),
                origin,
                DigitalConstructFlowerBlockEntity.autoBuildStructureName(target),
                Direction.SOUTH,
                false);
        helper.assertTrue(stats.placed() > 0, "Trinity " + target + " auto-build should place structure blocks");
        helper.assertValueEqual(stats.missing(), 0, "Trinity " + target + " auto-build should have all creative candidates");
        helper.assertValueEqual(stats.blocked(), 0, "Trinity " + target + " auto-build should not hit blocked targets");
        helper.assertValueEqual(stats.unloaded(), 0, "Trinity " + target + " auto-build should not target unloaded blocks");
        helper.assertValueEqual(stats.placeFailed(), 0, "Trinity " + target + " auto-build should place every candidate");
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

    private static List<TrinityAccessHatchBlockEntity> boundTrinityAccessHatches(DigitalConstructFlowerBlockEntity flower) {
        List<TrinityAccessHatchBlockEntity> hatches = new ArrayList<>();
        for (CompartmentPart part : flower.compartmentHost$getCompartments(mainStructureName())) {
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
                                               DigitalConstructFlowerBlockEntity flower,
                                               List<TrinityAccessHatchBlockEntity> hatches) {
        TrinityAccessHatchBlockEntity expected = hatches.stream()
                .min((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .orElseThrow();
        helper.assertValueEqual(hatches.stream().filter(flower::isLeaseOwner).count(), 1L,
                "Exactly one Trinity access hatch may own the network lease");
        helper.assertTrue(flower.isLeaseOwner(expected),
                "Simultaneously online Trinity access hatches must elect the lowest coordinate");
    }

    private static void assertFlowerUsesBoundTrinityAccessGrid(GameTestHelper helper,
                                                               DigitalConstructFlowerBlockEntity flower,
                                                               List<TrinityAccessHatchBlockEntity> boundHatches) {
        IGrid flowerGrid = flower.accessGrid();
        if (flowerGrid == null) {
            helper.fail("Formed Trinity Data Core should expose an AE grid through a bound Trinity access hatch");
            return;
        }
        for (TrinityAccessHatchBlockEntity hatch : boundHatches) {
            if (flowerGrid == hatch.accessGrid()) {
                return;
            }
        }
        helper.fail("Formed host should use the AE grid from one of its bound Trinity access hatches");
    }

    private static String mainStructureName() {
        return DigitalConstructFlowerBlockEntity.autoBuildStructureName(DigitalConstructFlowerAutoBuildTarget.MAIN);
    }

    private static JsonMultiBlockDefinition definition(DigitalConstructFlowerAutoBuildTarget target) {
        return ModVerticalMultiBlocks.JSON_MULTI_BLOCKS
                .get(DigitalConstructFlowerBlockEntity.autoBuildDefinitionKey(target))
                .orElseThrow(() -> new IllegalStateException("Missing Trinity auto-build test definition for " + target));
    }

    private static StructureWorldView world(Level level) {
        return new LevelView(level);
    }

    private record LevelView(Level level) implements StructureWorldView {

        @Override
        public boolean isLoaded(BlockPos pos) {
            return this.level.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
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
