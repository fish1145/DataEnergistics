package com.fish_dan_.data_energistics.worldgen.meteorite;

import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.world.meteorite.DataMeteoriteSavedData;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.FalloutMode;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import lombok.Getter;

@Getter
public class MeteoriteStructurePiece extends StructurePiece {

    public static final StructurePieceType.ContextlessType TYPE = MeteoriteStructurePiece::new;
    private final PlacedMeteoriteSettings settings;

    protected MeteoriteStructurePiece(BlockPos center, float coreRadius, CraterType craterType, FalloutMode fallout, boolean pureCrater, boolean craterLake) {
        super(TYPE, 0, createBoundingBox(center));
        this.settings = new PlacedMeteoriteSettings(center, coreRadius, craterType, fallout, pureCrater, craterLake);
    }

    private static BoundingBox createBoundingBox(BlockPos origin) {
        int range = 96;
        ChunkPos chunkPos = new ChunkPos(origin);
        return new BoundingBox(
                chunkPos.getMinBlockX() - range,
                origin.getY(),
                chunkPos.getMinBlockZ() - range,
                chunkPos.getMaxBlockX() + range,
                origin.getY(),
                chunkPos.getMaxBlockZ() + range);
    }

    public MeteoriteStructurePiece(CompoundTag tag) {
        super(TYPE, tag);
        BlockPos center = BlockPos.of(tag.getLong("c"));
        float coreRadius = tag.getFloat("r");
        CraterType craterType = CraterType.values()[tag.getByte("t")];
        FalloutMode fallout = FalloutMode.values()[tag.getByte("f")];
        boolean pureCrater = tag.getBoolean("p");
        boolean craterLake = tag.getBoolean("l");
        this.settings = new PlacedMeteoriteSettings(center, coreRadius, craterType, fallout, pureCrater, craterLake);
    }

    public boolean isFinalized() {
        return this.settings.getCraterType() != null;
    }

    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putFloat("r", this.settings.getMeteoriteRadius());
        tag.putLong("c", this.settings.getPos().asLong());
        tag.putByte("t", (byte) this.settings.getCraterType().ordinal());
        tag.putByte("f", (byte) this.settings.getFallout().ordinal());
        tag.putBoolean("p", this.settings.isPureCrater());
        tag.putBoolean("l", this.settings.isCraterLake());
    }

    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator chunkGenerator, RandomSource rand, BoundingBox bounds, ChunkPos chunkPos, BlockPos blockPos) {
        MeteoritePlacer.place(level, this.settings, bounds, rand);
        BlockPos center = this.settings.getPos();
        if (bounds.isInside(center)) {
            ServerLevel serverLevel = level.getLevel();
            MinecraftServer server = serverLevel.getServer();
            Runnable recordCenter = () -> {
                if (serverLevel.getBlockState(center).is(DEBlocks.DATA_MYSTERIOUS_CUBE.get())) {
                    DataMeteoriteSavedData.get(serverLevel).add(center);
                }
            };
            if (server.isSameThread()) {
                recordCenter.run();
            } else {
                server.execute(recordCenter);
            }
        }
    }
}
