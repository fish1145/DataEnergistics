package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.item.MeVacuumMenuHost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;

import appeng.api.client.StorageCellModels;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MeVacuumBakedModel implements BakedModel {

    private static final float CELL_X = 5.0F / 16.0F;
    private static final float CELL_Y = -5.5F / 16.0F;
    private static final float[] CELL_Z = {
            12.75F / 16.0F,
            15.75F / 16.0F,
            18.75F / 16.0F,
            21.75F / 16.0F,
            24.75F / 16.0F
    };

    private final BakedModel delegate;

    public MeVacuumBakedModel(BakedModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return this.delegate.getQuads(state, direction, random);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        return this.delegate.getQuads(state, side, rand, data, renderType);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.delegate.useAmbientOcclusion();
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return this.delegate.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public boolean isGui3d() {
        return this.delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.delegate.getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return this.delegate.getParticleIcon(data);
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack,
                                     boolean applyLeftHandTransform) {
        BakedModel transformed = this.delegate.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return transformed == this.delegate ? this : new MeVacuumBakedModel(transformed);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return this.delegate.getModelData(level, pos, state, modelData);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
        return this.delegate.getRenderTypes(itemStack, fabulous);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        HolderLookup.Provider registries = getRegistries();
        if (registries == null) {
            return List.of(this);
        }

        NonNullList<ItemStack> cells = MeVacuumMenuHost.readStoredCells(itemStack, registries);
        for (ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                return List.of(new CellRenderPass(this.delegate, cells));
            }
        }

        return List.of(this);
    }

    @Nullable
    private static HolderLookup.Provider getRegistries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }

        ClientPacketListener connection = minecraft.getConnection();
        return connection == null ? null : connection.registryAccess();
    }

    private record CellRenderPass(BakedModel delegate, NonNullList<ItemStack> cells) implements BakedModel {

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
            return getQuads(state, direction, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random,
                                        ModelData data, @Nullable RenderType renderType) {
            var quads = new java.util.ArrayList<BakedQuad>(
                    this.delegate.getQuads(state, direction, random, data, renderType));
            Minecraft minecraft = Minecraft.getInstance();

            for (int slot = 0; slot < Math.min(this.cells.size(), CELL_Z.length); slot++) {
                ItemStack cellStack = this.cells.get(slot);
                if (cellStack.isEmpty()) {
                    continue;
                }

                BakedModel cellModel = getCellModel(minecraft, cellStack);
                for (BakedQuad quad : cellModel.getQuads(state, direction, random, ModelData.EMPTY, renderType)) {
                    quads.add(translateQuad(quad, CELL_X, CELL_Y, CELL_Z[slot]));
                }
            }

            return quads;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return this.delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return this.delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return this.delegate.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return this.delegate.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return this.delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return this.delegate.getOverrides();
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
            return this.delegate.getRenderTypes(itemStack, fabulous);
        }
    }

    private static BakedModel getCellModel(Minecraft minecraft, ItemStack stack) {
        ResourceLocation modelId = StorageCellModels.model(stack.getItem());
        if (modelId == null) {
            modelId = StorageCellModels.getDefaultModel();
        }
        return minecraft.getModelManager().getModel(ModelResourceLocation.standalone(modelId));
    }

    private static BakedQuad translateQuad(BakedQuad quad, float x, float y, float z) {
        int[] vertices = quad.getVertices().clone();
        int vertexSize = vertices.length / 4;

        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * vertexSize;
            vertices[offset] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset]) + x);
            vertices[offset + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 1]) + y);
            vertices[offset + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 2]) + z);
        }

        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                quad.hasAmbientOcclusion());
    }
}
