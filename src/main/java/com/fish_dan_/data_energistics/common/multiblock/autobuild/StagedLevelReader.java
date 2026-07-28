package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * Read-only level projection that exposes transaction-staged block states to ordinary block survival checks.
 */
final class StagedLevelReader implements LevelReader {

    private final Level base;
    private final Map<BlockPos, BlockState> stagedStates;

    StagedLevelReader(Level base, Map<BlockPos, BlockState> stagedStates) {
        this.base = base;
        this.stagedStates = stagedStates;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        BlockState stagedState = this.stagedStates.get(position);
        if (stagedState != null && !stagedState.equals(this.base.getBlockState(position))) {
            return null;
        }
        return this.base.getBlockEntity(position);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        BlockState stagedState = this.stagedStates.get(position);
        return stagedState == null ? this.base.getBlockState(position) : stagedState;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        BlockState stagedState = this.stagedStates.get(position);
        return stagedState == null ? this.base.getFluidState(position) : stagedState.getFluidState();
    }

    @Override
    public int getHeight() {
        return this.base.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.base.getMinBuildHeight();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return this.base.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.base.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos position, ColorResolver colorResolver) {
        return this.base.getBlockTint(position, colorResolver);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.base.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this;
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, AABB bounds) {
        return this.base.getEntityCollisions(entity, bounds);
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk) {
        return this.base.getChunk(chunkX, chunkZ, status, requireChunk);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.base.hasChunk(chunkX, chunkZ);
    }

    @Override
    public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        return this.base.getHeight(heightmapType, x, z);
    }

    @Override
    public int getSkyDarken() {
        return this.base.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.base.getBiomeManager();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return this.base.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public boolean isClientSide() {
        return this.base.isClientSide();
    }

    @Override
    public int getSeaLevel() {
        return this.base.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return this.base.dimensionType();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.base.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.base.enabledFeatures();
    }
}
