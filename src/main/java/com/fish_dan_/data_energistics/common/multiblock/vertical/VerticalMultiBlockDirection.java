package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.List;

/**
 * Horizontal orientation used by the vertical multiblock scanner.
 *
 * <p>
 * Only horizontal rotation is part of v1. The direction transforms local template coordinates into world-space
 * offsets around the detected structure origin.
 */
public enum VerticalMultiBlockDirection {

    NORTH,
    EAST,
    SOUTH,
    WEST;

    public static List<VerticalMultiBlockDirection> horizontal() {
        return List.of(values());
    }

    public VerticalMultiBlockPos rotate(VerticalMultiBlockPos local, int width, int depth) {
        return switch (this) {
            case NORTH -> local;
            case EAST -> new VerticalMultiBlockPos(depth - 1 - local.z(), local.y(), local.x());
            case SOUTH -> new VerticalMultiBlockPos(width - 1 - local.x(), local.y(), depth - 1 - local.z());
            case WEST -> new VerticalMultiBlockPos(local.z(), local.y(), width - 1 - local.x());
        };
    }
}
