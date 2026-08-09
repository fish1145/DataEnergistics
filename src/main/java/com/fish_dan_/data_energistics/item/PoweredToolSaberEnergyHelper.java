package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class PoweredToolSaberEnergyHelper {

    public static final long DATA_FLOW_COST = 20L;

    private PoweredToolSaberEnergyHelper() {}

    public static boolean hasSaberEnergy(ItemStack stack, PoweredEnergyItem item) {
        return item.getSaberEnergyCardCount(stack) > 0;
    }

    public static boolean consumeDataFlow(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return false;
        }
        long extracted = cellInventory.extract(DataFlowKey.of(), DATA_FLOW_COST, Actionable.MODULATE, IActionSource.empty());
        return extracted >= DATA_FLOW_COST;
    }

    public static Set<BlockPos> collectTree(Level level, BlockPos startPos, int maxBlocks) {
        Set<BlockPos> result = new HashSet<>();
        if (!isTreeBlock(level.getBlockState(startPos))) {
            return result;
        }
        floodFill(level, startPos, maxBlocks, result, PoweredToolSaberEnergyHelper::isTreeBlock);
        return result;
    }

    public static Set<BlockPos> collectOreVein(Level level, BlockPos startPos, int maxBlocks) {
        Set<BlockPos> result = new HashSet<>();
        BlockState startState = level.getBlockState(startPos);
        if (!isOreBlock(startState)) {
            return result;
        }
        Block block = startState.getBlock();
        floodFill(level, startPos, maxBlocks, result, state -> state.getBlock() == block);
        return result;
    }

    private static void floodFill(Level level, BlockPos startPos, int maxBlocks, Set<BlockPos> result,
                                  Predicate<BlockState> predicate) {
        Set<BlockPos> frontier = new HashSet<>();
        frontier.add(startPos.immutable());

        while (!frontier.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = frontier.iterator().next();
            frontier.remove(current);
            if (result.contains(current)) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            if (!predicate.test(state)) {
                continue;
            }

            result.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!result.contains(next) && result.size() + frontier.size() < maxBlocks) {
                            frontier.add(next.immutable());
                        }
                    }
                }
            }
        }
    }

    private static boolean isTreeBlock(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    public static boolean isOreBlock(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES);
    }
}
