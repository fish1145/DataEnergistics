package com.fish_dan_.data_energistics.common.multiblock.vertical;

/**
 * Immutable integer position used by the vertical multiblock framework.
 *
 * <p>
 * The record is deliberately independent from Minecraft's {@code BlockPos} so the scanner remains easy to unit
 * test. Production code can convert to and from {@code BlockPos} at the boundary.
 *
 * @param x horizontal x coordinate
 * @param y vertical y coordinate
 * @param z horizontal z coordinate
 */
public record VerticalMultiBlockPos(int x, int y, int z) {

    public VerticalMultiBlockPos offset(VerticalMultiBlockPos offset) {
        return new VerticalMultiBlockPos(this.x + offset.x, this.y + offset.y, this.z + offset.z);
    }

    public VerticalMultiBlockPos subtract(VerticalMultiBlockPos offset) {
        return new VerticalMultiBlockPos(this.x - offset.x, this.y - offset.y, this.z - offset.z);
    }

    public VerticalMultiBlockPos withY(int y) {
        return new VerticalMultiBlockPos(this.x, y, this.z);
    }
}
