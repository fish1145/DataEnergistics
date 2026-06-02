package com.fish_dan_.data_energistics.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class DataFrameworkCtmModel implements IUnbakedGeometry<DataFrameworkCtmModel> {

    private final String textureRoot;

    public DataFrameworkCtmModel() {
        this("block/data_framework_ctm");
    }

    public DataFrameworkCtmModel(String textureRoot) {
        this.textureRoot = textureRoot;
    }

    @Override
    public @NotNull BakedModel bake(@NotNull IGeometryBakingContext context, @NotNull ModelBaker loader,
            @NotNull Function<Material, TextureAtlasSprite> textureGetter, @NotNull ModelState rotationContainer,
            @NotNull ItemOverrides overrides) {
        return new DataFrameworkCtmBakedModel(textureGetter, this.textureRoot);
    }

    public static class Loader implements IGeometryLoader<DataFrameworkCtmModel> {

        @Override
        public @NotNull DataFrameworkCtmModel read(@NotNull JsonObject jsonObject,
                @NotNull JsonDeserializationContext deserializationContext) throws JsonParseException {
            String textureRoot = jsonObject.has("texture_root")
                    ? jsonObject.get("texture_root").getAsString()
                    : "block/data_framework_ctm";
            return new DataFrameworkCtmModel(textureRoot);
        }
    }
}
