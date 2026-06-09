package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.block.EnderCohesionMeteoriteBlock;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.helpers.patternprovider.PatternContainer;

public final class SomeUselessThingsCompat {

    private static final String ADVANCED_ALLOY_FURNACE_BLOCK_ENTITY_CLASS = "com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity";

    private SomeUselessThingsCompat() {}

    public static void afterPatternUpload(PatternContainer container) {
        if (!isAdvancedAlloyFurnace(container)) {
            return;
        }

        ReflectionAccess.invokeNoArgBestEffort(container, "updatePatterns");
        ReflectionAccess.invokeNoArgBestEffort(container, "markChanged");
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

    private static boolean isAdvancedAlloyFurnace(PatternContainer container) {
        return container != null && ADVANCED_ALLOY_FURNACE_BLOCK_ENTITY_CLASS.equals(container.getClass().getName());
    }
}
