package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.cell.InfiniteDataCellHandler;
import com.fish_dan_.data_energistics.ae2.dataflow.DataFlowCellInventory;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.StorageCellTooltipComponent;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class EchoKeyGameTest {

    private EchoKeyGameTest() {}

    @TestHolder("echo_key_registered_codec_and_stream_round_trip")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void registeredCodecAndStreamRoundTrip(GameTestHelper helper) {
        helper.assertTrue(
                AEKeyTypes.get(EchoKey.ID) == EchoKeyType.TYPE,
                "The live AE2 key type registry must contain the Echo type");

        JsonElement encoded = AEKey.CODEC.encodeStart(JsonOps.INSTANCE, EchoKey.of()).getOrThrow();
        AEKey codecDecoded = AEKey.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(codecDecoded == EchoKey.of(), "The live AEKey codec must return the Echo singleton");
        helper.assertValueEqual(
                encoded.getAsJsonObject().get(AEKey.TYPE_FIELD).getAsString(),
                EchoKey.ID.toString(),
                "The live AEKey codec must identify Echo by its registered type id");
        helper.assertValueEqual(
                encoded.getAsJsonObject().size(),
                1,
                "Stateless Echo must add no codec payload beyond its type id");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.OTHER);
        try {
            AEKey.STREAM_CODEC.encode(buffer, EchoKey.of());
            AEKey streamDecoded = AEKey.STREAM_CODEC.decode(buffer);
            helper.assertTrue(streamDecoded == EchoKey.of(), "The live AEKey stream codec must return the Echo singleton");
            helper.assertValueEqual(buffer.readableBytes(), 0, "The Echo stream codec must consume its complete payload");
        } finally {
            buffer.release();
        }

        helper.succeed();
    }

    @TestHolder("infinite_data_cell_exposes_echo")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void infiniteDataCellExposesEcho(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.DATA_CELL_INFINITY.get());
        StorageCell cell = InfiniteDataCellHandler.INSTANCE.getCellInventory(stack, null);
        if (cell == null) {
            throw new GameTestAssertException("The infinite data cell must expose a real storage inventory");
        }

        KeyCounter available = new KeyCounter();
        cell.getAvailableStacks(available);
        helper.assertValueEqual(
                available.get(EchoKey.of()),
                Long.MAX_VALUE,
                "The infinite data cell must advertise Long.MAX_VALUE Echo");
        helper.assertValueEqual(
                available.get(DataFlowKey.of()),
                Long.MAX_VALUE,
                "The infinite data cell must retain infinite Data Flow");
        helper.assertValueEqual(
                available.get(DataKey.of()),
                Long.MAX_VALUE,
                "The infinite data cell must retain infinite Data");
        helper.assertValueEqual(available.size(), 3, "The infinite data cell must advertise exactly its three custom keys");

        IActionSource source = IActionSource.empty();
        helper.assertValueEqual(
                cell.insert(EchoKey.of(), 73L, Actionable.SIMULATE, source),
                73L,
                "The infinite data cell must accept all offered Echo");
        helper.assertValueEqual(
                cell.extract(EchoKey.of(), 91L, Actionable.MODULATE, source),
                91L,
                "The infinite data cell must provide all requested Echo");
        AEItemKey stone = AEItemKey.of(Items.STONE);
        helper.assertTrue(stone != null, "The test item key must exist");
        helper.assertValueEqual(
                cell.insert(stone, 1L, Actionable.SIMULATE, source),
                0L,
                "The infinite data cell must not expand to ordinary items");

        TooltipComponent tooltip = ModItems.DATA_CELL_INFINITY.get().getTooltipImage(stack).orElse(null);
        if (!(tooltip instanceof StorageCellTooltipComponent storageTooltip)) {
            throw new GameTestAssertException("The infinite data cell must expose the standard storage tooltip");
        }
        List<GenericStack> echoes = storageTooltip.content().stream()
                .filter(content -> EchoKey.of().equals(content.what()))
                .toList();
        if (echoes.size() != 1) {
            throw new GameTestAssertException("The infinite data cell tooltip must list Echo exactly once");
        }
        helper.assertValueEqual(
                echoes.getFirst().amount(),
                Long.MAX_VALUE,
                "The infinite data cell tooltip must display Long.MAX_VALUE Echo");

        helper.succeed();
    }

    @TestHolder("data_flow_cells_store_echo_with_shared_byte_accounting")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void dataFlowCellsStoreEchoWithSharedByteAccounting(GameTestHelper helper) {
        assertDataFlowCellStoresEcho(helper, ModItems.DATA_FLOW_CELL_1K.toStack(), "regular");
        assertDataFlowCellStoresEcho(helper, ModItems.PORTABLE_DATA_FLOW_CELL_1K.toStack(), "portable");
        helper.succeed();
    }

    @TestHolder("digital_storage_depot_stores_echo")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void digitalStorageDepotStoresEcho(GameTestHelper helper) {
        BlockPos depotPos = new BlockPos(2, 2, 2);
        DigitalStorageDepotBlockEntity depot = placeDepot(helper, depotPos);

        long inserted = depot.getExternalKeyInventory().insert(
                0,
                EchoKey.of(),
                37L,
                Actionable.MODULATE);
        helper.assertValueEqual(inserted, 37L, "The digital storage depot must accept the complete Echo amount");
        GenericStack stored = depot.getKeyStack(0);
        if (stored == null || stored.what() != EchoKey.of()) {
            throw new GameTestAssertException("The digital storage depot must preserve Echo as a native AE key");
        }
        helper.assertValueEqual(stored.amount(), 37L, "The digital storage depot must preserve the Echo amount");

        CompoundTag saved = new CompoundTag();
        depot.saveAdditional(saved, helper.getLevel().registryAccess());
        DigitalStorageDepotBlockEntity reloaded = placeDepot(helper, new BlockPos(3, 2, 2));
        reloaded.loadTag(saved, helper.getLevel().registryAccess());
        GenericStack restored = reloaded.getKeyStack(0);
        if (restored == null || restored.what() != EchoKey.of()) {
            throw new GameTestAssertException("Reloading the digital storage depot must restore Echo as a native AE key");
        }
        helper.assertValueEqual(restored.amount(), 37L, "Reloading the digital storage depot must restore the Echo amount");

        long extracted = reloaded.getExternalKeyInventory().extract(
                0,
                EchoKey.of(),
                37L,
                Actionable.MODULATE);
        helper.assertValueEqual(extracted, 37L, "The digital storage depot must return the complete Echo amount");
        helper.assertTrue(reloaded.getKeyStack(0) == null, "The Echo key slot must clear after complete extraction");
        helper.succeed();
    }

    private static DigitalStorageDepotBlockEntity placeDepot(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.DIGITAL_STORAGE_DEPOT.get());
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof DigitalStorageDepotBlockEntity depot) {
            return depot;
        }
        throw new GameTestAssertException("Placed digital storage depot has no matching block entity");
    }

    private static void assertDataFlowCellStoresEcho(GameTestHelper helper, ItemStack stack, String cellKind) {
        if (!(stack.getItem() instanceof IBasicCellItem basicCell)) {
            throw new GameTestAssertException("The " + cellKind + " Data Flow cell must implement IBasicCellItem");
        }

        StorageCell cell = StorageCells.getCellInventory(stack, null);
        if (!(cell instanceof DataFlowCellInventory inventory)) {
            throw new GameTestAssertException("The " + cellKind + " Data Flow cell must use the dual-resource inventory");
        }

        helper.assertValueEqual(inventory.getTotalItemTypes(), 2,
                "The " + cellKind + " Data Flow cell must accept Data Flow and Echo");
        helper.assertValueEqual(
                cell.insert(EchoKey.of(), 1L, Actionable.MODULATE, IActionSource.empty()),
                1L,
                "The " + cellKind + " Data Flow cell must store Echo");
        helper.assertValueEqual(
                cell.insert(DataFlowKey.of(), 7L, Actionable.MODULATE, IActionSource.empty()),
                7L,
                "The " + cellKind + " Data Flow cell must retain Data Flow alongside Echo");
        helper.assertValueEqual(
                inventory.getUsedBytes(),
                2L * basicCell.getBytesPerType(stack) + 1L,
                "Data Flow and Echo must share the same eight-units-per-byte payload accounting");

        StorageCell reloaded = StorageCells.getCellInventory(stack, null);
        if (reloaded == null) {
            throw new GameTestAssertException("The " + cellKind + " Data Flow cell must reload its storage inventory");
        }
        helper.assertValueEqual(reloaded.getAvailableStacks().get(EchoKey.of()), 1L,
                "Reloading the " + cellKind + " Data Flow cell must retain Echo");
        helper.assertValueEqual(reloaded.getAvailableStacks().get(DataFlowKey.of()), 7L,
                "Reloading the " + cellKind + " Data Flow cell must retain Data Flow");
    }
}
