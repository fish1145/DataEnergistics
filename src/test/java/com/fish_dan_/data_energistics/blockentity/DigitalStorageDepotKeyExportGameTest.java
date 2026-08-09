package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DigitalStorageDepotKeyExportGameTest {

    private static final BlockPos DEPOT_POS = new BlockPos(2, 2, 2);
    private static final BlockPos EAST_POS = DEPOT_POS.east();

    private DigitalStorageDepotKeyExportGameTest() {}

    @TestHolder("digital_storage_depot_exports_keys_to_generic_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exportsKeysToGenericInventory(GameTestHelper helper) {
        helper.setBlock(DEPOT_POS, ModBlocks.DIGITAL_STORAGE_DEPOT.get());
        helper.setBlock(EAST_POS, ModBlocks.DATA_MIMETIC_FIELD.get());

        DigitalStorageDepotBlockEntity depot = requireDepot(helper);
        DataMimeticFieldBlockEntity mimeticField = requireMimeticField(helper);
        IItemHandler itemHandler = requireDepotItemHandler(helper);

        helper.assertValueEqual(itemHandler.getSlots(), DigitalStorageDepotBlockEntity.STORAGE_SLOTS,
                "The item capability must expose only regular item slots");
        ItemStack wrappedKey = GenericStack.wrapInItemStack(DataFlowKey.of(), 100L);
        ItemStack rejected = itemHandler.insertItem(0, wrappedKey, false);
        helper.assertValueEqual(rejected.getCount(), wrappedKey.getCount(),
                "The item capability must reject wrapped non-item keys");
        helper.assertTrue(itemHandler.insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty(),
                "The item capability must continue accepting regular items");
        helper.assertTrue(itemHandler.extractItem(0, 1, false).is(Items.COBBLESTONE),
                "Regular items must remain extractable through the item capability");

        long inserted = depot.getExternalKeyInventory().insert(0, DataFlowKey.of(), 100L, Actionable.MODULATE);
        helper.assertValueEqual(inserted, 100L, "The depot key slot must accept the test data flow");
        configureKeyOutput(depot, Direction.EAST);
        depot.setAutoExportMode(DataExtractorAutoExportMode.CONTAINER);

        depot.serverTick();

        GenericStack received = mimeticField.getKeyInputStack();
        helper.assertTrue(received != null && DataFlowKey.of().equals(received.what()),
                "The adjacent generic key slot must receive Data Flow directly");
        helper.assertValueEqual(received.amount(), 100L,
                "The adjacent generic key slot must receive the complete amount");
        helper.assertTrue(depot.getKeyStack(0) == null,
                "The source key slot must clear after the direct key transfer");
        helper.succeed();
    }

    @TestHolder("digital_storage_depot_does_not_export_keys_as_items")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void doesNotExportKeysAsItems(GameTestHelper helper) {
        helper.setBlock(DEPOT_POS, ModBlocks.DIGITAL_STORAGE_DEPOT.get());
        helper.setBlock(EAST_POS, Blocks.CHEST);

        DigitalStorageDepotBlockEntity depot = requireDepot(helper);
        long inserted = depot.getExternalKeyInventory().insert(0, DataFlowKey.of(), 100L, Actionable.MODULATE);
        helper.assertValueEqual(inserted, 100L, "The depot key slot must accept the test data flow");
        configureKeyOutput(depot, Direction.EAST);
        depot.setAutoExportMode(DataExtractorAutoExportMode.CONTAINER);

        depot.serverTick();

        GenericStack retained = depot.getKeyStack(0);
        helper.assertTrue(retained != null && DataFlowKey.of().equals(retained.what()),
                "A key without a compatible generic inventory must remain in the depot");
        helper.assertValueEqual(retained.amount(), 100L,
                "A regular item container must not consume any non-item key amount");

        IItemHandler chest = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(EAST_POS),
                Direction.WEST);
        helper.assertTrue(chest != null, "The adjacent chest must expose an item capability");
        for (int slot = 0; slot < chest.getSlots(); slot++) {
            helper.assertTrue(chest.getStackInSlot(slot).isEmpty(),
                    "The adjacent chest must not receive a wrapped generic stack item");
        }
        helper.succeed();
    }

    private static void configureKeyOutput(DigitalStorageDepotBlockEntity depot, Direction outputSide) {
        for (Direction direction : Direction.values()) {
            depot.setOutputSideEnabled(DigitalStorageDepotOutputType.KEYS, direction, direction == outputSide);
        }
    }

    private static DigitalStorageDepotBlockEntity requireDepot(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(DEPOT_POS);
        if (blockEntity instanceof DigitalStorageDepotBlockEntity depot) {
            return depot;
        }
        throw new GameTestAssertException("Placed digital storage depot has no matching block entity");
    }

    private static DataMimeticFieldBlockEntity requireMimeticField(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(EAST_POS);
        if (blockEntity instanceof DataMimeticFieldBlockEntity mimeticField) {
            return mimeticField;
        }
        throw new GameTestAssertException("Placed data mimetic field has no matching block entity");
    }

    private static IItemHandler requireDepotItemHandler(GameTestHelper helper) {
        IItemHandler itemHandler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(DEPOT_POS),
                Direction.UP);
        if (itemHandler != null) {
            return itemHandler;
        }
        throw new GameTestAssertException("Placed digital storage depot has no item capability");
    }
}
