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
        return pattern.checkPatternAt(world, new JsonMultiBlockControllerView(
                controllerPos,
                frontFacing,
                Direction.UP), structureName);
    }

    private record JsonMultiBlockControllerView(BlockPos position,
                                                Direction frontFacing,
                                                Direction upwardsFacing)
            implements StructureControllerView {}
}
