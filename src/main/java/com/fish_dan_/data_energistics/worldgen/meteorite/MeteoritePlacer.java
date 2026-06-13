package com.fish_dan_.data_energistics.worldgen.meteorite;

import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModFluids;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.Fallout;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.FalloutCopy;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.FalloutMode;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.FalloutSand;
import com.fish_dan_.data_energistics.worldgen.meteorite.fallout.FalloutSnow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import appeng.core.AEConfig;
import appeng.core.definitions.AEBlocks;
import appeng.decorative.AEDecorativeBlock;
import appeng.decorative.solid.BuddingCertusQuartzBlock;
import appeng.decorative.solid.CertusQuartzClusterBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class MeteoritePlacer {

    private static final float CRACKED_METEORITE_CHANCE = 0.27F;
    private static final float EXPOSED_METEORITE_CHANCE = 0.12F;
    private static final float SHATTERED_METEORITE_CHANCE = 0.05F;
    private static final float END_STONE_METEORITE_CHANCE = 0.06F;
    private static final float CHARGED_DATA_CRYSTAL_CHANCE = 0.27F;
    private static final float CERTUS_MOTHER_ROCK_CHANCE = 0.55F;
    private static final int CORE_RADIUS = 1;
    private static final int METEORITE_BODY_RADIUS = 20;
    private static final int METEORITE_FALLOUT_RADIUS = 80;
    private final BlockState skyStone;
    private final BlockState crackedMeteorite;
    private final BlockState exposedMeteorite;
    private final BlockState shatteredMeteorite;
    private final BlockState endStone;
    private final BlockState deactivatedDataCrystalMotherRock;
    private final BlockState chargedDataCrystalMotherRock;
    private final List<BlockState> certusMotherRocks;
    private final List<BlockState> quartzGrowthStages;
    private final Map<Long, CoreColumnData> coreColumns = new HashMap<>();
    private final Map<Long, BlockState> chargedCoreMotherRocks = new HashMap<>();
    private final MeteoriteBlockPutter putter = new MeteoriteBlockPutter();
    private final LevelAccessor level;
    private final RandomSource random;
    private final Fallout type;
    private final BlockPos pos;
    private final int x;
    private final int y;
    private final int z;
    private final double meteoriteSize;
    private final double squaredMeteoriteSize;
    private final double crater;
    private final boolean placeCrater;
    private final CraterType craterType;
    private final boolean pureCrater;
    private final boolean craterLake;
    private final BoundingBox boundingBox;

    public static void place(LevelAccessor level, PlacedMeteoriteSettings settings, BoundingBox boundingBox, RandomSource random) {
        MeteoritePlacer placer = new MeteoritePlacer(level, settings, boundingBox, random);
        placer.place();
    }

    private MeteoritePlacer(LevelAccessor level, PlacedMeteoriteSettings settings, BoundingBox boundingBox, RandomSource random) {
        this.boundingBox = boundingBox;
        this.level = level;
        this.random = random;
        this.pos = settings.getPos();
        this.x = settings.getPos().getX();
        this.y = settings.getPos().getY();
        this.z = settings.getPos().getZ();
        this.meteoriteSize = settings.getMeteoriteRadius();
        this.placeCrater = settings.shouldPlaceCrater();
        this.craterType = settings.getCraterType();
        this.pureCrater = settings.isPureCrater();
        this.craterLake = settings.isCraterLake();
        this.squaredMeteoriteSize = this.meteoriteSize * this.meteoriteSize;
        double realCrater = this.meteoriteSize * 2.0F + 5.0F;
        this.crater = realCrater * realCrater;
        this.skyStone = ((AEDecorativeBlock) AEBlocks.SKY_STONE_BLOCK.block()).defaultBlockState();
        this.crackedMeteorite = ModBlocks.ENDER_COHESION_METEORITE_0.get().defaultBlockState();
        this.exposedMeteorite = ModBlocks.ENDER_COHESION_METEORITE_1.get().defaultBlockState();
        this.shatteredMeteorite = ModBlocks.ENDER_COHESION_METEORITE_2.get().defaultBlockState();
        this.endStone = Blocks.END_STONE.defaultBlockState();
        this.deactivatedDataCrystalMotherRock = ModBlocks.BUDDING_DATA_CRYSTAL_0.get().defaultBlockState();
        this.chargedDataCrystalMotherRock = ModBlocks.BUDDING_DATA_CRYSTAL_4.get().defaultBlockState();
        this.certusMotherRocks = this.getCertusMotherRocks();
        this.quartzGrowthStages = this.getQuartzGrowthStages();
        this.seedChargedCoreMotherRocks();
        this.type = this.getFallout(level, boundingBox.getCenter(), settings.getFallout());
    }

    private List<BlockState> getCertusMotherRocks() {
        return Stream.of(
                AEBlocks.DAMAGED_BUDDING_QUARTZ,
                AEBlocks.CHIPPED_BUDDING_QUARTZ,
                AEBlocks.FLAWED_BUDDING_QUARTZ,
                AEBlocks.FLAWLESS_BUDDING_QUARTZ)
                .map(def -> ((BuddingCertusQuartzBlock) def.block()).defaultBlockState())
                .toList();
    }

    private List<BlockState> getQuartzGrowthStages() {
        return Stream.of(AEBlocks.SMALL_QUARTZ_BUD, AEBlocks.MEDIUM_QUARTZ_BUD, AEBlocks.LARGE_QUARTZ_BUD, AEBlocks.QUARTZ_CLUSTER)
                .map(def -> ((CertusQuartzClusterBlock) def.block()).defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, Direction.UP))
                .toList();
    }

    public void place() {
        if (this.placeCrater) {
            this.placeCrater();
        }

        this.placeMeteorite();
        if (this.placeCrater) {
            this.decay();
        }

        if (this.craterLake) {
            this.placeCraterLake();
        } else if (this.placeCrater && this.craterType == CraterType.NORMAL) {
            this.placeImpactCorrosionLiquid();
        }
    }

    private int minX(int x) {
        if (x < this.boundingBox.minX()) {
            return this.boundingBox.minX();
        }
        return x > this.boundingBox.maxX() ? this.boundingBox.maxX() : x;
    }

    private int minZ(int x) {
        if (x < this.boundingBox.minZ()) {
            return this.boundingBox.minZ();
        }
        return x > this.boundingBox.maxZ() ? this.boundingBox.maxZ() : x;
    }

    private int maxX(int x) {
        if (x < this.boundingBox.minX()) {
            return this.boundingBox.minX();
        }
        return x > this.boundingBox.maxX() ? this.boundingBox.maxX() : x;
    }

    private int maxZ(int x) {
        if (x < this.boundingBox.minZ()) {
            return this.boundingBox.minZ();
        }
        return x > this.boundingBox.maxZ() ? this.boundingBox.maxZ() : x;
    }

    private void placeCrater() {
        int maxY = this.level.getMaxBuildHeight();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockState filler = this.craterType.getFiller().defaultBlockState();

        for (int j = this.y - 5; j <= maxY; ++j) {
            blockPos.setY(j);

            for (int i = this.boundingBox.minX(); i <= this.boundingBox.maxX(); ++i) {
                blockPos.setX(i);

                for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); ++k) {
                    blockPos.setZ(k);
                    double dx = i - this.x;
                    double dz = k - this.z;
                    double h = this.y - this.meteoriteSize + 1.0F + this.type.adjustCrater();
                    double distanceFrom = dx * dx + dz * dz;
                    if ((double) j > h + distanceFrom * 0.02) {
                        BlockState currentBlock = this.level.getBlockState(blockPos);
                        if (this.craterType != CraterType.NORMAL && j < this.y && currentBlock.isSolid()) {
                            if ((double) j > h + distanceFrom * 0.02) {
                                this.putter.put(this.level, blockPos, filler);
                            }
                        } else {
                            this.putter.put(this.level, blockPos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }

        for (ItemEntity e : this.level.getEntitiesOfClass(ItemEntity.class, new AABB(
                this.minX(this.x - 30),
                this.y - 5,
                this.minZ(this.z - 30),
                this.maxX(this.x + 30),
                this.y + 30,
                this.maxZ(this.z + 30)))) {
            e.discard();
        }
    }

    private void placeMeteorite() {
        this.placeMeteoriteSkyStone();
        if (this.boundingBox.isInside(this.pos)) {
            this.placeChest();
        }
    }

    private void placeChest() {
        if (AEConfig.instance().isSpawnPressesInMeteoritesEnabled()) {
            this.putter.put(this.level, this.pos, AEBlocks.MYSTERIOUS_CUBE.block().defaultBlockState());
        }
    }

    private void placeMeteoriteSkyStone() {
        int meteorXLength = this.minX(this.x - METEORITE_BODY_RADIUS);
        int meteorXHeight = this.maxX(this.x + METEORITE_BODY_RADIUS);
        int meteorZLength = this.minZ(this.z - METEORITE_BODY_RADIUS);
        int meteorZHeight = this.maxZ(this.z + METEORITE_BODY_RADIUS);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = meteorXLength; i <= meteorXHeight; ++i) {
            pos.setX(i);

            for (int j = this.y - 8; j < this.y + 8; ++j) {
                pos.setY(j);

                for (int k = meteorZLength; k <= meteorZHeight; ++k) {
                    pos.setZ(k);
                    int dx = i - this.x;
                    int dy = j - this.y;
                    int dz = k - this.z;
                    if ((double) (dx * dx) * 0.7 + (double) (dy * dy) * (j > this.y ? 1.4 : 0.8) + (double) (dz * dz) * 0.7 < this.squaredMeteoriteSize) {
                        boolean isCoreColumn = Math.abs(dx) <= CORE_RADIUS && Math.abs(dz) <= CORE_RADIUS && Math.abs(dy) <= CORE_RADIUS;
                        if (isCoreColumn) {
                            CoreColumnData coreColumn = this.getOrCreateCoreColumn(i, k);
                            if (dy == 0) {
                                if (!pos.equals(this.pos)) {
                                    BlockState middleLayer = coreColumn.middleLayer();
                                    this.putter.put(this.level, pos, middleLayer != null ? middleLayer : Blocks.AIR.defaultBlockState());
                                }
                            } else {
                                this.putter.put(this.level, pos, this.getCoreMotherRock(pos, coreColumn));
                            }
                        } else if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) {
                            this.putter.put(this.level, pos, this.pickOuterMeteoriteBlock(dy));
                        }
                    }
                }
            }
        }
    }

    private CoreColumnData getOrCreateCoreColumn(int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        return this.coreColumns.computeIfAbsent(key, ignored -> this.createCoreColumnData());
    }

    private CoreColumnData createCoreColumnData() {
        if (this.random.nextFloat() < CERTUS_MOTHER_ROCK_CHANCE) {
            return new CoreColumnData(this.randomCertusMotherRock(), this.randomQuartzGrowthStage());
        }
        return new CoreColumnData(this.deactivatedDataCrystalMotherRock, null);
    }

    private void seedChargedCoreMotherRocks() {
        if (this.random.nextFloat() >= CHARGED_DATA_CRYSTAL_CHANCE) {
            return;
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -CORE_RADIUS; dx <= CORE_RADIUS; dx++) {
            for (int dz = -CORE_RADIUS; dz <= CORE_RADIUS; dz++) {
                candidates.add(this.pos.offset(dx, -1, dz));
                candidates.add(this.pos.offset(dx, 1, dz));
            }
        }

        int chargedCount = 1 + this.random.nextInt(2);
        for (int i = 0; i < chargedCount && !candidates.isEmpty(); i++) {
            int selectedIndex = this.random.nextInt(candidates.size());
            BlockPos selectedPos = candidates.remove(selectedIndex);
            this.chargedCoreMotherRocks.put(selectedPos.asLong(), this.chargedDataCrystalMotherRock);
        }
    }

    private BlockState getCoreMotherRock(BlockPos pos, CoreColumnData coreColumn) {
        return this.chargedCoreMotherRocks.getOrDefault(pos.asLong(), coreColumn.motherRock());
    }

    private BlockState randomCertusMotherRock() {
        return this.certusMotherRocks.get(this.random.nextInt(this.certusMotherRocks.size()));
    }

    private BlockState randomQuartzGrowthStage() {
        return this.quartzGrowthStages.get(this.random.nextInt(this.quartzGrowthStages.size()));
    }

    private BlockState pickOuterMeteoriteBlock(int dy) {
        float heightFactor = (float) dy / 8.0F;

        float shatteredWeight = Math.max(0.0F, 1.0F - heightFactor) * 0.15F;
        float exposedWeight = Math.max(0.0F, 0.5F - Math.abs(heightFactor - 0.3F)) * 2.0F;
        float crackedWeight = Math.max(0.0F, heightFactor + 0.3F) * 2.0F;
        float skyStoneWeight = Math.max(0.0F, heightFactor + 0.7F) * 2.0F;

        float totalWeight = shatteredWeight + exposedWeight + crackedWeight + skyStoneWeight;
        if (totalWeight == 0.0F) totalWeight = 1.0F;

        shatteredWeight /= totalWeight;
        exposedWeight /= totalWeight;
        crackedWeight /= totalWeight;
        skyStoneWeight /= totalWeight;

        float roll = this.random.nextFloat();
        if (roll < shatteredWeight) {
            return this.shatteredMeteorite;
        }
        roll -= shatteredWeight;
        if (roll < exposedWeight) {
            return this.exposedMeteorite;
        }
        roll -= exposedWeight;
        if (roll < crackedWeight) {
            return this.crackedMeteorite;
        }
        return this.skyStone;
    }

    private void decay() {
        double randomShit = 0.0D;
        int meteorXLength = this.minX(this.x - METEORITE_FALLOUT_RADIUS);
        int meteorXHeight = this.maxX(this.x + METEORITE_FALLOUT_RADIUS);
        int meteorZLength = this.minZ(this.z - METEORITE_FALLOUT_RADIUS);
        int meteorZHeight = this.maxZ(this.z + METEORITE_FALLOUT_RADIUS);
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockPosUp = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockPosDown = new BlockPos.MutableBlockPos();

        for (int i = meteorXLength; i <= meteorXHeight; ++i) {
            blockPos.setX(i);
            blockPosUp.setX(i);
            blockPosDown.setX(i);

            for (int k = meteorZLength; k <= meteorZHeight; ++k) {
                blockPos.setZ(k);
                blockPosUp.setZ(k);
                blockPosDown.setZ(k);

                for (int j = this.y - 9; j < this.y + 30; ++j) {
                    blockPos.setY(j);
                    blockPosUp.setY(j + 1);
                    blockPosDown.setY(j - 1);
                    if (Math.abs(i - this.x) <= CORE_RADIUS && Math.abs(j - this.y) <= CORE_RADIUS && Math.abs(k - this.z) <= CORE_RADIUS) {
                        continue;
                    }
                    BlockState state = this.level.getBlockState(blockPos);
                    Block blk = this.level.getBlockState(blockPos).getBlock();
                    if (!this.pureCrater || blk != this.craterType.getFiller()) {
                        if (state.canBeReplaced()) {
                            if (!this.level.isEmptyBlock(blockPosUp)) {
                                BlockState stateUp = this.level.getBlockState(blockPosUp);
                                this.level.setBlock(blockPos, stateUp, 3);
                            } else if (randomShit < 100.0D * this.crater) {
                                double dx = i - this.x;
                                double dy = j - this.y;
                                double dz = k - this.z;
                                double dist = dx * dx + dy * dy + dz * dz;
                                BlockState xf = this.level.getBlockState(blockPosDown);
                                if (!xf.canBeReplaced()) {
                                    double extraRange = this.random.nextDouble() * 0.6;
                                    double height = this.crater * (extraRange + 0.2) - Math.abs(dist - this.crater * 1.7);
                                    if (!xf.isAir() && height > 0.0F && this.random.nextDouble() > 0.6) {
                                        ++randomShit;
                                        this.type.getRandomFall(this.level, blockPos);
                                    }
                                }
                            }
                        } else if (this.level.isEmptyBlock(blockPosUp) && this.random.nextDouble() > 0.4) {
                            double dx = i - this.x;
                            double dy = j - this.y;
                            double dz = k - this.z;
                            double dr2 = dx * dx + dy * dy + dz * dz;
                            if ((!(Math.abs(dx) <= 1.0F) || !(Math.abs(dy) <= 1.0F) || !(Math.abs(dz) <= 1.0F)) && dr2 < this.crater * 1.6) {
                                this.type.getRandomInset(this.level, blockPos);
                            }
                        }
                    }
                }
            }
        }
    }

    private void placeCraterLake() {
        int maxY = this.level.getSeaLevel() - 1;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (int currentX = this.boundingBox.minX(); currentX <= this.boundingBox.maxX(); ++currentX) {
            blockPos.setX(currentX);

            for (int currentZ = this.boundingBox.minZ(); currentZ <= this.boundingBox.maxZ(); ++currentZ) {
                blockPos.setZ(currentZ);
                ChunkAccess currentChunk = this.level.getChunk(blockPos);

                for (int currentY = this.y - 5; currentY <= maxY; ++currentY) {
                    blockPos.setY(currentY);
                    double dx = currentX - this.x;
                    double dz = currentZ - this.z;
                    double h = this.y - this.meteoriteSize + 1.0F + this.type.adjustCrater();
                    double distanceFrom = dx * dx + dz * dz;
                    if ((double) currentY > h + distanceFrom * 0.02) {
                        BlockState currentBlock = currentChunk.getBlockState(blockPos);
                        if (currentBlock.getBlock() == Blocks.AIR) {
                            this.putter.put(this.level, blockPos, Blocks.WATER.defaultBlockState());
                            if (currentY == maxY) {
                                this.level.scheduleTick(blockPos, Fluids.WATER, 0);
                            }
                        }
                    } else if ((double) (maxY + (maxY - currentY) * 2 + 2) > h + distanceFrom * 0.02) {
                        this.pillarDownSlopeBlocks(currentChunk, blockPos);
                    }
                }
            }
        }
    }

    private void placeImpactCorrosionLiquid() {
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockState corrosionLiquid = ModFluids.DATA_CORROSION_LIQUID_BLOCK.get().defaultBlockState();

        for (int currentX = this.boundingBox.minX(); currentX <= this.boundingBox.maxX(); ++currentX) {
            blockPos.setX(currentX);

            for (int currentZ = this.boundingBox.minZ(); currentZ <= this.boundingBox.maxZ(); ++currentZ) {
                blockPos.setZ(currentZ);
                ChunkAccess currentChunk = this.level.getChunk(blockPos);
                int lowestAirY = Integer.MAX_VALUE;

                for (int currentY = this.y - 5; currentY <= this.y - 1; ++currentY) {
                    blockPos.setY(currentY);
                    double dx = currentX - this.x;
                    double dz = currentZ - this.z;
                    double h = this.y - this.meteoriteSize + 1.0F + this.type.adjustCrater();
                    double distanceFrom = dx * dx + dz * dz;
                    if ((double) currentY > h + distanceFrom * 0.02) {
                        BlockState currentBlock = currentChunk.getBlockState(blockPos);
                        if (currentBlock.getBlock() == Blocks.AIR) {
                            lowestAirY = Math.min(lowestAirY, currentY);
                        }
                    }
                }

                if (lowestAirY != Integer.MAX_VALUE) {
                    blockPos.setY(lowestAirY);
                    this.putter.put(this.level, blockPos, corrosionLiquid);
                    this.level.scheduleTick(blockPos, ModFluids.DATA_CORROSION_LIQUID.get(), 0);
                }
            }
        }
    }

    private void pillarDownSlopeBlocks(ChunkAccess currentChunk, BlockPos.MutableBlockPos blockPos) {
        BlockPos.MutableBlockPos enclosingBlockPos = new BlockPos.MutableBlockPos();
        enclosingBlockPos.set(blockPos);

        for (int i = 0; i < 20 && !this.placeEnclosingBlock(currentChunk, enclosingBlockPos); ++i) {
            enclosingBlockPos.move(Direction.DOWN);
        }
    }

    private boolean placeEnclosingBlock(ChunkAccess currentChunk, BlockPos.MutableBlockPos enclosingBlockPos) {
        BlockState currentState = currentChunk.getBlockState(enclosingBlockPos);
        if (currentState.getBlock() == Blocks.AIR || currentState.getFluidState().isEmpty() && (currentState.canBeReplaced() || currentState.is(BlockTags.REPLACEABLE))) {
            if (this.craterType == CraterType.LAVA && this.level.getRandom().nextFloat() < 0.075F) {
                this.putter.put(this.level, enclosingBlockPos, Blocks.MAGMA_BLOCK.defaultBlockState());
            } else {
                this.type.getRandomFall(this.level, enclosingBlockPos);
            }
            return false;
        }
        return true;
    }

    private Fallout getFallout(LevelAccessor level, BlockPos pos, FalloutMode mode) {
        return switch (mode) {
            case SAND -> new FalloutSand(level, pos, this.putter, this.skyStone, this.random);
            case TERRACOTTA -> new FalloutCopy(level, pos, this.putter, this.skyStone, this.random);
            case ICE_SNOW -> new FalloutSnow(level, pos, this.putter, this.skyStone, this.random);
            default -> new Fallout(this.putter, this.skyStone, this.random);
        };
    }

    private record CoreColumnData(BlockState motherRock, BlockState middleLayer) {}
}
