package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEItems;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataChargerCapabilityGameTest {

    private static final BlockPos REGULAR_CHARGER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos EXTENDED_CHARGER_POS = new BlockPos(3, 1, 1);

    private DataChargerCapabilityGameTest() {}

    @TestHolder("data_charger_exposes_filtered_item_and_input_only_energy_capabilities")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exposesFilteredCapabilities(GameTestHelper helper) {
        helper.setBlock(REGULAR_CHARGER_POS, ModBlocks.DATA_CHARGER.get().defaultBlockState());
        helper.setBlock(EXTENDED_CHARGER_POS, ModBlocks.EXTENDED_DATA_CHARGER.get().defaultBlockState());

        DataChargerBlockEntity regularCharger = requireCharger(helper, REGULAR_CHARGER_POS);
        DataChargerBlockEntity extendedCharger = requireCharger(helper, EXTENDED_CHARGER_POS);
        IItemHandler regularItems = requireItemCapability(helper, REGULAR_CHARGER_POS);
        IItemHandler extendedItems = requireItemCapability(helper, EXTENDED_CHARGER_POS);

        helper.assertValueEqual(regularItems.getSlots(), DataChargerBlockEntity.REGULAR_SLOT_COUNT,
                "The regular data charger must expose exactly one automation slot");
        helper.assertValueEqual(extendedItems.getSlots(), DataChargerBlockEntity.EXTENDED_SLOT_COUNT,
                "The extended data charger must expose all four automation slots");
        helper.assertTrue(extendedItems.insertItem(3, new ItemStack(Items.SNOWBALL), false).isEmpty(),
                "The fourth extended slot must accept supported charger recipe input");
        extendedCharger.getInternalInventory().clear();

        assertInsertionFiltering(helper, regularCharger, regularItems);
        assertRecipeExtraction(helper, regularCharger, regularItems);
        assertAePowerExtraction(helper, regularCharger, regularItems);
        assertDataFlowExtraction(helper, regularCharger, regularItems);
        assertCombinedExtraction(helper, regularCharger, regularItems);
        assertEnergyCapability(helper);
        helper.succeed();
    }

    private static void assertInsertionFiltering(GameTestHelper helper, DataChargerBlockEntity charger,
                                                 IItemHandler items) {
        ItemStack unsupported = new ItemStack(Items.COBBLESTONE);
        helper.assertValueEqual(items.insertItem(0, unsupported, false).getCount(), 1,
                "An unsupported item must be rejected by the item capability");
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.SNOWBALL), false).isEmpty(),
                "A data charger recipe input must be accepted by the item capability");
        charger.getInternalInventory().clear();
    }

    private static void assertRecipeExtraction(GameTestHelper helper, DataChargerBlockEntity charger,
                                               IItemHandler items) {
        charger.getInternalInventory().setItemDirect(0, new ItemStack(Items.SNOWBALL));
        assertBlockedExtraction(helper, items, "An unfinished Data Energistics charger recipe input");

        charger.getInternalInventory().setItemDirect(0, new ItemStack(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()));
        assertBlockedExtraction(helper, items, "An unfinished AE2 charger recipe input");

        charger.getInternalInventory().setItemDirect(0, new ItemStack(Items.ENDER_PEARL));
        assertExtracted(helper, items, Items.ENDER_PEARL, "A completed charger recipe result");
    }

    private static void assertAePowerExtraction(GameTestHelper helper, DataChargerBlockEntity charger,
                                                IItemHandler items) {
        ItemStack poweredTool = ModItems.DATA_CRYSTAL_PICKAXE.toStack();
        charger.getInternalInventory().setItemDirect(0, poweredTool);
        assertBlockedExtraction(helper, items, "An AE-powered item that is not fully charged");

        fillAePower(poweredTool);
        assertExtracted(helper, items, poweredTool.getItem(), "A fully charged AE-powered item");
    }

    private static void assertDataFlowExtraction(GameTestHelper helper, DataChargerBlockEntity charger,
                                                 IItemHandler items) {
        ItemStack dataFlowCell = ModItems.DATA_FLOW_CELL_1K.toStack();
        charger.getInternalInventory().setItemDirect(0, dataFlowCell);
        assertBlockedExtraction(helper, items, "A Data Flow cell that is not full");

        fillDataFlow(dataFlowCell);
        assertExtracted(helper, items, dataFlowCell.getItem(), "A full Data Flow cell");
    }

    private static void assertCombinedExtraction(GameTestHelper helper, DataChargerBlockEntity charger,
                                                 IItemHandler items) {
        ItemStack portableCell = ModItems.PORTABLE_DATA_FLOW_CELL_1K.toStack();
        charger.getInternalInventory().setItemDirect(0, portableCell);
        fillAePower(portableCell);
        assertBlockedExtraction(helper, items,
                "A combined AE and Data Flow item with only its AE power full");

        portableCell = ModItems.PORTABLE_DATA_FLOW_CELL_1K.toStack();
        charger.getInternalInventory().setItemDirect(0, portableCell);
        fillDataFlow(portableCell);
        assertBlockedExtraction(helper, items,
                "A combined AE and Data Flow item with only its Data Flow full");

        fillAePower(portableCell);
        assertExtracted(helper, items, portableCell.getItem(),
                "A combined AE and Data Flow item with both stores full");
    }

    private static void assertEnergyCapability(GameTestHelper helper) {
        BlockPos absolutePos = helper.absolutePos(REGULAR_CHARGER_POS);
        IEnergyStorage front = helper.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, absolutePos, Direction.NORTH);
        helper.assertTrue(front == null, "The front face must not expose an energy capability");

        IEnergyStorage back = helper.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, absolutePos, Direction.SOUTH);
        helper.assertTrue(back != null, "A configured non-front face must expose an energy capability");
        helper.assertTrue(back.canReceive(), "The data charger energy capability must accept energy");
        helper.assertTrue(!back.canExtract(), "The data charger energy capability must not output energy");
        helper.assertTrue(back.receiveEnergy(100, false) > 0,
                "The data charger energy capability must transfer received energy into its AE buffer");
    }

    private static DataChargerBlockEntity requireCharger(GameTestHelper helper, BlockPos position) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof DataChargerBlockEntity charger) {
            return charger;
        }
        throw new GameTestAssertException("Placed data charger has no matching block entity");
    }

    private static IItemHandler requireItemCapability(GameTestHelper helper, BlockPos position) {
        IItemHandler items = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(position), Direction.UP);
        if (items != null) {
            return items;
        }
        throw new GameTestAssertException("Placed data charger has no item capability");
    }

    private static void assertBlockedExtraction(GameTestHelper helper, IItemHandler items, String subject) {
        helper.assertTrue(items.extractItem(0, 1, false).isEmpty(),
                subject + " must not be extractable through the item capability");
    }

    private static void assertExtracted(GameTestHelper helper, IItemHandler items, Item expected,
                                        String subject) {
        ItemStack extracted = items.extractItem(0, 1, false);
        helper.assertTrue(extracted.is(expected),
                subject + " must be extractable through the item capability");
    }

    private static void fillAePower(ItemStack stack) {
        if (!(stack.getItem() instanceof IAEItemPowerStorage powerStorage)) {
            throw new GameTestAssertException("Test item does not support AE power");
        }
        powerStorage.injectAEPower(stack, powerStorage.getAEMaxPower(stack), Actionable.MODULATE);
    }

    private static void fillDataFlow(ItemStack stack) {
        var storage = StorageCells.getCellInventory(stack, null);
        if (storage == null) {
            throw new GameTestAssertException("Test item does not support Data Flow storage");
        }
        long capacity = storage.insert(DataFlowKey.of(), Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
        long inserted = storage.insert(DataFlowKey.of(), capacity, Actionable.MODULATE, IActionSource.empty());
        if (capacity <= 0L || inserted != capacity) {
            throw new GameTestAssertException("Test item could not be filled with Data Flow");
        }
    }
}
