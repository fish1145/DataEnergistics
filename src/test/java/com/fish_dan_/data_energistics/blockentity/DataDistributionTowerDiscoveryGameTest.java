package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.VirtualGridBridge;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectorBindResult;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.VersionedTowerBindingCodec;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkDomain;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerVirtualDeviceState;
import com.fish_dan_.data_energistics.registry.DEBlocks;

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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.parts.IPart;
import appeng.api.util.AECableType;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.util.SettingsFrom;

import java.util.List;

/**
 * Exercises tower target discovery against real block capabilities and managed AE nodes.
 */
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
    private static final long QUARANTINED_TRANSFER_ENERGY = (long) Integer.MAX_VALUE + 8_192L;

    private DataDistributionTowerDiscoveryGameTest() {}

    @TestHolder("data_distribution_tower_discovers_both_data_chargers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void discoversBothDataChargers(GameTestHelper helper) {
        helper.setBlock(REGULAR_CHARGER_POS, DEBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(EXTENDED_CHARGER_POS, DEBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());

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
        helper.setBlock(CONNECTED_REGULAR_CHARGER_POS, DEBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(CONNECTED_EXTENDED_CHARGER_POS, DEBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());
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

    @TestHolder("data_distribution_tower_recovers_persisted_link_after_target_node_ready")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 400)
    public static void recoversPersistedLinkAfterTargetNodeReady(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, TOWER_POS);
        helper.setBlock(CONNECTED_REGULAR_CHARGER_POS, DEBlocks.DATA_CHARGER.get().defaultBlockState());
        DataChargerBlockEntity charger = requireDataCharger(helper, CONNECTED_REGULAR_CHARGER_POS);
        GridPower power = new GridPower(helper);
        CompoundTag savedData = new CompoundTag();
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        DataDistributionTowerBlockEntity[] restoredTower = new DataDistributionTowerBlockEntity[1];
        DataChargerBlockEntity[] restoredCharger = new DataChargerBlockEntity[1];
        BlockPos towerPos = helper.absolutePos(TOWER_POS);
        BlockPos chargerPos = helper.absolutePos(CONNECTED_REGULAR_CHARGER_POS);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertTowerNodeReady(helper, tower);
                    helper.assertTrue(charger.getMainNode().getNode() != null, "The initial charger node must be ready");
                })
                .thenExecute(() -> {
                    power.connect(requireNode(tower));
                    assertAeBindSuccess(helper, tower, charger.getBlockPos(), "Persisted recovery charger");
                })
                .thenWaitUntil(() -> assertConnectedTarget(
                        helper, tower, charger.getBlockPos(), requireNode(charger), "Persisted recovery charger"))
                .thenExecute(() -> {
                    tower.saveAdditional(savedData, registries);
                    charger.setRemoved();
                    helper.setBlock(CONNECTED_REGULAR_CHARGER_POS, Blocks.STONE.defaultBlockState());
                    tower.setRemoved();
                    helper.getLevel().removeBlockEntity(towerPos);

                    BlockState towerState = helper.getLevel().getBlockState(towerPos);
                    DataDistributionTowerBlockEntity restored = new DataDistributionTowerBlockEntity(towerPos, towerState);
                    restoredTower[0] = restored;
                    helper.getLevel().setBlockEntity(restored);
                    restored.loadTag(savedData, registries);
                    restored.onLoad();
                })
                .thenWaitUntil(() -> assertTowerNodeReady(helper, restoredTower[0]))
                .thenExecute(() -> power.connect(requireNode(restoredTower[0])))
                .thenIdle(50)
                .thenExecute(() -> {
                    helper.assertTrue(
                            restoredTower[0].linkedPositions().contains(chargerPos),
                            "A target without a ready block entity must remain persisted after recovery retries");
                    helper.assertValueEqual(
                            restoredTower[0].getTargetTransferInfo(chargerPos).channelConnections(),
                            0,
                            "A target without a ready AE node must not retain a live connection");

                    helper.setBlock(CONNECTED_REGULAR_CHARGER_POS, DEBlocks.DATA_CHARGER.get().defaultBlockState());
                    DataChargerBlockEntity restored = requireDataCharger(helper, CONNECTED_REGULAR_CHARGER_POS);
                    restoredCharger[0] = restored;
                })
                .thenWaitUntil(() -> {
                    DataChargerBlockEntity restored = restoredCharger[0];
                    helper.assertTrue(restored != null, "The replacement charger block entity must be created");
                    IGridNode targetNode = restored.getMainNode().getNode();
                    helper.assertTrue(targetNode != null, "The replacement charger AE node must be ready");
                    assertConnectedTarget(helper, restoredTower[0], chargerPos, targetNode, "Recovered persisted charger");
                })
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_collects_every_unique_host_node")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void collectsEveryUniqueHostNode(GameTestHelper helper) {
        helper.setBlock(REGULAR_CHARGER_POS, DEBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(EXTENDED_CHARGER_POS, DEBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());

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

    @TestHolder("data_distribution_tower_preserves_ae_part_external_node")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void preservesAePartExternalNode(GameTestHelper helper) {
        CableBusBlockEntity partHost = placeCableBus(helper, REGULAR_CHARGER_POS);
        IPart quartzFiber = partHost.addPart(AEParts.QUARTZ_FIBER.get(), Direction.EAST, null);
        helper.assertTrue(quartzFiber != null, "The quartz fiber must be installed");
        helper.assertTrue(partHost.getPart(null) == null, "The quartz fiber host must not contain a center cable");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    IGridNode internalNode = quartzFiber.getGridNode();
                    IGridNode externalNode = partHost.getGridNode(Direction.EAST);
                    helper.assertTrue(internalNode != null, "The quartz fiber internal node must be ready");
                    helper.assertTrue(externalNode != null, "The quartz fiber external node must be ready");
                    helper.assertTrue(internalNode != externalNode, "The quartz fiber nodes must remain distinct");

                    List<IGridNode> discovered = DataDistributionTowerBlockEntity.getConnectableNodes(
                            helper.getLevel(), partHost.getBlockPos());
                    helper.assertValueEqual(
                            discovered.size(), 1,
                            "Only the quartz fiber node exposed through the six-face Capability may be discovered");
                    helper.assertTrue(discovered.getFirst() == externalNode, "The exposed external node must remain connectable");
                    helper.assertTrue(
                            !discovered.contains(internalNode),
                            "Capability-only discovery must not traverse the multipart host for its internal node");
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
                            summaries.size(),
                            2,
                            "The sanctum main node and network-port node must produce independent device rows");
                    helper.assertTrue(
                            summaries.stream().allMatch(summary -> summary.pos().equals(sanctum.getBlockPos())),
                            "Every positioned sanctum device must use its owning block entity position");
                    helper.assertTrue(
                            summaries.stream().allMatch(summary -> summary.transferInfo()
                                    .bindingAnchor()
                                    .equals(networkPort)),
                            "Every device payload must retain the canonical network port as its binding anchor");
                    helper.assertTrue(
                            summaries.stream().map(summary -> summary.transferInfo().deviceKey()).distinct().count() == 2,
                            "The two sanctum nodes must retain distinct stable device keys");
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
        tower.setQuarantinedTransferEnergy(QUARANTINED_TRANSFER_ENERGY);
        tower.saveAdditional(savedData, registries);
        tower.setBufferedTransferEnergy(0L);
        tower.setQuarantinedTransferEnergy(0L);
        helper.assertValueEqual(tower.bufferedTransferEnergy(), 0L, "The transfer buffer must be cleared before loading");
        helper.assertValueEqual(
                tower.quarantinedTransferEnergy(),
                0L,
                "The quarantined transfer energy must be cleared before loading");

        tower.loadTag(savedData, registries);
        helper.assertValueEqual(
                tower.bufferedTransferEnergy(),
                BUFFERED_TRANSFER_ENERGY,
                "The transfer buffer must survive an NBT round trip");
        helper.assertValueEqual(
                tower.quarantinedTransferEnergy(),
                QUARANTINED_TRANSFER_ENERGY,
                "The quarantined transfer energy must survive an NBT round trip");
        helper.succeed();
    }

    @TestHolder("data_distribution_tower_save_does_not_load_linked_target_chunks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void saveDoesNotLoadLinkedTargetChunks(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, REGULAR_CHARGER_POS);
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        BlockPos unloadedTarget = tower.getBlockPos().offset(4096, 0, 4096);

        helper.assertFalse(level.hasChunkAt(unloadedTarget), "The remote target chunk must start unloaded");
        CompoundTag loadedData = new CompoundTag();
        ListTag linkedPositions = new ListTag();
        CompoundTag linkedPosition = new CompoundTag();
        linkedPosition.put("pos", NbtUtils.writeBlockPos(unloadedTarget));
        linkedPositions.add(linkedPosition);
        loadedData.put("linked_positions", linkedPositions);
        tower.loadTag(loadedData, registries);

        CompoundTag savedData = new CompoundTag();
        tower.saveAdditional(savedData, registries);

        helper.assertFalse(level.hasChunkAt(unloadedTarget), "Saving the tower must not load a linked target chunk");
        helper.assertValueEqual(
                savedData.getList(VersionedTowerBindingCodec.BINDINGS_TAG, Tag.TAG_COMPOUND).size(),
                1,
                "Saving must retain the unloaded target in the versioned binding list");
        helper.assertFalse(
                savedData.contains(VersionedTowerBindingCodec.LEGACY_LINKED_POSITIONS_TAG),
                "Saving must remove the migrated legacy linked-position list");
        helper.succeed();
    }

    @TestHolder("data_distribution_tower_preserves_buffered_energy_in_dismantle_item")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void preservesBufferedEnergyInDismantleItem(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, REGULAR_CHARGER_POS);

        tower.setBufferedTransferEnergy(BUFFERED_TRANSFER_ENERGY);
        tower.setQuarantinedTransferEnergy(QUARANTINED_TRANSFER_ENERGY);
        DataComponentMap dismantleComponents = tower.exportSettings(SettingsFrom.DISMANTLE_ITEM, null);
        tower.setBufferedTransferEnergy(0L);
        tower.setQuarantinedTransferEnergy(0L);
        tower.importSettings(SettingsFrom.DISMANTLE_ITEM, dismantleComponents, null);
        helper.assertValueEqual(
                tower.bufferedTransferEnergy(),
                BUFFERED_TRANSFER_ENERGY,
                "The dismantled tower item must retain its transfer buffer");
        helper.assertValueEqual(
                tower.quarantinedTransferEnergy(),
                QUARANTINED_TRANSFER_ENERGY,
                "The dismantled tower item must retain quarantined transfer energy");

        DataComponentMap memoryCardComponents = tower.exportSettings(SettingsFrom.MEMORY_CARD, null);
        tower.setBufferedTransferEnergy(0L);
        tower.setQuarantinedTransferEnergy(0L);
        tower.importSettings(SettingsFrom.MEMORY_CARD, memoryCardComponents, null);
        helper.assertValueEqual(
                tower.bufferedTransferEnergy(),
                0L,
                "Memory card settings must not duplicate buffered transfer energy");
        helper.assertValueEqual(
                tower.quarantinedTransferEnergy(),
                0L,
                "Memory card settings must not duplicate quarantined transfer energy");
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
        helper.assertTrue(portState.is(DEBlocks.DATA_SANCTUM.get()), "The expected sanctum network port block must exist");
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

    private static CableBusBlockEntity placeCableBus(GameTestHelper helper, BlockPos localPos) {
        helper.setBlock(localPos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(localPos);
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            return cableBus;
        }
        helper.fail("Expected an AE cable bus block entity", localPos);
        throw new IllegalStateException("Placed AE cable bus has no matching block entity");
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
        helper.assertTrue(result.success(), description + " must bind to the real tower: " + result.failure());
        helper.assertTrue(result.aeSupported(), description + " binding must discover an AE target");
    }

    private static void assertConnectedTarget(
                                              GameTestHelper helper,
                                              DataDistributionTowerBlockEntity tower,
                                              BlockPos targetPos,
                                              IGridNode targetNode,
                                              String description) {
        IGridNode towerNode = requireNode(tower);
        TargetTransferInfo transferInfo = tower.getTargetTransferInfo(targetPos);
        List<IGridNode> targetNodes = targetNode.getGrid().getService(TowerNetworkDomain.class).localNodes();
        int expectedChannels = Math.toIntExact(targetNodes
                .stream()
                .filter(node -> node.hasFlag(GridFlags.REQUIRE_CHANNEL))
                .count());
        helper.assertValueEqual(
                transferInfo.requestedChannels(),
                expectedChannels,
                description + " must request exactly its per-device virtual channel cost");
        helper.assertValueEqual(
                transferInfo.channelConnections(),
                expectedChannels,
                description + " must receive every requested virtual channel lease");
        helper.assertValueEqual(
                transferInfo.state(),
                TowerVirtualDeviceState.ALLOCATED,
                description + " must be allocated through the virtual bridge");
        helper.assertTrue(towerNode.getGrid() != null, "The powered tower must belong to an AE grid");
        helper.assertTrue(
                targetNode.getGrid() != towerNode.getGrid(),
                description + " must retain a distinct subordinate Grid identity");
        VirtualGridBridge primaryBridge = (VirtualGridBridge) towerNode.getGrid();
        helper.assertTrue(
                targetNodes.stream().allMatch(primaryBridge::containsIncomingVirtualMember),
                description + " must register every allocated subordinate node as a primary-grid virtual member");
        for (IGridConnection connection : towerNode.getConnections()) {
            helper.assertTrue(
                    connection.getOtherSide(towerNode) != targetNode,
                    description + " must not create a physical tower-to-target IGridConnection");
        }

        long matchingRows = tower.getBoundTargetSummaries(Integer.MAX_VALUE).stream()
                .filter(summary -> summary.transferInfo().bindingAnchor().equals(targetPos))
                .count();
        helper.assertValueEqual(
                matchingRows,
                (long) targetNodes.size(),
                description + " must expose one GUI row per subordinate device");
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
            BlockState state = DEBlocks.DATA_DISTRIBUTION_TOWER.get()
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
        BlockState state = DEBlocks.DATA_SANCTUM.get()
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
