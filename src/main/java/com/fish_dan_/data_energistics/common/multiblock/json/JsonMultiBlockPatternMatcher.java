package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.MultiblockState;
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
        StructureMatchResult result = matchOneAllowingFlip(pattern, world, controllerPos, frontFacing, structureName);
        if (result.matched()) {
            return result;
        }
        for (Direction fallbackFacing : Direction.Plane.HORIZONTAL) {
            if (fallbackFacing == frontFacing) {
                continue;
            }
            StructureMatchResult fallbackResult = matchOneAllowingFlip(
                    pattern,
                    world,
                    controllerPos,
                    fallbackFacing,
                    structureName);
            if (fallbackResult.matched()) {
                return fallbackResult;
            }
        }
        return result;
    }

    public static StructureMatchResult matchExact(BlockPattern pattern,
                                                  StructureWorldView world,
                                                  BlockPos controllerPos,
                                                  Direction frontFacing,
                                                  String structureName) {
        return matchExact(pattern, world, controllerPos, frontFacing, false, structureName);
    }

    public static StructureMatchResult matchExact(BlockPattern pattern,
                                                  StructureWorldView world,
                                                  BlockPos controllerPos,
                                                  Direction frontFacing,
                                                  boolean flipped,
                                                  String structureName) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(controllerPos, "controllerPos");
        Objects.requireNonNull(frontFacing, "frontFacing");
        Objects.requireNonNull(structureName, "structureName");
        return matchOne(pattern, world, controllerPos, frontFacing, flipped, structureName);
    }

    private static StructureMatchResult matchOneAllowingFlip(BlockPattern pattern,
                                                             StructureWorldView world,
                                                             BlockPos controllerPos,
                                                             Direction frontFacing,
                                                             String structureName) {
        StructureMatchResult result = matchExact(pattern, world, controllerPos, frontFacing, structureName);
        if (result.matched()) {
            return result;
        }
        return matchExact(pattern, world, controllerPos, frontFacing, true, structureName);
    }

    private static StructureMatchResult matchOne(BlockPattern pattern,
                                                 StructureWorldView world,
                                                 BlockPos controllerPos,
                                                 Direction frontFacing,
                                                 boolean flipped,
                                                 String structureName) {
        MultiblockState state = new MultiblockState(world, controllerPos, structureName);
        return pattern.checkPatternAt(
                state,
                controllerPos,
                frontFacing,
                DEFAULT_UPWARDS_FACING,
                flipped);
    }
}
