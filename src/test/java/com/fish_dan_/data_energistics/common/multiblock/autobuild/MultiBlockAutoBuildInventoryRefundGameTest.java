package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MultiBlockAutoBuildInventoryRefundGameTest {

    private MultiBlockAutoBuildInventoryRefundGameTest() {}

    @TestHolder("multi_block_auto_build_refund_preserves_external_inventory_changes")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void refundPreservesExternalInventoryChanges(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos firstPosition = helper.absolutePos(new BlockPos(4, 2, 4));
        ItemStack ironBlock = new ItemStack(Blocks.IRON_BLOCK);
        player.getInventory().setItem(0, ironBlock.copyWithCount(2));

        MultiBlockAutoBuildImpl.InventoryTransaction transaction = new MultiBlockAutoBuildImpl.InventoryTransaction(
                "refund-test",
                player.getInventory(),
                List.of(
                        new MultiBlockAutoBuildImpl.MaterialReservation(firstPosition, 0, ironBlock),
                        new MultiBlockAutoBuildImpl.MaterialReservation(firstPosition.east(), 0, ironBlock)),
                false);

        helper.assertTrue(transaction.commit(), "Material reservations should commit against the selected source slot");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "Both reservations should consume the selected source stack");

        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD));

        MultiBlockAutoBuildImpl.RefundOutcome refundOutcome = transaction.rollback(player);

        helper.assertTrue(refundOutcome.completed(), "Every deducted reservation should be returned exactly once");
        helper.assertValueEqual(player.getInventory().getItem(0).getItem(), Items.DIAMOND,
                "Refund must not overwrite an externally replaced source slot");
        helper.assertValueEqual(player.getInventory().getItem(1).getItem(), Items.EMERALD,
                "Refund must preserve unrelated inventory changes made during the transaction");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 2,
                "Refund must return the exact aggregate deducted from one source slot");
        helper.assertTrue(transaction.rollback(player).completed(), "A closed refund ledger must not deliver material twice");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 2,
                "Repeating rollback after settlement must not duplicate the refund");

        verifyFullInventoryRefundDropsAtPlayer(helper, player, firstPosition);
        helper.succeed();
    }

    private static void verifyFullInventoryRefundDropsAtPlayer(GameTestHelper helper, Player player, BlockPos firstPosition) {
        fillInventory(player, Items.DIRT);
        ItemStack goldBlock = new ItemStack(Blocks.GOLD_BLOCK);
        player.getInventory().setItem(2, goldBlock.copyWithCount(2));

        MultiBlockAutoBuildImpl.InventoryTransaction transaction = new MultiBlockAutoBuildImpl.InventoryTransaction(
                "refund-drop-test",
                player.getInventory(),
                List.of(
                        new MultiBlockAutoBuildImpl.MaterialReservation(firstPosition.south(), 2, goldBlock),
                        new MultiBlockAutoBuildImpl.MaterialReservation(firstPosition.south().east(), 2, goldBlock)),
                false);

        helper.assertTrue(transaction.commit(), "The full-inventory refund setup should reserve both gold blocks");
        player.getInventory().setItem(2, new ItemStack(Items.DIAMOND));
        player.getInventory().setItem(3, new ItemStack(Items.EMERALD));

        MultiBlockAutoBuildImpl.RefundOutcome refundOutcome = transaction.rollback(player);

        helper.assertTrue(refundOutcome.completed(), "The full-inventory refund should complete through a world item entity");
        helper.assertValueEqual(player.getInventory().getItem(2).getItem(), Items.DIAMOND,
                "A full source slot replaced during the transaction must remain intact");
        helper.assertValueEqual(player.getInventory().getItem(3).getItem(), Items.EMERALD,
                "An unrelated full-inventory slot must remain intact");
        helper.assertValueEqual(countItem(player, Blocks.GOLD_BLOCK.asItem()), 0,
                "A full inventory must not receive the refund by overwriting a slot");
        helper.assertValueEqual(countDroppedItem(helper, player, Blocks.GOLD_BLOCK.asItem()), 2,
                "The exact full-inventory refund must appear once at the player as item entities");
        helper.assertTrue(transaction.rollback(player).completed(),
                "Repeating a completed full-inventory rollback must not create more item entities");
        helper.assertValueEqual(countDroppedItem(helper, player, Blocks.GOLD_BLOCK.asItem()), 2,
                "A repeated rollback must not duplicate the full-inventory refund drop");
    }

    private static void fillInventory(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(item));
        }
    }

    private static int countItem(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countDroppedItem(GameTestHelper helper, Player player, Item item) {
        AABB searchArea = AABB.ofSize(player.position(), 4.0D, 4.0D, 4.0D);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, searchArea).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
