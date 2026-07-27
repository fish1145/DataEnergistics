package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModBlocks;

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

import java.util.ArrayList;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerDropsGameTest {

    private static final BlockPos REASSEMBLER_POS = new BlockPos(2, 2, 2);

    private DataRipperReassemblerDropsGameTest() {}

    @TestHolder("data_reassembler_additional_drops_do_not_duplicate_internal_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void additionalDropsDoNotDuplicateInternalInventory(GameTestHelper helper) {
        helper.setBlock(REASSEMBLER_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get());
        BlockEntity blockEntity = helper.getBlockEntity(REASSEMBLER_POS);
        if (!(blockEntity instanceof DataRipperReassemblerBlockEntity reassembler)) {
            throw new GameTestAssertException("Placed Data Reassembler has no matching block entity");
        }

        reassembler.getStorageInventory().setItemDirect(0, new ItemStack(Items.COBBLESTONE));
        var drops = new ArrayList<ItemStack>();
        reassembler.addAdditionalDrops(helper.getLevel(), helper.absolutePos(REASSEMBLER_POS), drops);

        int cobblestoneCount = drops.stream()
                .filter(stack -> stack.is(Items.COBBLESTONE))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertValueEqual(
                cobblestoneCount,
                1,
                "One stored cobblestone must be returned exactly once during wrench disassembly");
        helper.succeed();
    }
}
