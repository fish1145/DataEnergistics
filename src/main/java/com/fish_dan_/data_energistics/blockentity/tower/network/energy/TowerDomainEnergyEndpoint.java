package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Runtime capability endpoint retained only while its loaded topology revision remains valid.
 *
 * @param location        loaded owner location used to reject stale capability access after chunk unload
 * @param endpoint        stable snapshot identity
 * @param storage         mutable sided capability
 * @param storageIdentity stable physical backing identity compared only by object identity
 * @param direction       topology-time connection permissions
 */
public record TowerDomainEnergyEndpoint(TowerEnergyLocation location,
                                        TowerEnergyEndpointId endpoint,
                                        IEnergyStorage storage,
                                        Object storageIdentity,
                                        TowerEnergyDirection direction) {}
