package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.StructureControllerView;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;

import java.util.Objects;

/**
 * Runs a resolved JSON multiblock pattern against a controller view.
 */
public final class JsonMultiBlockPatternMatcher {

    // GregTech uses a horizontal marker for upright machines; Direction.UP is reserved for extended facing.
    private static final Direction DEFAULT_UPWARDS_FACING = Direction.NORTH;

    private JsonMultiBlockPatternMatcher() {}

    public static StructureMatchResult match(BlockPattern pattern,
                                             StructureWorldView world,
                                             BlockPos controllerPos,
                                             Direction frontFacing,
                                             String structureName) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(controllerPos, "controllerPos");
        Objects.requireNonNull(frontFacing, "frontFacing");
        Objects.requireNonNull(structureName, "structureName");
        StructureMatchResult result = matchOne(pattern, world, controllerPos, frontFacing, structureName);
        if (result.matched()) {
            return result;
        }
        for (Direction fallbackFacing : Direction.Plane.HORIZONTAL) {
            if (fallbackFacing == frontFacing) {
                continue;
            }
            StructureMatchResult fallbackResult = matchOne(pattern, world, controllerPos, fallbackFacing, structureName);
            if (fallbackResult.matched()) {
                return fallbackResult;
            }
        }
        return result;
    }

    private static StructureMatchResult matchOne(BlockPattern pattern,
                                                 StructureWorldView world,
                                                 BlockPos controllerPos,
                                                 Direction frontFacing,
                                                 String structureName) {
        return pattern.checkPatternAt(world, new JsonMultiBlockControllerView(
                controllerPos,
                frontFacing,
                DEFAULT_UPWARDS_FACING), structureName);
    }

    private record JsonMultiBlockControllerView(BlockPos position,
                                                Direction frontFacing,
                                                Direction upwardsFacing)
            implements StructureControllerView {}
}
