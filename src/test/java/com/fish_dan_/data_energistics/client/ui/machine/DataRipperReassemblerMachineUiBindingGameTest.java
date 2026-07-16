package com.fish_dan_.data_energistics.client.ui.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.menu.DataRipperReassemblerMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.network.clientbound.GuiDataSyncPacket;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.util.ConfigMenuInventory;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerMachineUiBindingGameTest {

    private static final BlockPos REASSEMBLER_POS = new BlockPos(2, 2, 2);
    private static final BlockPos ENERGY_CELL_POS = new BlockPos(3, 2, 2);
    private static final String ACTION_SET_AUTO_EXPORT = "set_auto_export";
    private static final String ACTION_SET_OUTPUT_SIDE = "set_output_side";
    private static final GenericStack FLUID_INPUT_A = new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L);
    private static final GenericStack FLUID_INPUT_B = new GenericStack(AEFluidKey.of(Fluids.LAVA), 2_000L);
    private static final GenericStack FLUID_OUTPUT_A = new GenericStack(AEFluidKey.of(Fluids.WATER), 3_000L);
    private static final GenericStack FLUID_OUTPUT_B = new GenericStack(AEFluidKey.of(Fluids.LAVA), 4_000L);
    private static final GenericStack KEY_INPUT = new GenericStack(DataKey.of(), 1_200L);
    private static final GenericStack KEY_OUTPUT = new GenericStack(DataFlowKey.of(), 2_400L);

    private DataRipperReassemblerMachineUiBindingGameTest() {}

    @TestHolder("data_reassembler_menu_binds_real_machine_ui_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 300)
    public static void menuBindsRealMachineUiState(GameTestHelper helper) {
        DataRipperReassemblerBlockEntity reassembler = placePoweredReassembler(helper);
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT,
                new ItemStack(Items.ENDER_PEARL));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        reassembler.isOnline(),
                        "The real Data Reassembler did not join its powered AE network"))
                .thenWaitUntil(() -> helper.assertTrue(
                        reassembler.getProgress() > 0,
                        "The real Ender recipe did not advance the machine progress"))
                .thenExecute(() -> assertMenuBinding(helper, reassembler))
                .thenSucceed();
    }

    @TestHolder("data_reassembler_menu_rejects_invalid_client_actions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void menuRejectsInvalidClientActions(GameTestHelper helper) {
        DataRipperReassemblerBlockEntity reassembler = placePoweredReassembler(helper);
        ServerPlayer player = createCapturingPlayer(helper);
        DataRipperReassemblerMenu menu = new DataRipperReassemblerMenu(1, player.getInventory(), reassembler);

        helper.assertFalse(reassembler.isAutoExportEnabled(), "Auto-export must start disabled");
        menu.receiveClientAction(ACTION_SET_AUTO_EXPORT, "true");
        helper.assertTrue(reassembler.isAutoExportEnabled(), "A valid true action must enable auto-export");
        helper.assertValueEqual(menu.getAutoExport(), YesNo.YES, "The menu must mirror enabled auto-export");

        menu.receiveClientAction(ACTION_SET_AUTO_EXPORT, null);
        helper.assertTrue(reassembler.isAutoExportEnabled(), "A missing auto-export payload must be rejected");
        helper.assertValueEqual(menu.getAutoExport(), YesNo.YES, "A rejected payload must not change the menu state");
        menu.receiveClientAction(ACTION_SET_AUTO_EXPORT, "null");
        helper.assertTrue(reassembler.isAutoExportEnabled(), "A JSON null auto-export payload must be rejected");
        helper.assertValueEqual(menu.getAutoExport(), YesNo.YES, "A rejected JSON null must not change the menu state");

        menu.receiveClientAction(ACTION_SET_AUTO_EXPORT, "false");
        helper.assertFalse(reassembler.isAutoExportEnabled(), "A valid false action must disable auto-export");
        helper.assertValueEqual(menu.getAutoExport(), YesNo.NO, "The menu must mirror disabled auto-export");

        menu.receiveClientAction(ACTION_SET_OUTPUT_SIDE, "\"north:false\"");
        helper.assertFalse(
                reassembler.getOutputSides().contains(Direction.NORTH),
                "A valid false action must disable the selected output side");
        helper.assertFalse(
                menu.getOutputSides().contains(Direction.NORTH),
                "The menu must mirror a disabled output side");
        menu.receiveClientAction(ACTION_SET_OUTPUT_SIDE, "\"north:true\"");
        helper.assertTrue(
                reassembler.getOutputSides().contains(Direction.NORTH),
                "A valid true action must enable the selected output side");
        helper.assertTrue(
                menu.getOutputSides().contains(Direction.NORTH),
                "The menu must mirror an enabled output side");

        assertRejectedOutputSideAction(helper, menu, reassembler, null, true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "null", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"\"", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"north\"", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\":true\"", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"north:\"", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"north:true:extra\"", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"missing:true\"", true);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"north:TRUE\"", false);
        assertRejectedOutputSideAction(helper, menu, reassembler, "\"north:yes\"", true);
        helper.succeed();
    }

    private static DataRipperReassemblerBlockEntity placePoweredReassembler(GameTestHelper helper) {
        helper.setBlock(REASSEMBLER_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.WEST));
        helper.setBlock(ENERGY_CELL_POS, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(REASSEMBLER_POS);
        if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
            return reassembler;
        }
        throw new GameTestAssertException("Placed Data Reassembler has no matching block entity");
    }

    private static void assertMenuBinding(
                                          GameTestHelper helper,
                                          DataRipperReassemblerBlockEntity reassembler) {
        int realProgress = reassembler.getProgress();
        int realMaxProgress = reassembler.getMaxProgress();
        helper.assertTrue(
                realProgress > 0 && realProgress < realMaxProgress,
                "The binding assertion must observe progress produced by a live recipe tick");
        Set<Direction> enabledSides = EnumSet.of(Direction.WEST, Direction.UP, Direction.NORTH);
        for (Direction side : Direction.values()) {
            reassembler.setOutputSideEnabled(side, enabledSides.contains(side));
        }
        setMenuStack(reassembler.getFluidMenuInventoryA(), FLUID_INPUT_A);
        setMenuStack(reassembler.getFluidMenuInventoryB(), FLUID_INPUT_B);
        setMenuStack(reassembler.getFluidOutputMenuInventoryA(), FLUID_OUTPUT_A);
        setMenuStack(reassembler.getFluidOutputMenuInventoryB(), FLUID_OUTPUT_B);
        setMenuStack(reassembler.getKeyMenuInventory(), KEY_INPUT);
        setMenuStack(reassembler.getKeyOutputMenuInventory(), KEY_OUTPUT);
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT,
                new ItemStack(Items.DIAMOND));
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT + 1,
                new ItemStack(Items.GOLD_INGOT));
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT + 2,
                new ItemStack(Items.EMERALD));
        reassembler.getConfigManager().putSetting(Settings.AUTO_EXPORT, YesNo.YES);

        ServerPlayer player = createCapturingPlayer(helper);
        CapturingPacketListener listener = (CapturingPacketListener) player.connection;
        DataRipperReassemblerMenu serverMenu = new DataRipperReassemblerMenu(1, player.getInventory(), reassembler);
        serverMenu.broadcastChanges();
        listener.clearGuiDataSyncPackets();
        serverMenu.sendAllDataToRemote();

        List<GuiDataSyncPacket> packets = listener.guiDataSyncPackets();
        helper.assertValueEqual(packets.size(), 1, "The real menu must send one full GUI synchronization packet");
        TrackingDataRipperReassemblerMenu menu = new TrackingDataRipperReassemblerMenu(
                1,
                player,
                reassembler);
        menu.receiveServerSyncData(RegistryFriendlyByteBuf.decorator(player.registryAccess(), ConnectionType.NEOFORGE)
                .apply(Unpooled.wrappedBuffer(packets.getFirst().syncData())));

        assertGuiSyncFieldIds(helper, menu);
        assertSynchronizedFields(helper, menu, reassembler, enabledSides, realProgress, realMaxProgress);
        assertCapacities(helper, menu);
        assertSlotBindings(helper, menu);
    }

    private static void assertGuiSyncFieldIds(GameTestHelper helper, TrackingDataRipperReassemblerMenu menu) {
        for (short fieldId = 840; fieldId <= 856; fieldId++) {
            helper.assertTrue(
                    menu.receivedField(fieldId),
                    "The full menu packet must update GuiSync field " + fieldId);
        }
    }

    private static void assertSynchronizedFields(
                                                 GameTestHelper helper,
                                                 DataRipperReassemblerMenu menu,
                                                 DataRipperReassemblerBlockEntity reassembler,
                                                 Set<Direction> enabledSides,
                                                 int realProgress,
                                                 int realMaxProgress) {
        helper.assertTrue(menu.online, "GuiSync 840 must read the real machine online state");
        assertFluidFields(helper, menu.fluidInputAId, menu.fluidInputAAmount, FLUID_INPUT_A, "input A");
        assertFluidFields(helper, menu.fluidInputBId, menu.fluidInputBAmount, FLUID_INPUT_B, "input B");
        assertFluidFields(helper, menu.fluidOutputAId, menu.fluidOutputAAmount, FLUID_OUTPUT_A, "output A");
        assertFluidFields(helper, menu.fluidOutputBId, menu.fluidOutputBAmount, FLUID_OUTPUT_B, "output B");
        helper.assertValueEqual(
                menu.keyInputLabel,
                KEY_INPUT.what().getDisplayName().getString(),
                "GuiSync 849 must read the real key input label");
        helper.assertValueEqual(
                menu.keyInputAmount,
                KEY_INPUT.amount(),
                "GuiSync 850 must read the real key input amount");
        helper.assertValueEqual(
                menu.keyOutputLabel,
                KEY_OUTPUT.what().getDisplayName().getString(),
                "GuiSync 851 must read the real key output label");
        helper.assertValueEqual(
                menu.keyOutputAmount,
                KEY_OUTPUT.amount(),
                "GuiSync 852 must read the real key output amount");
        helper.assertValueEqual(menu.progress, realProgress, "GuiSync 853 must read live recipe progress");
        helper.assertValueEqual(menu.maxProgress, realMaxProgress, "GuiSync 854 must read the live progress range");
        helper.assertValueEqual(menu.autoExport, YesNo.YES, "GuiSync 855 must read the real auto-export setting");
        helper.assertValueEqual(
                menu.outputSidesMask,
                encodeOutputSides(enabledSides),
                "GuiSync 856 must encode the real absolute output sides");
        helper.assertValueEqual(
                Set.copyOf(menu.getOutputSides()),
                Set.copyOf(reassembler.getOutputSides()),
                "The menu output-side view must decode the synchronized machine mask");
    }

    private static void assertFluidFields(
                                          GameTestHelper helper,
                                          String actualId,
                                          int actualAmount,
                                          GenericStack expected,
                                          String name) {
        AEFluidKey key = (AEFluidKey) expected.what();
        String expectedId = BuiltInRegistries.FLUID.getKey(key.getFluid()).toString();
        helper.assertValueEqual(actualId, expectedId, "The synchronized " + name + " fluid ID must match");
        helper.assertValueEqual(actualAmount, (int) expected.amount(), "The synchronized " + name + " amount must match");
    }

    private static void assertCapacities(GameTestHelper helper, DataRipperReassemblerMenu menu) {
        helper.assertValueEqual(
                menu.getFluidInputCapacity(),
                DataRipperReassemblerBlockEntity.FLUID_INPUT_CAPACITY,
                "The real fluid input capacity must reach the machine UI menu");
        helper.assertValueEqual(
                menu.getFluidOutputCapacity(),
                DataRipperReassemblerBlockEntity.FLUID_OUTPUT_CAPACITY,
                "The real fluid output capacity must reach the machine UI menu");
        helper.assertValueEqual(
                menu.getKeyInputCapacity(),
                DataRipperReassemblerBlockEntity.KEY_INPUT_CAPACITY,
                "The real key input capacity must reach the machine UI menu");
        helper.assertValueEqual(
                menu.getKeyOutputCapacity(),
                DataRipperReassemblerBlockEntity.KEY_OUTPUT_CAPACITY,
                "The real key output capacity must reach the machine UI menu");
    }

    private static void assertSlotBindings(GameTestHelper helper, DataRipperReassemblerMenu menu) {
        List<Slot> itemInputs = assertSlotCount(
                helper,
                menu,
                SlotSemantics.MACHINE_INPUT,
                DataRipperReassemblerBlockEntity.ITEM_INPUT_SLOT_COUNT,
                "item inputs");
        helper.assertTrue(
                itemInputs.getFirst().getItem().is(Items.ENDER_PEARL),
                "The first real item input slot must expose the active recipe input");

        assertWrappedSlot(
                helper,
                assertSingleSlot(helper, menu, SlotSemantics.STORAGE, "fluid input A"),
                FLUID_INPUT_A,
                "fluid input A");
        assertWrappedSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.FLUID_INPUT_B, "fluid input B"),
                FLUID_INPUT_B,
                "fluid input B");
        assertWrappedSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.KEY_INPUT, "key input"),
                KEY_INPUT,
                "key input");
        assertWrappedSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.FLUID_OUTPUT_A, "fluid output A"),
                FLUID_OUTPUT_A,
                "fluid output A");
        assertWrappedSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.FLUID_OUTPUT_B, "fluid output B"),
                FLUID_OUTPUT_B,
                "fluid output B");
        assertWrappedSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.KEY_OUTPUT, "key output"),
                KEY_OUTPUT,
                "key output");

        assertItemSlot(
                helper,
                assertSingleSlot(helper, menu, SlotSemantics.MACHINE_OUTPUT, "item output A"),
                Items.DIAMOND.getDefaultInstance(),
                "item output A");
        assertItemSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.ITEM_OUTPUT_B, "item output B"),
                Items.GOLD_INGOT.getDefaultInstance(),
                "item output B");
        assertItemSlot(
                helper,
                assertSingleSlot(helper, menu, DataRipperReassemblerMenu.ITEM_OUTPUT_C, "item output C"),
                Items.EMERALD.getDefaultInstance(),
                "item output C");
        assertSlotCount(helper, menu, SlotSemantics.PLAYER_INVENTORY, 27, "player inventory");
        assertSlotCount(helper, menu, SlotSemantics.PLAYER_HOTBAR, 9, "player hotbar");
        assertSlotCount(
                helper,
                menu,
                SlotSemantics.UPGRADE,
                DataRipperReassemblerBlockEntity.UPGRADE_SLOTS,
                "upgrade slots");
    }

    private static List<Slot> assertSlotCount(
                                              GameTestHelper helper,
                                              DataRipperReassemblerMenu menu,
                                              SlotSemantic semantic,
                                              int expectedCount,
                                              String name) {
        List<Slot> slots = menu.getSlots(semantic);
        helper.assertValueEqual(slots.size(), expectedCount, "The real menu must expose all " + name);
        return slots;
    }

    private static Slot assertSingleSlot(
                                         GameTestHelper helper,
                                         DataRipperReassemblerMenu menu,
                                         SlotSemantic semantic,
                                         String name) {
        return assertSlotCount(helper, menu, semantic, 1, name).getFirst();
    }

    private static void assertWrappedSlot(
                                          GameTestHelper helper,
                                          Slot slot,
                                          GenericStack expected,
                                          String name) {
        GenericStack actual = GenericStack.unwrapItemStack(slot.getItem());
        if (actual == null) {
            throw new GameTestAssertException("The real " + name + " slot did not expose a generic wrapper");
        }
        helper.assertValueEqual(actual.what(), expected.what(), "The real " + name + " key must match");
        helper.assertValueEqual(actual.amount(), expected.amount(), "The real " + name + " amount must match");
    }

    private static void assertItemSlot(
                                       GameTestHelper helper,
                                       Slot slot,
                                       ItemStack expected,
                                       String name) {
        helper.assertTrue(
                ItemStack.isSameItemSameComponents(slot.getItem(), expected),
                "The real " + name + " stack must match");
        helper.assertValueEqual(slot.getItem().getCount(), expected.getCount(), "The real " + name + " count must match");
    }

    private static void setMenuStack(ConfigMenuInventory inventory, GenericStack stack) {
        inventory.setItemDirect(0, GenericStack.wrapInItemStack(stack));
    }

    private static int encodeOutputSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private static void assertRejectedOutputSideAction(
                                                       GameTestHelper helper,
                                                       DataRipperReassemblerMenu menu,
                                                       DataRipperReassemblerBlockEntity reassembler,
                                                       String jsonPayload,
                                                       boolean northInitiallyEnabled) {
        reassembler.setOutputSideEnabled(Direction.NORTH, northInitiallyEnabled);
        menu.broadcastChanges();
        Set<Direction> expectedSides = Set.copyOf(reassembler.getOutputSides());
        int expectedMask = menu.outputSidesMask;

        menu.receiveClientAction(ACTION_SET_OUTPUT_SIDE, jsonPayload);

        helper.assertValueEqual(
                Set.copyOf(reassembler.getOutputSides()),
                expectedSides,
                "An invalid output-side payload must not change the machine state");
        helper.assertValueEqual(
                Set.copyOf(menu.getOutputSides()),
                expectedSides,
                "An invalid output-side payload must not change the menu state");
        helper.assertValueEqual(
                menu.outputSidesMask,
                expectedMask,
                "An invalid output-side payload must not change the synchronized mask");
    }

    private static ServerPlayer createCapturingPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "data-reassembler-ui-sync");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        new CapturingPacketListener(server, player, profile);
        return player;
    }

    private static final class TrackingDataRipperReassemblerMenu extends DataRipperReassemblerMenu {

        private final ShortSet updatedFields = new ShortOpenHashSet();

        private TrackingDataRipperReassemblerMenu(
                                                  int id,
                                                  ServerPlayer player,
                                                  DataRipperReassemblerBlockEntity host) {
            super(id, player.getInventory(), host);
        }

        @Override
        public void onServerDataSync(ShortSet updatedFields) {
            super.onServerDataSync(updatedFields);
            this.updatedFields.clear();
            this.updatedFields.addAll(updatedFields);
        }

        private boolean receivedField(short fieldId) {
            return this.updatedFields.contains(fieldId);
        }
    }

    private static final class CapturingPacketListener extends ServerGamePacketListenerImpl {

        private final List<GuiDataSyncPacket> guiDataSyncPackets = new ArrayList<>();

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
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket customPayloadPacket &&
                    customPayloadPacket.payload() instanceof GuiDataSyncPacket guiDataSyncPacket) {
                this.guiDataSyncPackets.add(guiDataSyncPacket);
            }
        }

        private void clearGuiDataSyncPackets() {
            this.guiDataSyncPackets.clear();
        }

        private List<GuiDataSyncPacket> guiDataSyncPackets() {
            return List.copyOf(this.guiDataSyncPackets);
        }
    }
}
