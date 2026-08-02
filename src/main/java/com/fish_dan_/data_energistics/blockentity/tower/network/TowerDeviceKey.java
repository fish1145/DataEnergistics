package com.fish_dan_.data_energistics.blockentity.tower.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * Persistable identity of one virtual AE device.
 *
 * @param dimensionId dimension containing the node
 * @param position    physical position, or {@code null} for a logical node
 * @param side        part/capability side ordinal, or {@code -1} when not sided
 * @param nodeType    stable logical owner type name
 * @param occurrence  deterministic tie-breaker for otherwise identical nodes
 */
public record TowerDeviceKey(ResourceLocation dimensionId,
                             @Nullable BlockPos position,
                             int side,
                             String nodeType,
                             int occurrence)
        implements Comparable<TowerDeviceKey> {

    /**
     * Validates and normalizes an immutable device key.
     */
    public TowerDeviceKey {
        position = position == null ? null : position.immutable();
        if (side < -1 || side > 5) {
            throw new IllegalArgumentException("Device side must be -1 or a direction ordinal");
        }
        if (nodeType.isBlank()) {
            throw new IllegalArgumentException("Device node type must not be blank");
        }
        if (occurrence < 0) {
            throw new IllegalArgumentException("Device occurrence must be non-negative");
        }
    }

    /**
     * Sorts positioned nodes by dimension, coordinates, side, type and duplicate occurrence. Logical nodes follow all
     * positioned nodes and use the same remaining fields.
     */
    @Override
    public int compareTo(TowerDeviceKey other) {
        int comparison = this.dimensionId.toString().compareTo(other.dimensionId.toString());
        if (comparison != 0) {
            return comparison;
        }
        if (this.position == null || other.position == null) {
            if (this.position != other.position) {
                return this.position == null ? 1 : -1;
            }
        } else {
            comparison = Integer.compare(this.position.getX(), other.position.getX());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(this.position.getY(), other.position.getY());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(this.position.getZ(), other.position.getZ());
            if (comparison != 0) {
                return comparison;
            }
        }
        comparison = Integer.compare(this.side, other.side);
        if (comparison != 0) {
            return comparison;
        }
        comparison = this.nodeType.compareTo(other.nodeType);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(this.occurrence, other.occurrence);
    }
}
