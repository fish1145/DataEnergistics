package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerUpgradeGameTest {

    private static final BlockPos REASSEMBLER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_CELL_POS = new BlockPos(2, 1, 1);

    private DataRipperReassemblerUpgradeGameTest() {}

    @TestHolder("data_reassembler_upgrade_limits_and_formulas")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void upgradeLimitsAndFormulas(GameTestHelper helper) {
        DataRipperReassemblerBlockEntity reassembler = placeReassembler(helper);

        helper.assertValueEqual(reassembler.getUpgrades().size(), 5,
                "The Data Reassembler must expose five upgrade slots");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(DEItems.CARD_SABER_ENERGY.get(), DEBlocks.DATA_RIPPER_REASSEMBLER.get()),
                2,
                "The Data Reassembler must accept two Saber Energy Cards");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, DEBlocks.DATA_RIPPER_REASSEMBLER.get()),
                0,
                "The Data Reassembler must not accept Capacity Cards");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.SPEED_CARD, DEBlocks.DATA_RIPPER_REASSEMBLER.get()),
                5,
                "The Data Reassembler must accept five Speed Cards");

        helper.assertValueEqual(DataRipperReassemblerBlockEntity.computeParallel(0), 1,
                "Base parallel must be one");
        helper.assertValueEqual(DataRipperReassemblerBlockEntity.computeParallel(1), 8,
                "One Saber Energy Card must provide eight parallel operations");
        helper.assertValueEqual(DataRipperReassemblerBlockEntity.computeParallel(2), 16,
                "Two Saber Energy Cards must provide sixteen parallel operations");
        reassembler.getUpgrades().setItemDirect(0, DEItems.CARD_SABER_ENERGY.toStack());
        reassembler.getUpgrades().setItemDirect(1, DEItems.CARD_SABER_ENERGY.toStack());
        reassembler.getUpgrades().setItemDirect(2, AEItems.SPEED_CARD.stack());
        helper.assertValueEqual(reassembler.getParallel(), 16,
                "Installed Saber Energy Cards must update real machine parallelism");
        helper.assertValueEqual(reassembler.getItemSlotCapacity(), 256,
                "Every item slot must use the fixed maximum capacity");
        for (int slot = 0; slot < DataRipperReassemblerBlockEntity.STORAGE_SLOTS; slot++) {
            helper.assertValueEqual(reassembler.getStorageInventory().getSlotLimit(slot), 256,
                    "Item slot " + slot + " must use the fixed maximum capacity");
        }
        helper.assertValueEqual(reassembler.getFluidInputCapacity(), 256_000,
                "Every fluid input slot must use the fixed maximum capacity");
        helper.assertValueEqual(reassembler.getFluidOutputCapacity(), 256_000,
                "Every fluid output slot must use the fixed maximum capacity");
        helper.assertValueEqual(reassembler.getKeyInputCapacity(), 25_600_000L,
                "The AEKey input slot must use the fixed maximum capacity");
        helper.assertValueEqual(reassembler.getKeyOutputCapacity(), 25_600_000L,
                "The AEKey output slot must use the fixed maximum capacity");

        int insertedFluid = reassembler.getExternalFluidHandler().fill(
                AEFluidKey.of(Fluids.WATER).toStack(256_000),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(insertedFluid, 256_000,
                "A fluid slot must accept its full fixed capacity");
        helper.assertValueEqual(reassembler.getFluidInputA().getAmount(), 256_000,
                "A fluid slot must retain all 256,000 mB");

        ItemStack remainder = reassembler.getStorageInventory().insertItem(
                DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT,
                new ItemStack(Items.COBBLESTONE, 256),
                false);
        helper.assertTrue(remainder.isEmpty(), "An item slot must accept its full fixed capacity");
        helper.assertValueEqual(reassembler.getStorageInventory()
                .getStackInSlot(DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT).getCount(),
                256,
                "An item slot must retain all 256 items");
        long insertedKey = reassembler.getExternalKeyInventory().insert(
                DataRipperReassemblerBlockEntity.KEY_INPUT_SLOT,
                DataFlowKey.of(),
                25_600_000L,
                Actionable.MODULATE);
        helper.assertValueEqual(insertedKey, 25_600_000L,
                "The AEKey slot must accept its full fixed capacity");
        helper.assertValueEqual(reassembler.getExternalKeyInventory().insert(
                DataRipperReassemblerBlockEntity.KEY_INPUT_SLOT,
                DataFlowKey.of(),
                1L,
                Actionable.SIMULATE),
                0L,
                "The AEKey slot must reject amounts beyond its fixed capacity");
        helper.succeed();
    }

    @TestHolder("data_reassembler_energy_cards_apply_real_parallel_processing")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 260)
    public static void energyCardsApplyRealParallelProcessing(GameTestHelper helper) {
        DataRipperReassemblerBlockEntity reassembler = placeReassembler(helper);
        helper.setBlock(ENERGY_CELL_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        reassembler.getUpgrades().setItemDirect(0, DEItems.CARD_SABER_ENERGY.toStack());
        reassembler.getUpgrades().setItemDirect(1, DEItems.CARD_SABER_ENERGY.toStack());
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT,
                new ItemStack(Items.ENDER_PEARL, 16));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        reassembler.isOnline(),
                        "The upgraded Data Reassembler did not join its powered AE network"))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        reassembler.getFluidOutputA().getAmount(),
                        4_000,
                        "Two Saber Energy Cards must process sixteen complete Ender batches in one cycle"))
                .thenExecute(() -> helper.assertTrue(
                        reassembler.getStorageInventory()
                                .getStackInSlot(DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT)
                                .isEmpty(),
                        "Parallel processing must consume all sixteen input batches"))
                .thenSucceed();
    }

    private static DataRipperReassemblerBlockEntity placeReassembler(GameTestHelper helper) {
        helper.setBlock(REASSEMBLER_POS, DEBlocks.DATA_RIPPER_REASSEMBLER.get());
        BlockEntity blockEntity = helper.getBlockEntity(REASSEMBLER_POS);
        if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
            return reassembler;
        }
        throw new GameTestAssertException("Placed Data Reassembler has no matching block entity");
    }
}
