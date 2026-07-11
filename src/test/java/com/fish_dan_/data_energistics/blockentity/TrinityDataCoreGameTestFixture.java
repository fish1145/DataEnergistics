package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Result;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TrinityDataCoreGameTestFixture implements AutoCloseable {

    private static final BlockPos LOCAL_ORIGIN = new BlockPos(25, 4, 25);

    private final GameTestHelper helper;
    private final TrinityDataCoreBlockEntity host;
    private final List<TrinityAccessHatchBlockEntity> hatches;
    private GridPower gridPower;

    private TrinityDataCoreGameTestFixture(GameTestHelper helper,
                                           TrinityDataCoreBlockEntity host,
                                           List<TrinityAccessHatchBlockEntity> hatches) {
        this.helper = helper;
        this.host = host;
        this.hatches = List.copyOf(hatches);
    }

    static TrinityDataCoreGameTestFixture create(GameTestHelper helper) {
        TrinityDataCoreBlockEntity host = placeHost(helper);

        buildStructure(
                helper,
                host,
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 1),
                "main");
        host.serverTick();
        helper.assertTrue(host.isStructureFormed(), "Trinity main structure should form: " +
                host.getLastFailureReason() + " at " + host.getLastFailurePosition());

        buildStructure(
                helper,
                host,
                TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1),
                "CPU");
        buildStructure(
                helper,
                host,
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1),
                "crafting");
        host.requestStructureRecheck();
        host.serverTick();

        helper.assertTrue(host.isCpuStructureFormed(), "Trinity CPU structure should form: " +
                host.getCpuLastFailureReason() + " at " + host.getCpuLastFailurePosition());
        helper.assertTrue(host.isCraftingStructureFormed(), "Trinity crafting structure should form: " +
                host.getCraftingLastFailureReason() + " at " + host.getCraftingLastFailurePosition());
        helper.assertTrue(!host.getCpuPartitions().isEmpty(), "Formed Trinity CPU structure should expose a CPU");
        helper.assertTrue(
                !host.getPatternCatalog().mountedCores().isEmpty(),
                "Formed Trinity crafting structure should expose P cores");

        List<TrinityAccessHatchBlockEntity> hatches = boundHatches(host);
        helper.assertValueEqual(hatches.size(), 2, "Trinity main structure should bind both access hatches");
        TrinityDataCoreGameTestFixture fixture = new TrinityDataCoreGameTestFixture(helper, host, hatches);
        fixture.registerCleanup();
        return fixture;
    }

    TrinityDataCoreBlockEntity host() {
        return this.host;
    }

    IGrid grid() {
        IGrid grid = this.host.accessGrid();
        if (grid == null) {
            throw new IllegalStateException("Trinity fixture has no active access grid");
        }
        return grid;
    }

    void awaitOnline() {
        connectGridWhenNodesAreReady();
        this.host.serverTick();
        refreshAccessHatches();

        IGrid grid = this.host.accessGrid();
        await(grid != null, "Trinity host is waiting for its access grid");
        await(this.host.hasActiveAccessHatch(), "Trinity host is waiting for an active access lease");
        await(this.host.isPatternProviderAvailable(), "Trinity pattern provider is not available yet");
        await(this.host.isCpuProviderAvailable(), "Trinity CPU provider is not available yet");
        await(this.hatches.stream().filter(this.host::isLeaseOwner).count() == 1L,
                "Trinity access lease has not elected exactly one hatch");
        await(grid.getCraftingService().getCpus().containsAll(this.host.getCpuPartitions()),
                "AE2 crafting service has not published all Trinity CPUs yet");
    }

    void refreshAccessHatches() {
        for (TrinityAccessHatchBlockEntity hatch : this.hatches) {
            hatch.refreshTrinityAccess();
        }
    }

    @Override
    public void close() {
        if (this.gridPower != null) {
            this.gridPower.destroy();
            this.gridPower = null;
        }
    }

    private void registerCleanup() {
        this.helper.testInfo.addListener(new GameTestListener() {

            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
                // The fixture is registered after its test structure has loaded.
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                close();
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                close();
            }

            @Override
            public void testAddedForRerun(GameTestInfo testInfo,
                                          GameTestInfo rerunTestInfo,
                                          GameTestRunner runner) {
                close();
            }
        });
    }

    private void connectGridWhenNodesAreReady() {
        if (this.gridPower != null) {
            return;
        }

        List<IGridNode> accessNodes = new ArrayList<>(this.hatches.size());
        for (TrinityAccessHatchBlockEntity hatch : this.hatches) {
            IGridNode node = hatch.getMainNode().getNode();
            if (node == null) {
                throw new GameTestAssertException("Trinity access node is still initializing");
            }
            accessNodes.add(node);
        }

        GridPower power = new GridPower(this.helper.getLevel());
        try {
            for (IGridNode accessNode : accessNodes) {
                power.connect(accessNode);
            }
        } catch (RuntimeException exception) {
            power.destroy();
            throw exception;
        }
        this.gridPower = power;
    }

    private static TrinityDataCoreBlockEntity placeHost(GameTestHelper helper) {
        helper.setBlock(LOCAL_ORIGIN, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        BlockPos origin = helper.absolutePos(LOCAL_ORIGIN);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(origin);
        if (blockEntity instanceof TrinityDataCoreBlockEntity host) {
            return host;
        }
        throw new IllegalStateException("Missing Trinity Data Core block entity at " + origin);
    }

    private static void buildStructure(GameTestHelper helper,
                                       TrinityDataCoreBlockEntity host,
                                       int structureIndex,
                                       Map<String, Integer> tierSelections,
                                       String structureName) {
        Result result = TrinityDataCoreBlockEntity.executeAutoBuild(
                helper.getLevel(),
                helper.makeMockPlayer(GameType.CREATIVE),
                host.getBlockPos(),
                Direction.SOUTH,
                false,
                new TrinityAutoBuildRequest(
                        structureIndex,
                        new TrinityAutoBuildOptions(true, 1, tierSelections)));
        helper.assertTrue(result.success(), "Trinity " + structureName + " auto-build should commit: " +
                result.failure());
        helper.assertTrue(result.placed() > 0, "Trinity " + structureName + " auto-build should place blocks");
    }

    private static List<TrinityAccessHatchBlockEntity> boundHatches(TrinityDataCoreBlockEntity host) {
        List<TrinityAccessHatchBlockEntity> hatches = new ArrayList<>();
        String mainStructure = TrinityDataCoreBlockEntity.autoBuildStructureName(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX);
        for (CompartmentPart part : host.compartmentHost$getCompartments(mainStructure)) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch) {
                hatches.add(hatch);
            }
        }
        return List.copyOf(hatches);
    }

    private static void await(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static final class GridPower implements IAEPowerStorage {

        private static final IGridNodeListener<GridPower> NODE_LISTENER = (owner, node) -> {};

        private final IManagedGridNode managedNode;

        private GridPower(ServerLevel level) {
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
    }
}
