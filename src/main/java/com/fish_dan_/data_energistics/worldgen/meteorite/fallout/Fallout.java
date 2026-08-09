package com.fish_dan_.data_energistics.worldgen.meteorite.fallout;

import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.worldgen.meteorite.MeteoriteBlockPutter;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Fallout {

    private static final float CRACKED_FALLOUT_CHANCE = 0.05F;
    private static final float FRACTURED_FALLOUT_CHANCE = 0.03F;
    private static final float SHATTERED_FALLOUT_CHANCE = 0.01F;
    private static final float END_STONE_FALLOUT_CHANCE = 0.17F;
    private final MeteoriteBlockPutter putter;
    private final BlockState skyStone;
    private final BlockState crackedMeteorite;
    private final BlockState fracturedMeteorite;
    private final BlockState shatteredMeteorite;
    protected final RandomSource random;

    public Fallout(MeteoriteBlockPutter putter, BlockState skyStone, RandomSource random) {
        this.putter = putter;
        this.skyStone = skyStone;
        this.random = random;
        this.crackedMeteorite = DEBlocks.ENDER_COHESION_METEORITE_0.get().defaultBlockState();
        this.fracturedMeteorite = DEBlocks.ENDER_COHESION_METEORITE_1.get().defaultBlockState();
        this.shatteredMeteorite = DEBlocks.ENDER_COHESION_METEORITE_2.get().defaultBlockState();
    }

    public int adjustCrater() {
        return 0;
    }

    public void getRandomFall(LevelAccessor level, BlockPos pos) {
        float a = this.random.nextFloat();
        BlockState scatteredBlock = this.pickScatteredFallBlock(a);
        if (scatteredBlock != null) {
            this.putter.put(level, pos, scatteredBlock);
            return;
        }

        if (a > 0.9F) {
            this.putter.put(level, pos, Blocks.STONE.defaultBlockState());
        } else if (a > 0.8F) {
            this.putter.put(level, pos, Blocks.COBBLESTONE.defaultBlockState());
        } else if (a > 0.7F) {
            this.putter.put(level, pos, Blocks.DIRT.defaultBlockState());
        } else {
            this.putter.put(level, pos, Blocks.GRAVEL.defaultBlockState());
        }
    }

    protected BlockState pickScatteredFallBlock(float roll) {
        if (roll < SHATTERED_FALLOUT_CHANCE) {
            return this.shatteredMeteorite;
        }
        if (roll < SHATTERED_FALLOUT_CHANCE + FRACTURED_FALLOUT_CHANCE) {
            return this.fracturedMeteorite;
        }
        if (roll < SHATTERED_FALLOUT_CHANCE + FRACTURED_FALLOUT_CHANCE + CRACKED_FALLOUT_CHANCE) {
            return this.crackedMeteorite;
        }
        if (roll < SHATTERED_FALLOUT_CHANCE + FRACTURED_FALLOUT_CHANCE + CRACKED_FALLOUT_CHANCE + END_STONE_FALLOUT_CHANCE) {
            return Blocks.END_STONE.defaultBlockState();
        }
        return null;
    }

    public void getRandomInset(LevelAccessor level, BlockPos pos) {
        float a = this.random.nextFloat();
        if (a > 0.9F) {
            this.putter.put(level, pos, Blocks.COBBLESTONE.defaultBlockState());
        } else if (a > 0.8F) {
            this.putter.put(level, pos, Blocks.STONE.defaultBlockState());
        } else if (a > 0.7F) {
            this.putter.put(level, pos, Blocks.GRASS_BLOCK.defaultBlockState());
        } else if (a > 0.6F) {
            this.putter.put(level, pos, this.skyStone);
        } else if (a > 0.5F) {
            this.putter.put(level, pos, Blocks.GRAVEL.defaultBlockState());
        } else {
            this.putter.put(level, pos, Blocks.AIR.defaultBlockState());
        }
    }
}
