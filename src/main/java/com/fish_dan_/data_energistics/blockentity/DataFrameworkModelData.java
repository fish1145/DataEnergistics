package com.fish_dan_.data_energistics.blockentity;

import java.util.EnumSet;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class DataFrameworkModelData {

    public static final ModelProperty<EnumSet<Direction>> CONNECTIONS = new ModelProperty<>();

    private DataFrameworkModelData() {
    }

    public static ModelData create(EnumSet<Direction> connections) {
        return ModelData.builder()
                .with(CONNECTIONS, connections)
                .build();
    }
}
