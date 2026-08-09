package com.fish_dan_.data_energistics.blockentity.tower.network.binding;

import com.fish_dan_.data_energistics.blockentity.tower.network.TowerDeviceKey;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent request to claim every legal AE subnet exposed at one connector anchor.
 *
 * @param dimensionId        anchor dimension
 * @param anchor             clicked or automatically discovered anchor
 * @param source             manual or automatic provenance
 * @param fifoSequence       first-application sequence within the tower
 * @param enabled            binding-wide state retained for legacy target disabling
 * @param disabledDeviceKeys individually disabled virtual devices
 */
public record TowerBinding(ResourceLocation dimensionId,
                           BlockPos anchor,
                           TowerBindingSource source,
                           long fifoSequence,
                           boolean enabled,
                           Set<TowerDeviceKey> disabledDeviceKeys) {

    /**
     * Validates and defensively copies a binding.
     */
    public TowerBinding {
        if (fifoSequence < 0) {
            throw new IllegalArgumentException("Tower binding FIFO sequence must be non-negative");
        }
        anchor = anchor.immutable();
        disabledDeviceKeys = Set.copyOf(disabledDeviceKeys);
    }

    /**
     * Returns a copy with a new binding-wide enabled state.
     *
     * @param nextEnabled new state
     * @return updated binding
     */
    public TowerBinding withEnabled(boolean nextEnabled) {
        return new TowerBinding(
                this.dimensionId,
                this.anchor,
                this.source,
                this.fifoSequence,
                nextEnabled,
                this.disabledDeviceKeys);
    }

    /**
     * Returns a copy with one device enabled or disabled.
     *
     * @param deviceKey device identity
     * @param disabled  whether the device should be disabled
     * @return updated binding
     */
    public TowerBinding withDeviceDisabled(TowerDeviceKey deviceKey, boolean disabled) {
        HashSet<TowerDeviceKey> nextKeys = new HashSet<>(this.disabledDeviceKeys);
        if (disabled) {
            nextKeys.add(deviceKey);
        } else {
            nextKeys.remove(deviceKey);
        }
        return new TowerBinding(
                this.dimensionId,
                this.anchor,
                this.source,
                this.fifoSequence,
                this.enabled,
                nextKeys);
    }
}
