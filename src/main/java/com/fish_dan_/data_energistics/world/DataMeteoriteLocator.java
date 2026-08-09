package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;

import appeng.core.definitions.AEBlocks;

import java.util.Optional;

public final class DataMeteoriteLocator {

    private static final int SEARCH_RADIUS_CHUNKS = 174;
    private static final int LOADED_SCAN_RADIUS_CHUNKS = 32;
    private static final TagKey<Structure> METEORITE_STRUCTURES = TagKey.create(
            Registries.STRUCTURE,
            Data_Energistics.id("data_meteorite_compass_targets"));

    private DataMeteoriteLocator() {}

    public static Optional<BlockPos> findOrDiscoverClosest(ServerLevel level, ChunkPos chunkPos, int y) {
        DataMeteoriteSavedData meteorites = DataMeteoriteSavedData.get(level);
        Optional<BlockPos> closest = Optional.ofNullable(
                meteorites.findClosest(chunkPos, pos -> isTargetStillPresent(level, pos)));
        if (closest.isPresent()) {
            return closest;
        }

        closest = findClosestLoadedMeteorite(level, chunkPos);
        if (closest.isPresent()) {
            closest.ifPresent(meteorites::add);
            return closest;
        }

        BlockPos origin = chunkPos.getMiddleBlockPosition(y);
        closest = Optional.ofNullable(level.findNearestMapStructure(
                METEORITE_STRUCTURES,
                origin,
                SEARCH_RADIUS_CHUNKS,
                false)).filter(pos -> isTargetStillPresent(level, pos));
        closest.ifPresent(meteorites::add);
        return closest;
    }

    private static Optional<BlockPos> findClosestLoadedMeteorite(ServerLevel level, ChunkPos originChunkPos) {
        Block targetBlock = AEBlocks.MYSTERIOUS_CUBE.block();
        BlockPos origin = originChunkPos.getMiddleBlockPosition(0);
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int offset = 0; offset <= LOADED_SCAN_RADIUS_CHUNKS; offset++) {
            int minX = originChunkPos.x - offset;
            int minZ = originChunkPos.z - offset;
            int maxX = originChunkPos.x + offset;
            int maxZ = originChunkPos.z + offset;

            for (int z = minZ; z <= maxZ; z++) {
                closest = findClosestLoadedMeteoriteInChunk(level, minX, z, targetBlock, origin, closest, closestDistance);
                if (closest != null) {
                    closestDistance = origin.distSqr(closest.atY(0));
                }
                closest = findClosestLoadedMeteoriteInChunk(level, maxX, z, targetBlock, origin, closest, closestDistance);
                if (closest != null) {
                    closestDistance = origin.distSqr(closest.atY(0));
                }
            }

            for (int x = minX + 1; x < maxX; x++) {
                closest = findClosestLoadedMeteoriteInChunk(level, x, minZ, targetBlock, origin, closest, closestDistance);
                if (closest != null) {
                    closestDistance = origin.distSqr(closest.atY(0));
                }
                closest = findClosestLoadedMeteoriteInChunk(level, x, maxZ, targetBlock, origin, closest, closestDistance);
                if (closest != null) {
                    closestDistance = origin.distSqr(closest.atY(0));
                }
            }

            if (closest != null) {
                return Optional.of(closest);
            }
        }

        return Optional.empty();
    }

    private static BlockPos findClosestLoadedMeteoriteInChunk(ServerLevel level, int chunkX, int chunkZ, Block targetBlock,
                                                              BlockPos origin, BlockPos closest, double closestDistance) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            return closest;
        }

        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(targetBlock))) {
                continue;
            }

            int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
            int minY = sectionY << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).is(targetBlock)) {
                            BlockPos pos = new BlockPos(
                                    chunk.getPos().getMinBlockX() + localX,
                                    minY + localY,
                                    chunk.getPos().getMinBlockZ() + localZ);
                            if (!isDataMeteoriteCenter(level, pos)) {
                                continue;
                            }

                            double distance = origin.distSqr(pos.atY(0));
                            if (distance < closestDistance) {
                                closest = pos;
                                closestDistance = distance;
                            }
                        }
                    }
                }
            }
        }
        return closest;
    }

    private static boolean isTargetStillPresent(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
        return chunk == null || chunk.getBlockState(pos).is(AEBlocks.MYSTERIOUS_CUBE.block());
    }

    private static boolean isDataMeteoriteCenter(ServerLevel level, BlockPos center) {
        for (int axis = 0; axis < 3; axis++) {
            for (int offset = -2; offset <= 2; offset += 4) {
                BlockPos nearby = switch (axis) {
                    case 0 -> center.offset(offset, 0, 0);
                    case 1 -> center.offset(0, offset, 0);
                    case 2 -> center.offset(0, 0, offset);
                    default -> throw new IllegalStateException("Unexpected axis: " + axis);
                };

                ChunkPos chunkPos = new ChunkPos(nearby);
                LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
                if (chunk != null && isDataMeteoriteBlock(chunk.getBlockState(nearby).getBlock())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDataMeteoriteBlock(Block block) {
        return block == DEBlocks.ENDER_COHESION_METEORITE_0.get() || block == DEBlocks.ENDER_COHESION_METEORITE_1.get() || block == DEBlocks.ENDER_COHESION_METEORITE_2.get();
    }
}
