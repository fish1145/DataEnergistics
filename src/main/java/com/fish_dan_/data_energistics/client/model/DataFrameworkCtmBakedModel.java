package com.fish_dan_.data_energistics.client.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.model.ctm.CTMMeshBuilder;
import com.fish_dan_.data_energistics.client.model.ctm.CtmTextureManager;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DataFrameworkCtmBakedModel implements IDynamicBakedModel {

    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ModelProperty<BlockAndTintGetter> LEVEL = new ModelProperty<>();
    private static final ModelProperty<BlockPos> POS = new ModelProperty<>();

    private final TextureAtlasSprite texture;

    public DataFrameworkCtmBakedModel(Function<Material, TextureAtlasSprite> getter, String texture,
                                      String connectionTexture) {
        Material material = new Material(InventoryMenu.BLOCK_ATLAS, Data_Energistics.id(texture));
        this.texture = getter.apply(material);
        Material connectionMaterial = new Material(InventoryMenu.BLOCK_ATLAS, Data_Energistics.id(connectionTexture));
        CtmTextureManager.registerConnection(this.texture, getter.apply(connectionMaterial));
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        return modelData.derive()
                .with(LEVEL, level)
                .with(POS, pos)
                .build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState blockState, @Nullable Direction side,
                                             @NotNull RandomSource randomSource, @NotNull ModelData modelData, @Nullable RenderType renderType) {
        if (side == null || blockState == null) {
            return List.of();
        }
        if (renderType != null && renderType != RenderType.solid()) {
            return List.of();
        }

        List<BakedQuad> quads = new ArrayList<>(1);
        quads.add(FaceQuadBakery.bakeFace(side, this.texture));

        BlockAndTintGetter level = modelData.get(LEVEL);
        BlockPos pos = modelData.get(POS);
        if (level == null || pos == null) {
            return quads;
        }
        return CTMMeshBuilder.buildCTMQuads(level, pos, blockState, quads, side);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return this.texture;
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        return RENDER_TYPES;
    }
}
