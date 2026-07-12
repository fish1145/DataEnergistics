package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AECableType;

import java.util.List;

/** Exercises tower target discovery against real block capabilities and managed AE nodes. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataDistributionTowerDiscoveryGameTest {

    private static final BlockPos REGULAR_CHARGER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos EXTENDED_CHARGER_POS = new BlockPos(3, 2, 1);
    private static final BlockPos TOWER_POS = new BlockPos(20, 4, 25);
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

        BlockPos firstPart = DataSanctumBlockEntity.getPartPos(
                sanctum.getBlockPos(), SANCTUM_FACING, -2, -2, 3);
        BlockPos secondPart = DataSanctumBlockEntity.getPartPos(
                sanctum.getBlockPos(), SANCTUM_FACING, 2, 1, 1);
        BlockPos networkPort = DataSanctumBlockEntity.getPartPos(
                sanctum.getBlockPos(), SANCTUM_FACING, 0, 2, 0);

        helper.startSequence()
                .thenWaitUntil(() -> assertSanctumPortDiscovery(helper, level, networkPort))
                .thenExecute(() -> {
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
}
