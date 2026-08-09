package com.fish_dan_.data_energistics.blockentity.tower.network.binding;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable runtime/persistent identity of one tower block entity.
 *
 * @param dimensionId tower dimension
 * @param position    tower position
 */
public record TowerRuntimeKey(ResourceLocation dimensionId, BlockPos position) implements Comparable<TowerRuntimeKey> {

    /** Validates and normalizes the tower identity. */
    public TowerRuntimeKey {
        position = position.immutable();
    }

    @Override
    public int compareTo(TowerRuntimeKey other) {
        int comparison = this.dimensionId.toString().compareTo(other.dimensionId.toString());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(this.position.getX(), other.position.getX());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(this.position.getY(), other.position.getY());
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(this.position.getZ(), other.position.getZ());
    }
}
