package com.fish_dan_.data_energistics.configuration.snapshot;

import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Immutable low-tier astronomy production settings and dimension multiplier overrides.
 */
public record AstronomySettings(
                                long lowTierCelestialEnergyPerTick,
                                long lowTierAeEnergyPerTick,
                                long highTierMirrorCelestialEnergyPerTick1To4,
                                long highTierMirrorCelestialEnergyPerTick5To8,
                                long highTierMirrorCelestialEnergyPerTick9To12,
                                long highTierMirrorCelestialEnergyPerTick13To16,
                                long highTierCoreAeEnergyPerTick,
                                long highTierMirrorAeEnergyPerTick,
                                int highTierMinimumMirrors,
                                int highTierMaximumMirrors,
                                int highTierMirrorHorizontalRange,
                                int highTierMirrorVerticalRange,
                                int highTierWaveguidePathLength,
                                double rainOutputMultiplier,
                                int observationWindowStartTick,
                                int observationWindowEndTick,
                                double defaultDimensionMultiplier,
                                Map<ResourceLocation, Double> dimensionMultipliers)
        implements DataEnergisticsSettings.Astronomy {

    public AstronomySettings {
        dimensionMultipliers = Map.copyOf(dimensionMultipliers);
    }
}
