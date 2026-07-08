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

import java.util.ArrayList;
import java.util.List;

public final class MeVacuumBakedModel implements BakedModel {

    private static final CellTransform[] CELL_TRANSFORMS = {
            CellTransform.left(7.25F, 4.00F, 6.00F),
            CellTransform.left(7.25F, 4.00F, 9.00F),
            CellTransform.right(8.75F, 4.00F, 6.00F),
            CellTransform.right(8.75F, 4.00F, 9.00F)
    };

    private final BakedModel delegate;
    private final boolean customRenderer;

    public MeVacuumBakedModel(BakedModel delegate) {
        this(delegate, true);
    }

    private MeVacuumBakedModel(BakedModel delegate, boolean customRenderer) {
        this.delegate = delegate;
        this.customRenderer = customRenderer;
    }

    public MeVacuumBakedModel withoutCustomRenderer() {
        return this.customRenderer ? new MeVacuumBakedModel(this.delegate, false) : this;
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
        return this.customRenderer || this.delegate.isCustomRenderer();
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
        return transformed == this.delegate ? this : new MeVacuumBakedModel(transformed, this.customRenderer);
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
            var quads = new ArrayList<BakedQuad>(
                    this.delegate.getQuads(state, direction, random, data, renderType));
            Minecraft minecraft = Minecraft.getInstance();

            for (int slot = 0; slot < Math.min(this.cells.size(), CELL_TRANSFORMS.length); slot++) {
                ItemStack cellStack = this.cells.get(slot);
                if (cellStack.isEmpty()) {
                    continue;
                }

                BakedModel cellModel = getCellModel(minecraft, cellStack);
                List<BakedQuad> cellQuads = getAllCellQuads(cellModel, state, random, renderType);
                for (BakedQuad quad : cellQuads) {
                    BakedQuad transformed = transformQuad(quad, CELL_TRANSFORMS[slot]);
                    if (direction == null || transformed.getDirection() == direction) {
                        quads.add(transformed);
                    }
                }
            }

            return quads;
        }

        private static List<BakedQuad> getAllCellQuads(BakedModel cellModel, @Nullable BlockState state,
                                                       RandomSource random,
                                                       @Nullable RenderType renderType) {
            var quads = new ArrayList<BakedQuad>();
            quads.addAll(cellModel.getQuads(state, null, random, ModelData.EMPTY, renderType));
            for (Direction side : Direction.values()) {
                quads.addAll(cellModel.getQuads(state, side, random, ModelData.EMPTY, renderType));
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

    private static BakedQuad transformQuad(BakedQuad quad, CellTransform transform) {
        int[] vertices = quad.getVertices().clone();
        int vertexSize = vertices.length / 4;

        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * vertexSize;
            float localX = Float.intBitsToFloat(vertices[offset]) * 16.0F;
            float localY = Float.intBitsToFloat(vertices[offset + 1]) * 16.0F;
            float localZ = Float.intBitsToFloat(vertices[offset + 2]) * 16.0F;

            float worldX;
            float worldY;
            float worldZ;
            if (transform.rightSide()) {
                worldX = transform.x() + 2.0F - localZ;
                worldY = transform.y() + localX;
                worldZ = transform.z() + localY;
            } else {
                float depth = 2.0F - localZ;
                worldX = transform.x() - depth;
                worldY = transform.y() + localX;
                worldZ = transform.z() + localY;
            }

            vertices[offset] = Float.floatToRawIntBits(worldX / 16.0F);
            vertices[offset + 1] = Float.floatToRawIntBits(worldY / 16.0F);
            vertices[offset + 2] = Float.floatToRawIntBits(worldZ / 16.0F);
        }

        if (transform.rightSide()) {
            reverseWinding(vertices, vertexSize);
        }

        return new BakedQuad(vertices, quad.getTintIndex(), transformDirection(quad.getDirection(), transform),
                quad.getSprite(), false, false);
    }

    private static void reverseWinding(int[] vertices, int vertexSize) {
        for (int i = 0; i < vertexSize; i++) {
            int second = vertexSize + i;
            int fourth = vertexSize * 3 + i;
            int value = vertices[second];
            vertices[second] = vertices[fourth];
            vertices[fourth] = value;
        }
    }

    private static Direction transformDirection(Direction direction, CellTransform transform) {
        if (transform.rightSide()) {
            return switch (direction) {
                case NORTH -> Direction.EAST;
                case SOUTH -> Direction.WEST;
                case EAST -> Direction.UP;
                case WEST -> Direction.DOWN;
                case UP -> Direction.SOUTH;
                case DOWN -> Direction.NORTH;
            };
        }

        return switch (direction) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    private record CellTransform(float x, float y, float z, boolean rightSide) {

        private static CellTransform left(float x, float y, float z) {
            return new CellTransform(x, y, z, false);
        }

        private static CellTransform right(float x, float y, float z) {
            return new CellTransform(x, y, z, true);
        }
    }
}
