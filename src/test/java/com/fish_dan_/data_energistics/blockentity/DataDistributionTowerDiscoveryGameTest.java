package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectorBindResult;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.AECapabilities;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.util.AECableType;
import appeng.util.SettingsFrom;

import java.util.List;

/** Exercises tower target discovery against real block capabilities and managed AE nodes. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataDistributionTowerDiscoveryGameTest {

    private static final BlockPos REGULAR_CHARGER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos EXTENDED_CHARGER_POS = new BlockPos(3, 2, 1);
    private static final BlockPos TOWER_POS = new BlockPos(20, 4, 25);
    private static final BlockPos CONNECTED_REGULAR_CHARGER_POS = new BlockPos(16, 4, 25);
    private static final BlockPos CONNECTED_EXTENDED_CHARGER_POS = new BlockPos(18, 4, 25);
    private static final BlockPos SANCTUM_MAIN_POS = new BlockPos(25, 4, 25);
    private static final Direction SANCTUM_FACING = Direction.NORTH;
    private static final long BUFFERED_TRANSFER_ENERGY = (long) Integer.MAX_VALUE + 4_096L;

    private DataDistributionTowerDiscoveryGameTest() {}

    @TestHolder("data_distribution_tower_discovers_both_data_chargers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void discoversBothDataChargers(GameTestHelper helper) {
        helper.setBlock(REGULAR_CHARGER_POS, ModBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(EXTENDED_CHARGER_POS, ModBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());

        DataChargerBlockEntity regular = requireDataCharger(helper, REGULAR_CHARGER_POS);
        DataChargerBlockEntity extended = requireDataCharger(helper, EXTENDED_CHARGER_POS);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertChargerDiscovery(helper, REGULAR_CHARGER_POS, regular, "Regular data charger");
                    assertChargerDiscovery(helper, EXTENDED_CHARGER_POS, extended, "Extended data charger");
                })
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_connects_and_displays_both_data_chargers")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 200)
    public static void connectsAndDisplaysBothDataChargers(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, TOWER_POS);
        helper.setBlock(CONNECTED_REGULAR_CHARGER_POS, ModBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(CONNECTED_EXTENDED_CHARGER_POS, ModBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());
        DataChargerBlockEntity regular = requireDataCharger(helper, CONNECTED_REGULAR_CHARGER_POS);
        DataChargerBlockEntity extended = requireDataCharger(helper, CONNECTED_EXTENDED_CHARGER_POS);
        GridPower power = new GridPower(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertTowerNodeReady(helper, tower);
                    assertChargerDiscovery(helper, CONNECTED_REGULAR_CHARGER_POS, regular, "Regular data charger");
                    assertChargerDiscovery(helper, CONNECTED_EXTENDED_CHARGER_POS, extended, "Extended data charger");
                })
                .thenExecute(() -> {
                    power.connect(requireNode(tower));
                    assertAeBindSuccess(helper, tower, regular.getBlockPos(), "Regular data charger");
                    assertAeBindSuccess(helper, tower, extended.getBlockPos(), "Extended data charger");
                })
                .thenWaitUntil(() -> {
                    assertConnectedTarget(helper, tower, regular.getBlockPos(), requireNode(regular), "Regular data charger");
                    assertConnectedTarget(helper, tower, extended.getBlockPos(), requireNode(extended), "Extended data charger");
                    List<BoundTargetSummary> summaries = tower.getBoundTargetSummaries(Integer.MAX_VALUE);
                    helper.assertValueEqual(summaries.size(), 2, "Both connected data chargers must appear in the GUI");
                })
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_collects_every_unique_host_node")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void collectsEveryUniqueHostNode(GameTestHelper helper) {
        helper.setBlock(REGULAR_CHARGER_POS, ModBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(EXTENDED_CHARGER_POS, ModBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());

        DataChargerBlockEntity regular = requireDataCharger(helper, REGULAR_CHARGER_POS);
        DataChargerBlockEntity extended = requireDataCharger(helper, EXTENDED_CHARGER_POS);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    IGridNode firstNode = regular.getMainNode().getNode();
                    IGridNode secondNode = extended.getMainNode().getNode();
                    helper.assertTrue(firstNode != null, "The first real managed charger node must be ready");
                    helper.assertTrue(secondNode != null, "The second real managed charger node must be ready");
                    helper.assertTrue(firstNode != secondNode, "Separate charger hosts must own separate managed nodes");

                    List<IGridNode> discovered = DataDistributionTowerBlockEntity.collectConnectableNodes(
                            new MultiNodeHost(firstNode, secondNode));
                    helper.assertValueEqual(
                            discovered.size(), 2, "Repeated sides must not hide a later distinct host node");
                    helper.assertTrue(
                            discovered.get(0) == firstNode, "Discovery must preserve the first node's direction order");
                    helper.assertTrue(
                            discovered.get(1) == secondNode, "Discovery must retain the second unique managed node");
                })
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_normalizes_data_sanctum_targets")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 200)
    public static void normalizesDataSanctumTargets(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, TOWER_POS);
        DataSanctumBlockEntity sanctum = placeSanctum(helper, SANCTUM_MAIN_POS, SANCTUM_FACING);
        ServerLevel level = helper.getLevel();
        GridPower power = new GridPower(helper);

        BlockPos firstPart = DataSanctumBlockEntity.getPartPos(
                sanctum.getBlockPos(), SANCTUM_FACING, -2, -2, 3);
        BlockPos secondPart = DataSanctumBlockEntity.getPartPos(
                sanctum.getBlockPos(), SANCTUM_FACING, 2, 1, 1);
        BlockPos networkPort = DataSanctumBlockEntity.getPartPos(
                sanctum.getBlockPos(), SANCTUM_FACING, 0, 2, 0);

        helper.startSequence()
                .thenWaitUntil(() -> assertSanctumPortDiscovery(helper, level, networkPort))
                .thenExecute(() -> {
                    power.connect(requireNode(tower));
                    assertAeBindSuccess(
                            helper,
                            tower,
                            firstPart,
                            "Data sanctum non-port structure part");
                })
                .thenWaitUntil(() -> {
                    IGridNode portNode = DataDistributionTowerBlockEntity.getConnectableNodes(level, networkPort).getFirst();
                    assertConnectedTarget(helper, tower, networkPort, portNode, "Data sanctum network port");
                    helper.assertValueEqual(
                            DataSanctumBlockEntity.findNetworkPortPos(level, firstPart),
                            networkPort,
                            "A corner sanctum part must normalize to the network port");
                    helper.assertValueEqual(
                            DataSanctumBlockEntity.findNetworkPortPos(level, secondPart),
                            networkPort,
                            "An interior sanctum part must normalize to the network port");

                    tower.setTargetTransferMode(firstPart, TargetTransferMode.DISABLED);
                    tower.setTargetTransferMode(secondPart, TargetTransferMode.DISABLED);
                    List<BoundTargetSummary> summaries = tower.getBoundTargetSummaries(Integer.MAX_VALUE);

                    helper.assertValueEqual(
                            summaries.size(), 1, "All parts of one sanctum must produce one GUI target");
                    helper.assertValueEqual(
                            summaries.getFirst().pos(),
                            networkPort,
                            "The sanctum GUI target must use the canonical network port position");
                })
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_persists_buffered_transfer_energy")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void persistsBufferedTransferEnergy(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, REGULAR_CHARGER_POS);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag savedData = new CompoundTag();

        tower.setBufferedTransferEnergy(BUFFERED_TRANSFER_ENERGY);
        tower.saveAdditional(savedData, registries);
        tower.setBufferedTransferEnergy(0L);
        helper.assertValueEqual(tower.bufferedTransferEnergy(), 0L, "The transfer buffer must be cleared before loading");

        tower.loadTag(savedData, registries);
        helper.assertValueEqual(
                tower.bufferedTransferEnergy(),
                BUFFERED_TRANSFER_ENERGY,
                "The transfer buffer must survive an NBT round trip");
        helper.succeed();
    }

    @TestHolder("data_distribution_tower_preserves_buffered_energy_in_dismantle_item")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void preservesBufferedEnergyInDismantleItem(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, REGULAR_CHARGER_POS);

        tower.setBufferedTransferEnergy(BUFFERED_TRANSFER_ENERGY);
        DataComponentMap dismantleComponents = tower.exportSettings(SettingsFrom.DISMANTLE_ITEM, null);
        tower.setBufferedTransferEnergy(0L);
        tower.importSettings(SettingsFrom.DISMANTLE_ITEM, dismantleComponents, null);
        helper.assertValueEqual(
                tower.bufferedTransferEnergy(),
                BUFFERED_TRANSFER_ENERGY,
                "The dismantled tower item must retain its transfer buffer");

        DataComponentMap memoryCardComponents = tower.exportSettings(SettingsFrom.MEMORY_CARD, null);
        tower.setBufferedTransferEnergy(0L);
        tower.importSettings(SettingsFrom.MEMORY_CARD, memoryCardComponents, null);
        helper.assertValueEqual(
                tower.bufferedTransferEnergy(),
                0L,
                "Memory card settings must not duplicate buffered transfer energy");
        helper.succeed();
    }

    private static void assertChargerDiscovery(
                                               GameTestHelper helper, BlockPos localPos, DataChargerBlockEntity charger, String description) {
        IGridNode mainNode = charger.getMainNode().getNode();
        helper.assertTrue(mainNode != null, description + " managed node must be ready");

        int exposedSideCount = 0;
        for (Direction direction : Direction.values()) {
            IGridNode sideNode = charger.getGridNode(direction);
            if (sideNode != null) {
                exposedSideCount++;
                helper.assertTrue(sideNode == mainNode, description + " must expose the same node on every cable side");
            }
        }
        helper.assertValueEqual(exposedSideCount, 5, description + " must expose every side except its front");

        List<IGridNode> discovered = DataDistributionTowerBlockEntity.getConnectableNodes(
                helper.getLevel(), helper.absolutePos(localPos));
        helper.assertValueEqual(discovered.size(), 1, description + " exposed sides must be identity-deduplicated");
        helper.assertTrue(discovered.getFirst() == mainNode, description + " discovery must return its managed AE node");
    }

    private static void assertSanctumPortDiscovery(
                                                   GameTestHelper helper, ServerLevel level, BlockPos networkPort) {
        BlockState portState = level.getBlockState(networkPort);
        helper.assertTrue(portState.is(ModBlocks.DATA_SANCTUM.get()), "The expected sanctum network port block must exist");
        helper.assertTrue(
                DataSanctumBlockEntity.isNetworkPortPart(portState),
                "The expected sanctum network port must carry the port offsets");
        BlockPos resolvedMainPos = DataSanctumBlockEntity.getMainPos(networkPort, portState);
        helper.assertTrue(
                level.getBlockEntity(resolvedMainPos) instanceof DataSanctumBlockEntity,
                "The sanctum network port must resolve its main block entity");
        helper.assertValueEqual(
                DataSanctumBlockEntity.findNetworkPortPos(level, networkPort),
                networkPort,
                "The network port must normalize to itself");

        IInWorldGridNodeHost portHost = level.getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST, networkPort, null);
        helper.assertTrue(portHost != null, "The sanctum network port must expose an AE host capability");

        IGridNode expectedNode = null;
        for (Direction direction : Direction.values()) {
            IGridNode sideNode = portHost.getGridNode(direction);
            helper.assertTrue(sideNode != null, "The sanctum network port must expose an AE node on " + direction);
            if (expectedNode == null) {
                expectedNode = sideNode;
            } else {
                helper.assertTrue(
                        sideNode == expectedNode, "Every sanctum network port side must expose the same node identity");
            }
        }

        List<IGridNode> discovered = DataDistributionTowerBlockEntity.getConnectableNodes(level, networkPort);
        helper.assertValueEqual(discovered.size(), 1, "Six sanctum port sides must be identity-deduplicated");
        helper.assertTrue(
                discovered.getFirst() == expectedNode, "Tower discovery must retain the sanctum network port node");
    }

    private static DataChargerBlockEntity requireDataCharger(GameTestHelper helper, BlockPos localPos) {
        BlockEntity blockEntity = helper.getBlockEntity(localPos);
        if (blockEntity instanceof DataChargerBlockEntity charger) {
            return charger;
        }
        helper.fail("Expected a data charger block entity", localPos);
        throw new IllegalStateException("Placed data charger has no matching block entity");
    }

    private static void assertTowerNodeReady(GameTestHelper helper, DataDistributionTowerBlockEntity tower) {
        helper.assertTrue(tower.getMainNode().getNode() != null, "The data distribution tower managed node must be ready");
    }

    private static void assertAeBindSuccess(
                                            GameTestHelper helper,
                                            DataDistributionTowerBlockEntity tower,
                                            BlockPos targetPos,
                                            String description) {
        ConnectorBindResult result = tower.bindTargetFromConnector(targetPos);
        helper.assertTrue(result.success(), description + " must bind to the real tower");
        helper.assertTrue(result.aeSupported(), description + " binding must discover an AE target");
    }

    private static void assertConnectedTarget(
                                              GameTestHelper helper,
                                              DataDistributionTowerBlockEntity tower,
                                              BlockPos targetPos,
                                              IGridNode targetNode,
                                              String description) {
        IGridNode towerNode = requireNode(tower);
        helper.assertValueEqual(
                tower.getTargetTransferInfo(targetPos).channelConnections(),
                1,
                description + " must have exactly one identity-deduplicated wireless connection");
        helper.assertTrue(towerNode.getGrid() != null, "The powered tower must belong to an AE grid");
        helper.assertTrue(targetNode.getGrid() == towerNode.getGrid(), description + " must join the tower's real AE grid");

        long matchingRows = tower.getBoundTargetSummaries(Integer.MAX_VALUE).stream()
                .filter(summary -> summary.pos().equals(targetPos))
                .count();
        helper.assertValueEqual(matchingRows, 1L, description + " must appear exactly once in the GUI");
    }

    private static IGridNode requireNode(DataDistributionTowerBlockEntity tower) {
        IGridNode node = tower.getMainNode().getNode();
        if (node == null) {
            throw new IllegalStateException("Data distribution tower managed node was not created");
        }
        return node;
    }

    private static IGridNode requireNode(DataChargerBlockEntity charger) {
        IGridNode node = charger.getMainNode().getNode();
        if (node == null) {
            throw new IllegalStateException("Data charger managed node was not created");
        }
        return node;
    }

    private static DataDistributionTowerBlockEntity placeTower(GameTestHelper helper, BlockPos localBasePos) {
        ServerLevel level = helper.getLevel();
        for (int part = 2; part >= 0; part--) {
            BlockState state = ModBlocks.DATA_DISTRIBUTION_TOWER.get()
                    .defaultBlockState()
                    .setValue(DataDistributionTowerBlock.PART, part)
                    .setValue(DataDistributionTowerBlock.FACING, Direction.NORTH)
                    .setValue(DataDistributionTowerBlock.ACTIVE, false);
            level.setBlock(helper.absolutePos(localBasePos.above(part)), state, Block.UPDATE_CLIENTS);
        }

        BlockEntity blockEntity = helper.getBlockEntity(localBasePos);
        if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
            return tower;
        }
        helper.fail("Expected a data distribution tower block entity", localBasePos);
        throw new IllegalStateException("Placed data distribution tower has no matching block entity");
    }

    private static DataSanctumBlockEntity placeSanctum(
                                                       GameTestHelper helper, BlockPos localMainPos, Direction facing) {
        ServerLevel level = helper.getLevel();
        placeSanctumPart(level, helper, localMainPos, facing, 0, 0, 0);
        for (int offsetY = 0; offsetY <= 3; offsetY++) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0 && offsetY == 0) {
                        continue;
                    }
                    placeSanctumPart(level, helper, localMainPos, facing, offsetX, offsetZ, offsetY);
                }
            }
        }

        BlockEntity blockEntity = helper.getBlockEntity(localMainPos);
        if (blockEntity instanceof DataSanctumBlockEntity sanctum) {
            return sanctum;
        }
        helper.fail("Expected a data sanctum block entity", localMainPos);
        throw new IllegalStateException("Placed data sanctum has no matching block entity");
    }

    private static void placeSanctumPart(
                                         ServerLevel level,
                                         GameTestHelper helper,
                                         BlockPos localMainPos,
                                         Direction facing,
                                         int offsetX,
                                         int offsetZ,
                                         int offsetY) {
        BlockPos localPartPos = DataSanctumBlockEntity.getPartPos(
                localMainPos, facing, offsetX, offsetZ, offsetY);
        BlockState state = ModBlocks.DATA_SANCTUM.get()
                .defaultBlockState()
                .setValue(DataSanctumBlock.FACING, facing)
                .setValue(DataSanctumBlock.OFFSET_X, DataSanctumBlockEntity.encodeOffsetX(offsetX))
                .setValue(DataSanctumBlock.OFFSET_Z, DataSanctumBlockEntity.encodeOffsetZ(offsetZ))
                .setValue(DataSanctumBlock.OFFSET_Y, offsetY)
                .setValue(DataSanctumBlock.ACTIVE, false)
                .setValue(DataSanctumBlock.MODE, 0);
        level.setBlock(helper.absolutePos(localPartPos), state, Block.UPDATE_CLIENTS);
    }

    private record MultiNodeHost(IGridNode firstNode, IGridNode secondNode) implements IInWorldGridNodeHost {

        @Override
        public IGridNode getGridNode(Direction direction) {
            return switch (direction) {
                case DOWN, UP -> this.firstNode;
                case NORTH -> this.secondNode;
                default -> null;
            };
        }

        @Override
        public AECableType getCableConnectionType(Direction direction) {
            return getGridNode(direction) == null ? AECableType.NONE : AECableType.COVERED;
        }
    }

    private static final class GridPower implements IAEPowerStorage {

        private static final IGridNodeListener<GridPower> NODE_LISTENER = (owner, node) -> {};

        private final IManagedGridNode managedNode;
        private boolean destroyed;

        private GridPower(GameTestHelper helper) {
            this.managedNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                    .setInWorldNode(false)
                    .setIdlePowerUsage(0.0D)
                    .addService(IAEPowerStorage.class, this);
            this.managedNode.create(helper.getLevel(), null);
            helper.testInfo.addListener(new GameTestListener() {

                @Override
                public void testStructureLoaded(GameTestInfo testInfo) {}

                @Override
                public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                    destroy();
                }

                @Override
                public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                    destroy();
                }

                @Override
                public void testAddedForRerun(GameTestInfo testInfo, GameTestInfo rerunTestInfo,
                                              GameTestRunner runner) {
                    destroy();
                }
            });
        }

        private void connect(IGridNode target) {
            GridHelper.createConnection(requirePowerNode(), target);
        }

        private IGridNode requirePowerNode() {
            IGridNode node = this.managedNode.getNode();
            if (node == null) {
                throw new IllegalStateException("Managed grid power node was not created");
            }
            return node;
        }

        private void destroy() {
            if (!this.destroyed) {
                this.destroyed = true;
                this.managedNode.destroy();
            }
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
