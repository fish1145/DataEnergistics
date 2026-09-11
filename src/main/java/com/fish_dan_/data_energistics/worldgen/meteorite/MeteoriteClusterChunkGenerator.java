package com.fish_dan_.data_energistics.worldgen.meteorite;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import java.util.concurrent.CompletableFuture;

public final class MeteoriteClusterChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<MeteoriteClusterChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(MeteoriteClusterChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(MeteoriteClusterChunkGenerator::generatorSettings)
    ).apply(instance, MeteoriteClusterChunkGenerator::new));

    private static final int TOTAL_WEIGHT = 10;

    public MeteoriteClusterChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends NoiseBasedChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager, ChunkAccess chunk) {
        return super.fillFromNoise(blender, randomState, structureManager, chunk)
                .thenApply(generated -> {
                    replaceSolidBlocks(generated);
                    return generated;
                });
    }

    private static void replaceSolidBlocks(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPosCursor cursor = new BlockPosCursor(chunkPos, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        while (cursor.next()) {
            BlockState state = chunk.getBlockState(cursor.pos());
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            chunk.setBlockState(cursor.pos(), weightedState(cursor.x, cursor.y, cursor.z), false);
        }
    }

    private static BlockState weightedState(int x, int y, int z) {
        long hash = mix(x, y, z);
        int weight = (int) Math.floorMod(hash, TOTAL_WEIGHT);
        if (weight < 4) {
            return DEBlocks.ENDER_COHESION_METEORITE_0.get().defaultBlockState();
        }
        if (weight < 7) {
            return DEBlocks.ENDER_COHESION_METEORITE_0.get().defaultBlockState();
        }
        if (weight < 9) {
            return DEBlocks.ENDER_COHESION_METEORITE_1.get().defaultBlockState();
        }
        return DEBlocks.ENDER_COHESION_METEORITE_2.get().defaultBlockState();
    }

    private static long mix(int x, int y, int z) {
        long value = x * 341873128712L + y * 132897987541L + z * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value;
    }

    private static final class BlockPosCursor {
        private final ChunkPos chunkPos;
        private final int maxY;
        private int x;
        private int y;
        private int z;

        private BlockPosCursor(ChunkPos chunkPos, int minY, int maxY) {
            this.chunkPos = chunkPos;
            this.maxY = maxY;
            this.x = chunkPos.getMinBlockX();
            this.y = minY;
            this.z = chunkPos.getMinBlockZ();
        }

        private boolean next() {
            if (y >= maxY) return false;
            z++;
            if (z >= chunkPos.getMaxBlockZ() + 1) {
                z = chunkPos.getMinBlockZ();
                x++;
                if (x >= chunkPos.getMaxBlockX() + 1) {
                    x = chunkPos.getMinBlockX();
                    y++;
                }
            }
            return y < maxY;
        }

        private BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }
}
