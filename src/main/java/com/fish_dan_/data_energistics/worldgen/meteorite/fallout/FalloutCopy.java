package com.fish_dan_.data_energistics.worldgen.meteorite.fallout;

import com.fish_dan_.data_energistics.worldgen.meteorite.MeteoriteBlockPutter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags.Biomes;

public class FalloutCopy extends Fallout {

    private final BlockState block;
    private final MeteoriteBlockPutter putter;

    public FalloutCopy(LevelAccessor level, BlockPos pos, MeteoriteBlockPutter putter, BlockState skyStone, RandomSource random) {
        super(putter, skyStone, random);
        this.putter = putter;
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            this.block = Blocks.TERRACOTTA.defaultBlockState();
        } else if (biome.is(Biomes.IS_SNOWY)) {
            this.block = Blocks.SNOW_BLOCK.defaultBlockState();
        } else if (!biome.is(BiomeTags.IS_BEACH) && !biome.is(Biomes.IS_SANDY)) {
            if (!biome.is(Biomes.IS_PLAINS) && !biome.is(BiomeTags.IS_FOREST)) {
                this.block = Blocks.COBBLESTONE.defaultBlockState();
            } else {
                this.block = Blocks.DIRT.defaultBlockState();
            }
        } else {
            this.block = Blocks.SAND.defaultBlockState();
        }
    }

    public void getRandomFall(LevelAccessor level, BlockPos pos) {
        float a = this.random.nextFloat();
        BlockState scatteredBlock = this.pickScatteredFallBlock(a);
        if (scatteredBlock != null) {
            this.putter.put(level, pos, scatteredBlock);
        } else if (a > 0.9F) {
            this.putter.put(level, pos, this.block);
        } else {
            this.getOther(level, pos, a);
        }
    }

    public void getOther(LevelAccessor level, BlockPos pos, float a) {}

    public void getRandomInset(LevelAccessor level, BlockPos pos) {
        float a = this.random.nextFloat();
        if (a > 0.9F) {
            this.putter.put(level, pos, this.block);
        } else if (a > 0.8F) {
            this.putter.put(level, pos, Blocks.AIR.defaultBlockState());
        } else {
            this.getOther(level, pos, a - 0.1F);
        }
    }
}
