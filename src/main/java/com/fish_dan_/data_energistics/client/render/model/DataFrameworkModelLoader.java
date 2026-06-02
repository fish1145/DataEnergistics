package com.fish_dan_.data_energistics.client.render.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

public final class DataFrameworkModelLoader implements IGeometryLoader<DataFrameworkModelGeometry> {

    public static final DataFrameworkModelLoader INSTANCE = new DataFrameworkModelLoader();

    private DataFrameworkModelLoader() {
    }

    @Override
    public DataFrameworkModelGeometry read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
        return DataFrameworkModelGeometry.INSTANCE;
    }
}
