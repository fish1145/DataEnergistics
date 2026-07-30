package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.EchoKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.FormationPlanePart;

import java.util.HashSet;
import java.util.Set;

/**
 * Server implementation that reconstructs the vanilla sonic-boom path and deposits Echo into each intersected
 * formation plane's own ME grid.
 */
public final class SonicBoomEchoCaptureImpl implements SonicBoomEchoCapture {

    private static final double SONIC_EXTENSION_BEYOND_TARGET = 7.0D;
    private static final double INTERSECTION_EPSILON = 1.0E-7D;
    private static final long ECHO_PER_PLANE = 1L;

    /**
     * Observes only direct Warden sonic damage. It deliberately leaves the event untouched so vanilla damage and
     * knockback continue normally.
     *
     * @param event incoming living damage event raised by NeoForge
     */
    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(event.getEntity().level() instanceof ServerLevel level) || !isDirectWardenSonicBoom(source)) {
            return;
        }

        capture(level, (Warden) source.getDirectEntity(), event.getEntity());
    }

    @Override
    public int capture(ServerLevel level, Warden warden, LivingEntity target) {
        Vec3 start = warden.position().add(
                warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, warden.getYRot()));
        Vec3 end = extendPastTarget(start, target.getEyePosition());
        if (start.distanceToSqr(end) <= INTERSECTION_EPSILON) {
            return 0;
        }

        Set<FormationPlaneIdentity> visitedPlanes = new HashSet<>();
        int insertedEcho = 0;
        int minimumChunkX = SectionPos.blockToSectionCoord(Mth.floor(Math.min(start.x, end.x)) - 1);
        int maximumChunkX = SectionPos.blockToSectionCoord(Mth.floor(Math.max(start.x, end.x)) + 1);
        int minimumChunkZ = SectionPos.blockToSectionCoord(Mth.floor(Math.min(start.z, end.z)) - 1);
        int maximumChunkZ = SectionPos.blockToSectionCoord(Mth.floor(Math.max(start.z, end.z)) + 1);

        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof IPartHost partHost) {
                        insertedEcho += captureFromHost(start, end, partHost, visitedPlanes);
                    }
                }
            }
        }
        return insertedEcho;
    }

    /**
     * Identifies the exact source shape emitted by {@link DamageSources#sonicBoom}.
     *
     * @param source damage source to classify
     * @return true only for a direct Warden sonic boom
     */
    static boolean isDirectWardenSonicBoom(DamageSource source) {
        return source.is(DamageTypes.SONIC_BOOM) && source.isDirect() && source.getDirectEntity() instanceof Warden;
    }

    /**
     * Extends the reconstructed path exactly seven blocks past the target eye along the Warden-to-target direction.
     *
     * @param start     Warden chest attachment position
     * @param targetEye original target eye position
     * @return extended sonic path endpoint
     */
    static Vec3 extendPastTarget(Vec3 start, Vec3 targetEye) {
        Vec3 direction = targetEye.subtract(start);
        if (direction.lengthSqr() <= INTERSECTION_EPSILON) {
            return targetEye;
        }
        return targetEye.add(direction.normalize().scale(SONIC_EXTENSION_BEYOND_TARGET));
    }

    /**
     * Tests the visible one-block face of a part and rejects rays travelling out through its back.
     *
     * @param start sonic path start
     * @param end   extended sonic path end
     * @param pos   formation plane host position
     * @param side  host face on which the formation plane is installed
     * @return true when the ray approaches and crosses the plane's front face
     */
    static boolean intersectsFrontFace(Vec3 start, Vec3 end, BlockPos pos, Direction side) {
        Vec3 delta = end.subtract(start);
        double normalTravel = delta.x * side.getStepX() + delta.y * side.getStepY() + delta.z * side.getStepZ();
        if (normalTravel >= -INTERSECTION_EPSILON) {
            return false;
        }

        Direction.Axis axis = side.getAxis();
        double axisTravel = component(delta, axis);
        if (Math.abs(axisTravel) <= INTERSECTION_EPSILON) {
            return false;
        }

        double planeCoordinate = coordinate(pos, axis) + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D);
        double distanceAlongSegment = (planeCoordinate - component(start, axis)) / axisTravel;
        if (distanceAlongSegment < -INTERSECTION_EPSILON || distanceAlongSegment > 1.0D + INTERSECTION_EPSILON) {
            return false;
        }

        Vec3 intersection = start.add(delta.scale(distanceAlongSegment));
        return switch (axis) {
            case X -> withinBlockFace(intersection.y, pos.getY()) && withinBlockFace(intersection.z, pos.getZ());
            case Y -> withinBlockFace(intersection.x, pos.getX()) && withinBlockFace(intersection.z, pos.getZ());
            case Z -> withinBlockFace(intersection.x, pos.getX()) && withinBlockFace(intersection.y, pos.getY());
        };
    }

    private int captureFromHost(
                                Vec3 start,
                                Vec3 end,
                                IPartHost partHost,
                                Set<FormationPlaneIdentity> visitedPlanes) {
        BlockPos position = partHost.getBlockEntity().getBlockPos();
        int insertedEcho = 0;
        for (Direction side : Direction.values()) {
            try {
                IPart part = partHost.getPart(side);
                FormationPlaneIdentity identity = new FormationPlaneIdentity(position, side);
                if (!(part instanceof FormationPlanePart formationPlane) || !intersectsFrontFace(start, end, position, side) || !visitedPlanes.add(identity)) {
                    continue;
                }

                if (insertEcho(formationPlane, position, side)) {
                    insertedEcho++;
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to capture Warden Echo at formation plane {} on face {}: {}",
                        position,
                        side,
                        exception.getMessage(),
                        exception);
            }
        }
        return insertedEcho;
    }

    private boolean insertEcho(FormationPlanePart formationPlane, BlockPos position, Direction side) {
        if (!formationPlane.getMainNode().isOnline()) {
            return false;
        }

        IGrid grid = formationPlane.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        MachineSource source = new MachineSource(formationPlane);
        long simulated = storage.insert(EchoKey.of(), ECHO_PER_PLANE, Actionable.SIMULATE, source);
        if (simulated != ECHO_PER_PLANE) {
            return false;
        }

        long inserted = storage.insert(EchoKey.of(), ECHO_PER_PLANE, Actionable.MODULATE, source);
        if (inserted != ECHO_PER_PLANE) {
            Data_Energistics.LOGGER.error(
                    "Formation plane {} on face {} simulated one Echo but inserted {}",
                    position,
                    side,
                    inserted);
            return false;
        }
        return true;
    }

    private static double component(Vec3 vector, Direction.Axis axis) {
        return switch (axis) {
            case X -> vector.x;
            case Y -> vector.y;
            case Z -> vector.z;
        };
    }

    private static int coordinate(BlockPos position, Direction.Axis axis) {
        return switch (axis) {
            case X -> position.getX();
            case Y -> position.getY();
            case Z -> position.getZ();
        };
    }

    private static boolean withinBlockFace(double coordinate, int minimum) {
        return coordinate >= minimum - INTERSECTION_EPSILON && coordinate <= minimum + 1.0D + INTERSECTION_EPSILON;
    }

    private record FormationPlaneIdentity(BlockPos position, Direction side) {

        private FormationPlaneIdentity {
            position = position.immutable();
        }
    }
}
