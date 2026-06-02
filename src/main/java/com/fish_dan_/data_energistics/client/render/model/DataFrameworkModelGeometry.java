package com.fish_dan_.data_energistics.client.render.model;

import java.util.function.Function;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

public final class DataFrameworkModelGeometry implements IUnbakedGeometry<DataFrameworkModelGeometry> {

    public static final DataFrameworkModelGeometry INSTANCE = new DataFrameworkModelGeometry();

    private DataFrameworkModelGeometry() {
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        return new DataFrameworkBakedModel(
                spriteGetter.apply(context.getMaterial("corner")),
                spriteGetter.apply(context.getMaterial("edge_h")),
                spriteGetter.apply(context.getMaterial("edge_v")),
                spriteGetter.apply(context.getMaterial("center")),
                overrides);
    }
}
