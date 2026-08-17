package com.fish_dan_.data_energistics.configuration.snapshot;

import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

/**
 * Immutable orbital reserve, deployment and endpoint limits published with one configuration revision.
 */
public record OrbitalWeaponSettings(
                                    long celestialEnergyCapacity,
                                    long aeEnergyCapacity,
                                    long celestialEnergyUpkeepPerTick,
                                    long aeEnergyUpkeepPerTick,
                                    long celestialEnergyChargePerTick,
                                    long aeEnergyChargePerTick,
                                    int reserveGraceTicks,
                                    double deploymentThreshold,
                                    int redeploymentTicks,
                                    int maxEndpointsPerWeapon,
                                    int maxEndpointsPerDimension,
                                    boolean endpointChunkLoadingEnabled,
                                    int maxAttackChunkTicketsPerTask,
                                    int maxAttackChunkTicketsGlobal,
                                    int maxAttackChunkGenerationPerDimension,
                                    int maxAttackChunkGenerationGlobal,
                                    int maxAttackBlockMutationsPerTaskTick,
                                    int maxAttackBlockMutationsGlobalTick,
                                    int maxCommittedAttackTasks,
                                    long kineticCelestialEnergyCost,
                                    long kineticAeEnergyCost,
                                    int attackWarningTicks,
                                    int kineticCooldownTicks,
                                    long directedEnergyBaseCelestialEnergyCost,
                                    long directedEnergyBaseAeEnergyCost,
                                    long directedEnergyCelestialEnergyPerCoordinate,
                                    long directedEnergyAeEnergyPerCoordinate,
                                    int directedEnergyCooldownTicks,
                                    long directedEnergyEntityDamage,
                                    long digitalAnnihilationCelestialEnergyCost,
                                    long digitalAnnihilationAeEnergyCost,
                                    int digitalAnnihilationCooldownTicks)
        implements DataEnergisticsSettings.OrbitalWeapon {}
