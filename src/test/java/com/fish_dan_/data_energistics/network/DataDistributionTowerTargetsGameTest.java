package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.DataChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.RangeAdjustmentMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.menu.DataDistributionTowerMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Verifies target search after the client has atomically assembled multiple payload batches. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataDistributionTowerTargetsGameTest {

    private static final int TARGET_COUNT = 70;
    private static final int MENU_ID = 17;
    private static final BlockPos TOWER_POS = new BlockPos(20, 4, 25);
    private static final BlockPos TARGET_GRID_ORIGIN = new BlockPos(13, 4, 18);

    private DataDistributionTowerTargetsGameTest() {}

    @TestHolder("data_distribution_tower_syncs_and_searches_seventy_real_targets")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 200)
    public static void syncsAndSearchesSeventyRealTargets(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, TOWER_POS);
        List<DataChargerBlockEntity> chargers = placeNamedChargers(helper, TARGET_COUNT);
        tower.setRangeAdjustmentMode(RangeAdjustmentMode.SCOPE);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<BoundTargetSummary> summaries = tower.getBoundTargetSummaries(Integer.MAX_VALUE);
                    helper.assertValueEqual(summaries.size(), TARGET_COUNT, "The real tower must discover all 70 charger targets");
                    helper.assertValueEqual(summaries.getLast().displayName(), "Target 69", "X-Y-Z ordering must retain target 69 after the first batch");
                    helper.assertTrue(
                            chargers.stream().allMatch(charger -> charger.getMainNode().getNode() != null),
                            "Every real charger managed node must be ready before menu synchronization");
                })
                .thenExecute(() -> assertMenuTargetSynchronization(helper, tower))
                .thenSucceed();
    }

    @TestHolder("data_distribution_tower_target_actions_require_current_snapshot")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 200)
    public static void targetActionsRequireCurrentSnapshot(GameTestHelper helper) {
        DataDistributionTowerBlockEntity tower = placeTower(helper, TOWER_POS);
        placeNamedChargers(helper, 1);
        tower.setRangeAdjustmentMode(RangeAdjustmentMode.SCOPE);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            tower.getMainNode().getNode() != null,
                            "The real tower node must finish normal initialization before validating target actions");
                    helper.assertValueEqual(
                            tower.getBoundTargetSummaries(Integer.MAX_VALUE).size(),
                            1,
                            "The real tower must discover the target before validating target actions");
                })
                .thenExecute(() -> assertTargetActionValidation(helper, tower))
                .thenSucceed();
    }

    private static List<DataChargerBlockEntity> placeNamedChargers(GameTestHelper helper, int count) {
        ArrayList<DataChargerBlockEntity> chargers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BlockPos localPos = TARGET_GRID_ORIGIN.offset(index / 7, 0, index % 7);
            helper.setBlock(localPos, ModBlocks.DATA_CHARGER.get().defaultBlockState());
            BlockEntity blockEntity = helper.getBlockEntity(localPos);
            if (!(blockEntity instanceof DataChargerBlockEntity charger)) {
                helper.fail("Expected a real data charger block entity", localPos);
                throw new IllegalStateException("Placed target has no data charger block entity");
            }
            charger.setName("Target " + index);
            chargers.add(charger);
        }
        return List.copyOf(chargers);
    }

    private static DataDistributionTowerBlockEntity placeTower(GameTestHelper helper, BlockPos localBasePos) {
        for (int part = 2; part >= 0; part--) {
            BlockState state = ModBlocks.DATA_DISTRIBUTION_TOWER.get()
                    .defaultBlockState()
                    .setValue(DataDistributionTowerBlock.PART, part)
                    .setValue(DataDistributionTowerBlock.FACING, Direction.NORTH)
                    .setValue(DataDistributionTowerBlock.ACTIVE, false);
            helper.getLevel().setBlock(helper.absolutePos(localBasePos.above(part)), state, Block.UPDATE_CLIENTS);
        }

        BlockEntity blockEntity = helper.getBlockEntity(localBasePos);
        if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
            return tower;
        }
        helper.fail("Expected a data distribution tower block entity", localBasePos);
        throw new IllegalStateException("Placed data distribution tower has no matching block entity");
    }

    private static void assertMenuTargetSynchronization(
                                                        GameTestHelper helper,
                                                        DataDistributionTowerBlockEntity tower) {
        ServerPlayer player = createCapturingPlayer(helper);
        CapturingPacketListener listener = (CapturingPacketListener) player.connection;
        DataDistributionTowerMenu serverMenu = new DataDistributionTowerMenu(MENU_ID, player.getInventory(), tower);

        serverMenu.broadcastChanges();

        List<DataDistributionTowerTargetsPayload> payloads = listener.targetPayloads();
        helper.assertValueEqual(payloads.size(), 2, "The real tower menu must split 70 targets into two packets");
        helper.assertValueEqual(payloads.getFirst().entries().size(), 64, "The first real menu packet must contain 64 targets");
        helper.assertValueEqual(payloads.getLast().entries().size(), 6, "The second real menu packet must contain the remaining targets");

        DataDistributionTowerMenu receivingMenu = new DataDistributionTowerMenu(MENU_ID, player.getInventory(), null);
        player.containerMenu = receivingMenu;
        DataDistributionTowerTargetsClientHandler.receive(payloads.getFirst(), player);
        helper.assertTrue(
                receivingMenu.boundTargetEntries.isEmpty(),
                "The client handler must not expose a partial target revision after the first packet");

        DataDistributionTowerTargetsClientHandler.receive(payloads.getLast(), player);
        helper.assertValueEqual(
                receivingMenu.boundTargetEntries.size(),
                TARGET_COUNT,
                "The client handler must atomically publish all 70 real tower targets");

        String filter = PinyinUtil.normalizeSearch("Target 69");
        List<DataDistributionTowerTargetEntry> matches = receivingMenu.boundTargetEntries.stream()
                .filter(entry -> PinyinUtil.matchesSearch(
                        entry.displayName() + " (" + entry.kind().name() + ")", filter))
                .toList();
        helper.assertValueEqual(matches.size(), 1, "Search must find exactly one real target after entry 64");
        helper.assertValueEqual(matches.getFirst().displayName(), "Target 69", "Search must return the final real charger target");
    }

    private static void assertTargetActionValidation(GameTestHelper helper, DataDistributionTowerBlockEntity tower) {
        ServerPlayer player = createCapturingPlayer(helper);
        CapturingPacketListener listener = (CapturingPacketListener) player.connection;
        DataDistributionTowerMenu menu = new DataDistributionTowerMenu(MENU_ID, player.getInventory(), tower);
        menu.broadcastChanges();

        DataDistributionTowerTargetEntry target = listener.targetPayloads().getFirst().entries().getFirst();
        assertTargetTransferModeActionValidation(helper, tower, menu, target);
        DataDistributionTowerTargetsPayload snapshot = listener.targetPayloads().getLast();
        int initialMessageCount = listener.systemMessageCount();

        menu.receiveClientAction("focus_target", focusTargetPayload(snapshot.revision(), target.dimensionId().toString(), target.pos()));
        helper.assertValueEqual(listener.systemMessageCount(), initialMessageCount + 1,
                "A current snapshot target action must retain normal focus feedback");

        menu.receiveClientAction("focus_target", focusTargetPayload(snapshot.revision() + 1L, target.dimensionId().toString(), target.pos()));
        helper.assertValueEqual(listener.systemMessageCount(), initialMessageCount + 1,
                "A target action with a mismatched snapshot revision must not emit focus feedback");

        menu.receiveClientAction("focus_target", focusTargetPayload(snapshot.revision(), target.dimensionId().toString(), target.pos().east()));
        helper.assertValueEqual(listener.systemMessageCount(), initialMessageCount + 1,
                "A target action for an unbound position must not emit focus feedback");

        menu.receiveClientAction("focus_target", focusTargetPayload(snapshot.revision(), "invalid dimension", target.pos()));
        helper.assertValueEqual(listener.systemMessageCount(), initialMessageCount + 1,
                "A target action with an invalid dimension identifier must not emit focus feedback");

        CompoundTag savedState = new CompoundTag();
        tower.saveAdditional(savedState, helper.getLevel().registryAccess());
        tower.loadTag(savedState, helper.getLevel().registryAccess());
        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                target.dimensionId().toString(), target.pos(), TargetTransferMode.DISABLED.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.AUTO,
                "A transfer mode action captured before an NBT reload must not restore a target override");
        menu.receiveClientAction("focus_target", focusTargetPayload(snapshot.revision(), target.dimensionId().toString(), target.pos()));
        helper.assertValueEqual(listener.systemMessageCount(), initialMessageCount + 1,
                "A target action captured before an NBT reload must not emit focus feedback");

        BlockState targetState = helper.getLevel().getBlockState(target.pos());
        helper.assertTrue(helper.getLevel().removeBlock(target.pos(), false),
                "The current bound target must be removable before validating the stale action");
        DataDistributionTowerBlockEntity.onBlockBreak(
                new BlockEvent.BreakEvent(helper.getLevel(), target.pos(), targetState, player));
        helper.assertValueEqual(tower.getBoundTargetSummaries(Integer.MAX_VALUE).size(), 0,
                "Removing the target must invalidate the tower's current target summary");

        menu.receiveClientAction("focus_target", focusTargetPayload(snapshot.revision(), target.dimensionId().toString(), target.pos()));
        helper.assertValueEqual(listener.systemMessageCount(), initialMessageCount + 1,
                "A target action captured before target removal must not emit focus feedback");
        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                target.dimensionId().toString(), target.pos(), TargetTransferMode.DISABLED.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.AUTO,
                "A transfer mode action captured before target removal must not restore a target override");
    }

    private static void assertTargetTransferModeActionValidation(GameTestHelper helper,
                                                                 DataDistributionTowerBlockEntity tower,
                                                                 DataDistributionTowerMenu menu,
                                                                 DataDistributionTowerTargetEntry target) {
        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                target.dimensionId().toString(), target.pos(), TargetTransferMode.DISABLED.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.DISABLED,
                "A current target action must retain normal disabled-mode behavior");

        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                "minecraft:the_nether", target.pos(), TargetTransferMode.AUTO.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.DISABLED,
                "A transfer mode action for another dimension must not change the local target override");

        BlockPos unboundPos = target.pos().east();
        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                target.dimensionId().toString(), unboundPos, TargetTransferMode.DISABLED.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.DISABLED,
                "A transfer mode action for an unbound position must not change the current target override");
        helper.assertValueEqual(tower.getTargetTransferMode(unboundPos), TargetTransferMode.AUTO,
                "A transfer mode action for an unbound position must not create an override");

        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                "invalid dimension", target.pos(), TargetTransferMode.AUTO.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.DISABLED,
                "A transfer mode action with an invalid dimension must not change the target override");

        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayloadWithoutMode(
                target.dimensionId().toString(), target.pos()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.DISABLED,
                "A transfer mode action with a missing mode must not fall back to AUTO");

        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                target.dimensionId().toString(), target.pos(), Integer.MAX_VALUE));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.DISABLED,
                "A transfer mode action with an invalid mode must not fall back to AUTO");

        menu.receiveClientAction("set_target_transfer_mode", targetTransferModePayload(
                target.dimensionId().toString(), target.pos(), TargetTransferMode.AUTO.ordinal()));
        helper.assertValueEqual(tower.getTargetTransferMode(target.pos()), TargetTransferMode.AUTO,
                "A current target action must retain normal AUTO-mode behavior");
    }

    private static String focusTargetPayload(long revision, String dimensionId, BlockPos pos) {
        return "{\"targetSnapshotRevision\":" + revision + ",\"dimensionId\":\"" + dimensionId + "\",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + ",\"teleport\":false}";
    }

    private static String targetTransferModePayload(String dimensionId, BlockPos pos, int mode) {
        return "{\"dimensionId\":\"" + dimensionId + "\",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + ",\"mode\":" + mode + "}";
    }

    private static String targetTransferModePayloadWithoutMode(String dimensionId, BlockPos pos) {
        return "{\"dimensionId\":\"" + dimensionId + "\",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + "}";
    }

    private static ServerPlayer createCapturingPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "tower-target-sync");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        new CapturingPacketListener(server, player, profile);
        return player;
    }

    private static final class CapturingPacketListener extends ServerGamePacketListenerImpl {

        private final List<DataDistributionTowerTargetsPayload> targetPayloads = new ArrayList<>();
        private int systemMessageCount;

        private CapturingPacketListener(MinecraftServer server, ServerPlayer player, GameProfile profile) {
            super(
                    server,
                    new Connection(PacketFlow.SERVERBOUND),
                    player,
                    new CommonListenerCookie(
                            profile,
                            0,
                            ClientInformation.createDefault(),
                            false,
                            ConnectionType.NEOFORGE));
        }

        @Override
        public void send(@NotNull Packet<?> packet) {
            capturePacket(packet);
        }

        @Override
        public void send(@NotNull Packet<?> packet, PacketSendListener listener) {
            capturePacket(packet);
        }

        private void capturePacket(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket customPayloadPacket && customPayloadPacket.payload() instanceof DataDistributionTowerTargetsPayload payload) {
                this.targetPayloads.add(payload);
            } else if (packet instanceof ClientboundSystemChatPacket) {
                this.systemMessageCount++;
            }
        }

        private List<DataDistributionTowerTargetsPayload> targetPayloads() {
            return List.copyOf(this.targetPayloads);
        }

        private int systemMessageCount() {
            return this.systemMessageCount;
        }
    }
}
