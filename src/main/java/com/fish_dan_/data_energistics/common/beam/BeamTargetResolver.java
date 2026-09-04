package com.fish_dan_.data_energistics.common.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.parts.IPartHost;
import org.jspecify.annotations.Nullable;

/** Loaded-chunk-only target resolution. Linear devices inspect a ray; omni devices inspect explicit peers only. */
final class BeamTargetResolver {

    private BeamTargetResolver() {}

    static @Nullable BeamEndpoint omni(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof BeamEndpoint endpoint &&
                endpoint.beamState().kind() == BeamDeviceKind.OMNI ? endpoint : null;
    }

    static @Nullable BeamEndpoint scan(BeamEndpoint source, Level level) {
        Direction facing = source.beamFacing();
        BlockPos.MutableBlockPos cursor = source.beamPosition().mutable();
        for (int distance = 1; distance <= source.beamState().range(); distance++) {
            cursor.move(facing);
            if (!level.isInWorldBounds(cursor) || !level.hasChunkAt(cursor)) {
                return null;
            }
            var state = level.getBlockState(cursor);
            var blockEntity = level.getBlockEntity(cursor);
            if (source.beamState().kind() == BeamDeviceKind.DIRECTIONAL) {
                if (blockEntity instanceof BeamEndpoint target &&
                        target.beamState().kind() == BeamDeviceKind.DIRECTIONAL) {
                    if (target.beamFacing() == facing.getOpposite()) {
                        return distance <= target.beamState().range() ? target : null;
                    }
                    if (target.beamFacing() == facing) {
                        return null;
                    }
                }
            } else if (blockEntity instanceof IPartHost host) {
                boolean hasBeamPart = false;
                for (Direction side : Direction.values()) {
                    if (host.getPart(side) instanceof BeamEndpoint target &&
                            target.beamState().kind() == BeamDeviceKind.PART) {
                        hasBeamPart = true;
                        if (side == facing.getOpposite()) {
                            return distance <= target.beamState().range() ? target : null;
                        }
                    }
                }
                if (hasBeamPart) {
                    continue;
                }
            }
            if (state.canOcclude() && !state.isAir()) {
                return null;
            }
        }
        return null;
    }
}
