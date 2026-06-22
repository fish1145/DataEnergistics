package com.fish_dan_.data_energistics.integration.useless;

import com.fish_dan_.data_energistics.block.EnderCohesionMeteoriteBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.helpers.patternprovider.PatternContainer;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;

public final class SomeUselessThingsCompat {

    private SomeUselessThingsCompat() {}

    public static void afterPatternUpload(PatternContainer container) {
        if (container instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            furnace.markChanged();
            furnace.setChanged();
        }
    }

    public static void beforeSpecialMiningRemove(ServerLevel level, BlockPos pos, Player player, ItemStack tool, boolean silkTouch) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof EnderCohesionMeteoriteBlock meteorite) {
            meteorite.handleSpecialMining(level, player, pos, state, tool, silkTouch, EnderCohesionMeteoriteBlock.getFortuneLevel(level, tool));
        }
    }

    public static void removeBlockSafely(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
        }
        level.removeBlock(pos, false);
    }
}
