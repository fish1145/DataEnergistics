package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.sanctum.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Set;

public class DataSanctumPortalLogic {

    private static final String COOLDOWN_UNTIL_TAG = Data_Energistics.MODID + ".data_sanctum_portal_cooldown_until";
    private static final int PORTAL_MODE = 2;
    private static final int COOLDOWN_TICKS = 80;
    private static final int RETURN_Y = 80;
    private static final int RETURN_SPACING = 64;
    private static final int RETURN_COORDINATE_LIMIT = 24_000;
    private static final int RETURN_SCAN_PER_TICK = 32;
    private static final int PLATFORM_RADIUS = 2;
    private int returnScanCursor;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickReturnPortals(event.getServer());
    }

    public static boolean isPortalMode(int mode) {
        return mode == PORTAL_MODE;
    }

    public static void tickSourcePortal(DataSanctumBlockEntity sanctum) {
        if (!(sanctum.getLevel() instanceof ServerLevel sourceLevel) || !sanctum.isOnline()) {
            return;
        }

        ServerLevel targetLevel = sourceLevel.getServer().getLevel(DataEnergisticsDimensions.METEORITE_CLUSTER);
        if (targetLevel == null) {
            return;
        }

        BlockPos returnPos = getReturnPortalPos(sourceLevel.dimension().location(), sanctum.getBlockPos());
        registerSourcePortal(sourceLevel.getServer(), returnPos, sourceLevel.dimension().location(), sanctum.getBlockPos());
        prepareReturnPlatform(targetLevel, returnPos);
        teleportEntities(sourceLevel, getPortalArea(sanctum.getBlockPos()), targetLevel, getArrivalPos(returnPos));
    }

    public static void unregisterSourcePortal(DataSanctumBlockEntity sanctum) {
        if (!(sanctum.getLevel() instanceof ServerLevel sourceLevel)) {
            return;
        }

        BlockPos returnPos = getReturnPortalPos(sourceLevel.dimension().location(), sanctum.getBlockPos());
        DataSanctumPortalSavedData.get(sourceLevel.getServer())
                .removePortal(returnPos, sourceLevel.dimension().location(), sanctum.getBlockPos());
    }

    private void tickReturnPortals(MinecraftServer server) {
        ServerLevel dataSanctumLevel = server.getLevel(DataEnergisticsDimensions.METEORITE_CLUSTER);
        if (dataSanctumLevel == null) {
            return;
        }

        List<DataSanctumPortalSavedData.PortalRecord> portals = DataSanctumPortalSavedData.get(server).getPortals();
        if (portals.isEmpty()) {
            this.returnScanCursor = 0;
            return;
        }
        if (this.returnScanCursor >= portals.size()) {
            this.returnScanCursor = 0;
        }

        int scanned = 0;
        while (scanned < RETURN_SCAN_PER_TICK && scanned < portals.size()) {
            DataSanctumPortalSavedData.PortalRecord portal = portals.get(this.returnScanCursor);
            this.returnScanCursor = (this.returnScanCursor + 1) % portals.size();
            scanned++;

            ServerLevel sourceLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, portal.sourceDimensionId()));
            if (sourceLevel == null || !isSourcePortalActive(sourceLevel, portal.sourcePos())) {
                continue;
            }

            prepareReturnPlatform(dataSanctumLevel, portal.returnPos());
            teleportEntities(dataSanctumLevel, getPortalArea(portal.returnPos()), sourceLevel, getSourceArrivalPos(portal.sourcePos()));
        }
    }

    private static boolean isSourcePortalActive(ServerLevel sourceLevel, BlockPos sourcePos) {
        sourceLevel.getChunkAt(sourcePos);
        if (!(sourceLevel.getBlockEntity(sourcePos) instanceof DataSanctumBlockEntity sanctum)) {
            return false;
        }

        BlockState state = sourceLevel.getBlockState(sourcePos);
        return state.is(DEBlocks.DATA_SANCTUM.get()) && state.hasProperty(DataSanctumBlock.MODE) && state.getValue(DataSanctumBlock.MODE) == PORTAL_MODE && sanctum.isOnline();
    }

    private static void registerSourcePortal(MinecraftServer server, BlockPos returnPos, ResourceLocation sourceDimensionId,
                                             BlockPos sourcePos) {
        DataSanctumPortalSavedData.get(server).registerPortal(returnPos, sourceDimensionId, sourcePos);
    }

    private static void teleportEntities(ServerLevel sourceLevel, AABB sourceArea, ServerLevel targetLevel,
                                         Vec3 targetPos) {
        List<Entity> entities = sourceLevel.getEntities((Entity) null, sourceArea,
                entity -> entity.isAlive() && entity.getVehicle() == null && !isOnPortalCooldown(entity));
        for (Entity entity : entities) {
            teleportEntity(entity, targetLevel, targetPos);
        }
    }

    private static void teleportEntity(Entity entity, ServerLevel targetLevel, Vec3 targetPos) {
        setPortalCooldown(entity);
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(targetLevel, targetPos.x, targetPos.y, targetPos.z, Set.of(),
                    player.getYRot(), player.getXRot());
            player.fallDistance = 0.0F;
            setPortalCooldown(player);
            return;
        }

        if (entity.level() == targetLevel) {
            entity.teleportTo(targetPos.x, targetPos.y, targetPos.z);
            entity.fallDistance = 0.0F;
            return;
        }

        Entity movedEntity = entity.changeDimension(new DimensionTransition(
                targetLevel,
                targetPos,
                entity.getDeltaMovement(),
                entity.getYRot(),
                entity.getXRot(),
                DimensionTransition.DO_NOTHING));
        if (movedEntity != null) {
            movedEntity.fallDistance = 0.0F;
            setPortalCooldown(movedEntity);
        }
    }

    private static AABB getPortalArea(BlockPos pos) {
        return new AABB(
                pos.getX() - 1.5D,
                pos.getY(),
                pos.getZ() - 1.5D,
                pos.getX() + 2.5D,
                pos.getY() + 4.0D,
                pos.getZ() + 2.5D);
    }

    private static Vec3 getArrivalPos(BlockPos portalPos) {
        return new Vec3(portalPos.getX() + 0.5D, portalPos.getY() + 1.0D, portalPos.getZ() + 0.5D);
    }

    private static Vec3 getSourceArrivalPos(BlockPos sourcePos) {
        return new Vec3(sourcePos.getX() + 0.5D, sourcePos.getY() + 1.0D, sourcePos.getZ() + 0.5D);
    }

    private static BlockPos getReturnPortalPos(ResourceLocation sourceDimensionId, BlockPos sourcePos) {
        int dimensionHash = sourceDimensionId.toString().hashCode();
        int x = clampReturnCoordinate(sourcePos.getX() * RETURN_SPACING + Math.floorMod(dimensionHash, RETURN_SPACING));
        int z = clampReturnCoordinate(sourcePos.getZ() * RETURN_SPACING + Math.floorMod(dimensionHash / RETURN_SPACING, RETURN_SPACING));
        return new BlockPos(x, RETURN_Y, z);
    }

    private static int clampReturnCoordinate(int coordinate) {
        return Math.max(-RETURN_COORDINATE_LIMIT, Math.min(RETURN_COORDINATE_LIMIT, coordinate));
    }

    private static void prepareReturnPlatform(ServerLevel level, BlockPos portalPos) {
        for (int offsetX = -PLATFORM_RADIUS; offsetX <= PLATFORM_RADIUS; offsetX++) {
            for (int offsetZ = -PLATFORM_RADIUS; offsetZ <= PLATFORM_RADIUS; offsetZ++) {
                BlockPos floorPos = portalPos.offset(offsetX, -1, offsetZ);
                if (level.getBlockState(floorPos).isAir()) {
                    level.setBlock(floorPos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                for (int offsetY = 0; offsetY <= 3; offsetY++) {
                    BlockPos clearPos = portalPos.offset(offsetX, offsetY, offsetZ);
                    BlockState state = level.getBlockState(clearPos);
                    if (!state.isAir() && !state.is(DEBlocks.DATA_SANCTUM_RETURN_PORTAL.get())) {
                        level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }

        BlockState portalState = level.getBlockState(portalPos);
        if (!portalState.is(DEBlocks.DATA_SANCTUM_RETURN_PORTAL.get())) {
            level.setBlock(portalPos, DEBlocks.DATA_SANCTUM_RETURN_PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean isOnPortalCooldown(Entity entity) {
        return entity.getPersistentData().getLong(COOLDOWN_UNTIL_TAG) > entity.level().getGameTime();
    }

    private static void setPortalCooldown(Entity entity) {
        entity.getPersistentData().putLong(COOLDOWN_UNTIL_TAG, entity.level().getGameTime() + COOLDOWN_TICKS);
        entity.setPortalCooldown(COOLDOWN_TICKS);
    }
}
