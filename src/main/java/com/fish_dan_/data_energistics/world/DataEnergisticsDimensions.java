package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public final class DataEnergisticsDimensions {

    public static final ResourceKey<Level> DATA_SANCTUM = ResourceKey.create(
            Registries.DIMENSION,
            Data_Energistics.id("data_sanctum"));
    public static final ResourceKey<DimensionType> DATA_SANCTUM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            Data_Energistics.id("data_sanctum"));

    private DataEnergisticsDimensions() {}
}
