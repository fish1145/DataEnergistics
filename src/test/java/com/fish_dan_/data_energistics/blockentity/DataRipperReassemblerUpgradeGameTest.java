package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.core.definitions.AEBlocks;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerUpgradeGameTest {

    private static final BlockPos REASSEMBLER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_CELL_POS = new BlockPos(2, 1, 1);

    private DataRipperReassemblerUpgradeGameTest() {}

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
