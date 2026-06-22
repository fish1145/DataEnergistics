package com.fish_dan_.data_energistics.worldgen.meteorite;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.FalloutMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import com.google.common.math.StatsAccumulator;
import com.mojang.serialization.MapCodec;

import java.util.Optional;
import java.util.Set;

public class MeteoriteStructure extends Structure {

    public static final MapCodec<MeteoriteStructure> CODEC;
    public static final ResourceKey<Structure> KEY;
    public static final ResourceKey<StructureSet> STRUCTURE_SET_KEY;
    public static final TagKey<Biome> BIOME_TAG_KEY;
    public static StructureType<MeteoriteStructure> TYPE;

    public MeteoriteStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    public StructureType<?> type() {
        return TYPE;
    }

    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
        return !worldgenRandom.nextBoolean() ? Optional.empty() : onTopOfChunkCenter(context, Heightmap.Types.OCEAN_FLOOR_WG,
                structurePiecesBuilder -> generatePieces(structurePiecesBuilder, context));
    }

    private static void generatePieces(StructurePiecesBuilder piecesBuilder, Structure.GenerationContext context) {
        var chunkPos = context.chunkPos();
        WorldgenRandom random = context.random();
        LevelHeightAccessor heightAccessor = context.heightAccessor();
        ChunkGenerator generator = context.chunkGenerator();
        int centerX = chunkPos.getMinBlockX() + random.nextInt(16);
        int centerZ = chunkPos.getMinBlockZ() + random.nextInt(16);
        float meteoriteRadius = random.nextFloat() * 8.0F + 4.0F;
        int yOffset = (int) Math.ceil(meteoriteRadius) + 1;
        Set<Holder<Biome>> t2 = generator.getBiomeSource().getBiomesWithin(centerX, generator.getSeaLevel(), centerZ, 0, context.randomState().sampler());
        Holder<Biome> spawnBiome = t2.stream().findFirst().orElseThrow();
        boolean isOcean = spawnBiome.is(BiomeTags.IS_OCEAN);
        Heightmap.Types heightmapType = isOcean ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
        StatsAccumulator stats = new StatsAccumulator();
        int scanRadius = (int) Math.max(1.0F, meteoriteRadius * 2.0F);

        for (int x = -scanRadius; x <= scanRadius; ++x) {
            for (int z = -scanRadius; z <= scanRadius; ++z) {
                int h = generator.getBaseHeight(centerX + x, centerZ + z, heightmapType, heightAccessor, context.randomState());
                stats.add(h);
            }
        }

        int centerY = (int) stats.mean();
        if (stats.populationVariance() > 5.0F) {
            centerY = (int) (centerY - (stats.mean() - stats.min()) * 0.75F);
        }

        centerY -= yOffset;
        centerY = Math.max(heightAccessor.getMinBuildHeight() + yOffset, centerY);
        BlockPos actualPos = new BlockPos(centerX, centerY, centerZ);
        boolean craterLake = locateWaterAroundTheCrater(actualPos, meteoriteRadius, context);
        CraterType craterType = determineCraterType(actualPos, spawnBiome, random);
        boolean pureCrater = random.nextFloat() > 0.9F;
        FalloutMode fallout = FalloutMode.fromBiome(spawnBiome);
        piecesBuilder.addPiece(new MeteoriteStructurePiece(actualPos, meteoriteRadius, craterType, fallout, pureCrater, craterLake));
    }

    private static boolean locateWaterAroundTheCrater(BlockPos pos, float radius, Structure.GenerationContext context) {
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();
        int seaLevel = generator.getSeaLevel();
        int maxY = seaLevel - 1;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        blockPos.setY(maxY);

        for (int i = pos.getX() - 32; i <= pos.getX() + 32; ++i) {
            blockPos.setX(i);
            for (int k = pos.getZ() - 32; k <= pos.getZ() + 32; ++k) {
                blockPos.setZ(k);
                double dx = i - pos.getX();
                double dz = k - pos.getZ();
                double h = pos.getY() - radius + 1.0F;
                double distanceFrom = dx * dx + dz * dz;
                if ((double) maxY > h + distanceFrom * 0.0175 && (double) maxY < h + distanceFrom * 0.02) {
                    int heigth = generator.getBaseHeight(blockPos.getX(), blockPos.getZ(), Heightmap.Types.OCEAN_FLOOR, heightAccessor, context.randomState());
                    if (heigth < seaLevel) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static CraterType determineCraterType(BlockPos pos, Holder<Biome> biomeHolder, WorldgenRandom random) {
        Biome biome = biomeHolder.value();
        float temp = biome.getBaseTemperature();
        if (biomeHolder.is(BiomeTags.IS_OCEAN)) {
            return CraterType.NONE;
        }

        boolean specialMeteor = random.nextFloat() > 0.5F;
        if (!specialMeteor) {
            return CraterType.NORMAL;
        }

        boolean canSnow = biome.coldEnoughToSnow(pos);
        if (temp >= 1.0F) {
            boolean lava = random.nextFloat() > 0.5F;
            if (!biome.hasPrecipitation()) {
                return lava ? CraterType.LAVA : CraterType.NORMAL;
            }
            if (!canSnow) {
                boolean obsidian = random.nextFloat() > 0.75F;
                CraterType alternativObsidian = obsidian ? CraterType.OBSIDIAN : CraterType.LAVA;
                return lava ? alternativObsidian : CraterType.NORMAL;
            }
        }

        if (temp < 1.0F && temp >= 0.2F) {
            boolean lake = random.nextFloat() > 0.25F;
            boolean lava = random.nextFloat() > 0.8F;
            if (!biome.hasPrecipitation()) {
                return lava ? CraterType.LAVA : CraterType.NORMAL;
            } else if (!canSnow) {
                boolean obsidian = random.nextFloat() > 0.75F;
                CraterType alternativObsidian = obsidian ? CraterType.OBSIDIAN : CraterType.LAVA;
                CraterType craterLake = lake ? CraterType.WATER : CraterType.NORMAL;
                return lava ? alternativObsidian : craterLake;
            } else {
                boolean snow = random.nextFloat() > 0.75F;
                CraterType water = lake ? CraterType.WATER : CraterType.NORMAL;
                return snow ? CraterType.SNOW : water;
            }
        } else if (temp < 0.2F) {
            boolean lake = random.nextFloat() > 0.25F;
            boolean lava = random.nextFloat() > 0.95F;
            boolean frozen = random.nextFloat() > 0.25F;
            if (!biome.hasPrecipitation()) {
                return lava ? CraterType.LAVA : CraterType.NORMAL;
            } else if (!canSnow) {
                CraterType frozenLake = frozen ? CraterType.ICE : CraterType.WATER;
                CraterType craterLake = lake ? frozenLake : CraterType.NORMAL;
                return lava ? CraterType.LAVA : craterLake;
            } else {
                CraterType snowCovered = lake ? CraterType.SNOW : CraterType.NORMAL;
                return lava ? CraterType.LAVA : snowCovered;
            }
        }
        return CraterType.NORMAL;
    }

    static {
        CODEC = simpleCodec(MeteoriteStructure::new);
        KEY = ResourceKey.create(Registries.STRUCTURE, Data_Energistics.id("meteorite"));
        STRUCTURE_SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, Data_Energistics.id("meteorite"));
        BIOME_TAG_KEY = TagKey.create(Registries.BIOME, Data_Energistics.id("has_meteorites"));
        TYPE = () -> CODEC;
    }
}
