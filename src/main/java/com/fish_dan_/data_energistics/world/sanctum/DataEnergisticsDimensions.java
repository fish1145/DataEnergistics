package com.fish_dan_.data_energistics.world.sanctum;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public final class DataEnergisticsDimensions {

    public static final ResourceKey<Level> METEORITE_CLUSTER = ResourceKey.create(
            Registries.DIMENSION,
            Data_Energistics.id("meteorite_cluster"));
    public static final ResourceKey<DimensionType> METEORITE_CLUSTER_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            Data_Energistics.id("meteorite_cluster"));

    private DataEnergisticsDimensions() {}
}
