package com.fish_dan_.data_energistics.orbital.astronomy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Resolves data-pack-controlled observability and immutable per-dimension production settings.
 */
public final class AstronomyDimensionRules {

    private static final int DAY_LENGTH_TICKS = 24_000;
    private static final TagKey<DimensionType> OBSERVABLE = TagKey.create(
            Registries.DIMENSION_TYPE,
            Data_Energistics.id("astronomy/observable"));
    private static final TagKey<DimensionType> PERMANENT_OBSERVATION = TagKey.create(
            Registries.DIMENSION_TYPE,
            Data_Energistics.id("astronomy/permanent_observation"));

    private AstronomyDimensionRules() {}

    /**
     * Returns whether the current dimension type is explicitly observable by a data-pack tag.
     */
    public static boolean isObservable(ServerLevel level) {
        return level.dimensionTypeRegistration().is(OBSERVABLE);
    }

    /**
     * Returns whether this dimension is inside its observation window for the current server tick.
     */
    public static boolean isObservationWindowOpen(
                                                  ServerLevel level,
                                                  DataEnergisticsSettings.Astronomy settings) {
        if (level.dimensionTypeRegistration().is(PERMANENT_OBSERVATION)) {
            return true;
        }
        long dayTick = Math.floorMod(level.getDayTime(), DAY_LENGTH_TICKS);
        return dayTick >= settings.observationWindowStartTick() && dayTick < settings.observationWindowEndTick();
    }

    /**
     * Calculates the whole Celestial Energy output for this dimension and current weather.
     */
    public static long celestialEnergyPerTick(
                                              ServerLevel level,
                                              DataEnergisticsSettings.Astronomy settings) {
        return celestialEnergyPerTick(level, settings, settings.lowTierCelestialEnergyPerTick());
    }

    /**
     * Applies this dimension's configured multiplier and current rain multiplier to a caller-provided base output.
     */
    public static long celestialEnergyPerTick(
                                              ServerLevel level,
                                              DataEnergisticsSettings.Astronomy settings,
                                              long baseOutput) {
        if (baseOutput < 0L) {
            throw new IllegalArgumentException("Base Celestial Energy output must be non-negative: " + baseOutput);
        }
        ResourceLocation dimensionId = level.dimension().location();
        double dimensionMultiplier = settings.dimensionMultipliers()
                .getOrDefault(dimensionId, settings.defaultDimensionMultiplier());
        double weatherMultiplier = level.isRaining() ? settings.rainOutputMultiplier() : 1.0D;
        double scaledOutput = baseOutput * dimensionMultiplier * weatherMultiplier;
        if (scaledOutput >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) Math.floor(scaledOutput));
    }
}
