package com.fish_dan_.data_energistics.blockentity.tower.network.binding;

import com.fish_dan_.data_energistics.api.registry.connector.EnergyTransferDirection;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerDeviceKey;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Set;

/**
 * Persistent tower link that either claims an ordinary transfer target or joins one peer tower to a logical tower
 * network.
 *
 * @param dimensionId        anchor dimension
 * @param anchor             clicked or automatically discovered anchor
 * @param kind               ordinary transfer target or peer-tower membership edge
 * @param source             manual or automatic provenance
 * @param fifoSequence       first-application sequence within the tower
 * @param enabled            binding-wide state retained for legacy target disabling
 * @param disabledDeviceKeys individually disabled virtual devices
 */
public record TowerBinding(ResourceLocation dimensionId,
                           BlockPos anchor,
                           TowerBindingKind kind,
                           TowerBindingSource source,
                           long fifoSequence,
                           boolean enabled,
                           Set<TowerDeviceKey> disabledDeviceKeys,
                           EnergyTransferDirection energyDirection) {

    public TowerBinding(ResourceLocation dimensionId, BlockPos anchor, TowerBindingKind kind,
                        TowerBindingSource source, long fifoSequence, boolean enabled,
                        Set<TowerDeviceKey> disabledDeviceKeys) {
        this(dimensionId, anchor, kind, source, fifoSequence, enabled, disabledDeviceKeys,
                EnergyTransferDirection.INPUT);
    }

    /**
     * Validates and defensively copies a binding.
     */
    public TowerBinding {
        if (fifoSequence < 0) {
            throw new IllegalArgumentException("Tower binding FIFO sequence must be non-negative");
        }
        anchor = anchor.immutable();
        disabledDeviceKeys = Set.copyOf(disabledDeviceKeys);
        energyDirection = energyDirection == null ? EnergyTransferDirection.INPUT : energyDirection;
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
                this.kind,
                this.source,
                this.fifoSequence,
                nextEnabled,
                this.disabledDeviceKeys,
                this.energyDirection);
    }

    /**
     * Returns a copy with one device enabled or disabled.
     *
     * @param deviceKey device identity
     * @param disabled  whether the device should be disabled
     * @return updated binding
     */
    public TowerBinding withDeviceDisabled(TowerDeviceKey deviceKey, boolean disabled) {
        ObjectOpenHashSet<TowerDeviceKey> nextKeys = new ObjectOpenHashSet<>(this.disabledDeviceKeys);
        if (disabled) {
            nextKeys.add(deviceKey);
        } else {
            nextKeys.remove(deviceKey);
        }
        return new TowerBinding(
                this.dimensionId,
                this.anchor,
                this.kind,
                this.source,
                this.fifoSequence,
                this.enabled,
                nextKeys,
                this.energyDirection);
    }

    /**
     * Returns a copy with the binding classified as an ordinary target or peer tower.
     *
     * @param nextKind new binding kind
     * @return updated binding
     */
    public TowerBinding withKind(TowerBindingKind nextKind) {
        return new TowerBinding(
                this.dimensionId,
                this.anchor,
                nextKind,
                this.source,
                this.fifoSequence,
                this.enabled,
                this.disabledDeviceKeys,
                this.energyDirection);
    }

    public TowerBinding withEnergyDirection(EnergyTransferDirection direction) {
        return new TowerBinding(this.dimensionId, this.anchor, this.kind, this.source, this.fifoSequence,
                this.enabled, this.disabledDeviceKeys, direction);
    }
}
