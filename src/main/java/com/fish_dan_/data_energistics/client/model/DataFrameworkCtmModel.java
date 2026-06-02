package com.fish_dan_.data_energistics.client.model;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class DataFrameworkCtmModel implements IUnbakedGeometry<DataFrameworkCtmModel> {

    private final String texture;
    private final String connectionTexture;

    public DataFrameworkCtmModel() {
        this("block/data_framework_ctm_off/full", "block/data_framework_ctm_off/sides");
    }

    public DataFrameworkCtmModel(String texture, String connectionTexture) {
        this.texture = texture;
        this.connectionTexture = connectionTexture;
    }

    @Override
    public @NotNull BakedModel bake(@NotNull IGeometryBakingContext context, @NotNull ModelBaker loader,
                                    @NotNull Function<Material, TextureAtlasSprite> textureGetter, @NotNull ModelState rotationContainer,
                                    @NotNull ItemOverrides overrides) {
        return new DataFrameworkCtmBakedModel(textureGetter, this.texture, this.connectionTexture);
    }

    public static class Loader implements IGeometryLoader<DataFrameworkCtmModel> {

        @Override
        public @NotNull DataFrameworkCtmModel read(@NotNull JsonObject jsonObject,
                                                   @NotNull JsonDeserializationContext deserializationContext) throws JsonParseException {
            JsonObject textures = jsonObject.has("textures") ? jsonObject.getAsJsonObject("textures") : null;
            String texture = jsonObject.has("texture") ? jsonObject.get("texture").getAsString() : textures != null && textures.has("all") ? textures.get("all").getAsString() : jsonObject.has("texture_root") ? jsonObject.get("texture_root").getAsString() + "/full" : "block/data_framework_ctm_off/full";
            String connectionTexture = jsonObject.has("connection_texture") ? jsonObject.get("connection_texture").getAsString() : textures != null && textures.has("ctm") ? textures.get("ctm").getAsString() : jsonObject.has("texture_root") ? jsonObject.get("texture_root").getAsString() + "/sides" : texture.replace("/full", "/sides");
            return new DataFrameworkCtmModel(texture, connectionTexture);
        }
    }
}
