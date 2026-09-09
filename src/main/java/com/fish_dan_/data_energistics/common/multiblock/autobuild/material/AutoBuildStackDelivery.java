package com.fish_dan_.data_energistics.common.multiblock.autobuild.material;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Final tangible delivery for a returned container or AE2 wrapped fluid amount. */
final class AutoBuildStackDelivery {

    private AutoBuildStackDelivery() {}

    static void give(Player player, ItemStack stack) {
        player.getInventory().add(stack);
        if (stack.isEmpty()) return;
        ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy());
        if (!player.level().addFreshEntity(entity)) throw new IllegalStateException("World rejected auto-build refund");
    }
}
