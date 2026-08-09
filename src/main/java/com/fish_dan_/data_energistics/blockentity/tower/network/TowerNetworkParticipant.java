package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBinding;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGrid;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import java.util.List;

/**
 * Typed tower-facing contract consumed by the grid-level network domain.
 */
public interface TowerNetworkParticipant {

    /** @return stable tower identity */
    TowerRuntimeKey towerKey();

    /** @return currently loaded tower level */
    ServerLevel towerLevel();

    /** @return physical primary grid containing the tower node */
    IGrid towerGrid();

    /** @return whether the physical tower node is active */
    boolean isTowerNetworkActive();

    /** @return whether current tower mode exposes AE targets */
    boolean towerAllowsAe();

    /** @return whether current tower mode exposes FE targets */
    boolean towerAllowsFe();

    /** @return persisted manual and automatic bindings */
    List<TowerBinding> towerBindings();

    /** @return loaded FE candidate locations discovered by this tower */
    List<TowerEnergyLocation> towerEnergyLocations();

    /** @return AE host used as the Applied Flux action source */
    AENetworkedBlockEntity towerEnergyHost();

    /** @return currently quarantined FE retained by this tower */
    long towerQuarantinedEnergy();

    /**
     * Replaces the tower's quarantined FE after a failed domain compensation.
     *
     * @param amount non-negative quarantined FE
     */
    void setTowerQuarantinedEnergy(long amount);

    /**
     * Publishes the latest immutable domain result for UI and per-device actions.
     *
     * @param snapshot latest tower-specific result
     */
    void applyTowerNetworkSnapshot(TowerNetworkTowerSnapshot snapshot);
}
