package com.fish_dan_.data_energistics.client.render.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.fish_dan_.data_energistics.blockentity.DataFrameworkModelData;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

import appeng.client.render.cablebus.CubeBuilder;
import appeng.util.Platform;

public final class DataFrameworkBakedModel implements IDynamicBakedModel {

    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.solid());

    private final TextureAtlasSprite cornerTexture;
    private final TextureAtlasSprite horizontalEdgeTexture;
    private final TextureAtlasSprite verticalEdgeTexture;
    private final TextureAtlasSprite centerTexture;
    private final ItemOverrides overrides;

    public DataFrameworkBakedModel(TextureAtlasSprite cornerTexture, TextureAtlasSprite horizontalEdgeTexture,
                                   TextureAtlasSprite verticalEdgeTexture, TextureAtlasSprite centerTexture, ItemOverrides overrides) {
        this.cornerTexture = cornerTexture;
        this.horizontalEdgeTexture = horizontalEdgeTexture;
        this.verticalEdgeTexture = verticalEdgeTexture;
        this.centerTexture = centerTexture;
        this.overrides = overrides;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData,
                                    @Nullable RenderType renderType) {
        if (side == null) {
            if (state != null) {
                return Collections.emptyList();
            }

            List<BakedQuad> itemQuads = new ArrayList<>();
            EnumSet<Direction> connections = EnumSet.noneOf(Direction.class);
            for (Direction face : Direction.values()) {
                addFace(itemQuads, face, connections);
            }
            return itemQuads;
        }

        List<BakedQuad> quads = new ArrayList<>();
        addFace(quads, side, getConnections(extraData));
        return quads;
    }

    private void addFace(List<BakedQuad> quads, Direction side, EnumSet<Direction> connections) {
        CubeBuilder builder = new CubeBuilder(quads);
        builder.setDrawFaces(EnumSet.of(side));

        addRing(builder, side, connections);

        float x2 = connections.contains(Direction.EAST) ? 16.0F : 13.0F;
        float x1 = connections.contains(Direction.WEST) ? 0.0F : 3.0F;
        float y2 = connections.contains(Direction.UP) ? 16.0F : 13.0F;
        float y1 = connections.contains(Direction.DOWN) ? 0.0F : 3.0F;
        float z2 = connections.contains(Direction.SOUTH) ? 16.0F : 13.0F;
        float z1 = connections.contains(Direction.NORTH) ? 0.0F : 3.0F;

        switch (side) {
            case DOWN, UP -> {
                y1 = 0.0F;
                y2 = 16.0F;
            }
            case NORTH, SOUTH -> {
                z1 = 0.0F;
                z2 = 16.0F;
            }
            case WEST, EAST -> {
                x1 = 0.0F;
                x2 = 16.0F;
            }
        }

        builder.setTexture(centerTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);
    }

    private void addRing(CubeBuilder builder, Direction side, EnumSet<Direction> connections) {
        builder.setTexture(cornerTexture);
        addCornerCap(builder, connections, side, Direction.UP, Direction.EAST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.UP, Direction.EAST, Direction.SOUTH);
        addCornerCap(builder, connections, side, Direction.UP, Direction.WEST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.UP, Direction.WEST, Direction.SOUTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.EAST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.EAST, Direction.SOUTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.WEST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.WEST, Direction.SOUTH);

        for (Direction edge : Direction.values()) {
            if (edge == side || edge == side.getOpposite() || connections.contains(edge)) {
                continue;
            }

            if (usesVerticalEdgeTexture(side, edge)) {
                builder.setTexture(verticalEdgeTexture);
            } else {
                builder.setTexture(horizontalEdgeTexture);
            }

            float x1 = 0.0F;
            float y1 = 0.0F;
            float z1 = 0.0F;
            float x2 = 16.0F;
            float y2 = 16.0F;
            float z2 = 16.0F;

            switch (edge) {
                case DOWN -> y2 = 3.0F;
                case UP -> y1 = 13.0F;
                case WEST -> x2 = 3.0F;
                case EAST -> x1 = 13.0F;
                case NORTH -> z2 = 3.0F;
                case SOUTH -> z1 = 13.0F;
            }

            Direction perpendicular = Platform.rotateAround(edge, side);
            for (Direction cornerCandidate : EnumSet.of(perpendicular, perpendicular.getOpposite())) {
                if (connections.contains(cornerCandidate)) {
                    continue;
                }

                switch (cornerCandidate) {
                    case DOWN -> y1 = 3.0F;
                    case UP -> y2 = 13.0F;
                    case NORTH -> z1 = 3.0F;
                    case SOUTH -> z2 = 13.0F;
                    case WEST -> x1 = 3.0F;
                    case EAST -> x2 = 13.0F;
                }
            }

            builder.addCube(x1, y1, z1, x2, y2, z2);
        }
    }

    private void addCornerCap(CubeBuilder builder, EnumSet<Direction> connections, Direction side, Direction vertical,
                              Direction horizontal, Direction depth) {
        if (connections.contains(vertical) || connections.contains(horizontal) || connections.contains(depth)) {
            return;
        }

        if (side != vertical && side != horizontal && side != depth) {
            return;
        }

        float x1 = horizontal == Direction.WEST ? 0.0F : 13.0F;
        float y1 = vertical == Direction.DOWN ? 0.0F : 13.0F;
        float z1 = depth == Direction.NORTH ? 0.0F : 13.0F;
        float x2 = horizontal == Direction.WEST ? 3.0F : 16.0F;
        float y2 = vertical == Direction.DOWN ? 3.0F : 16.0F;
        float z2 = depth == Direction.NORTH ? 3.0F : 16.0F;
        builder.addCube(x1, y1, z1, x2, y2, z2);
    }

    private static EnumSet<Direction> getConnections(ModelData modelData) {
        if (modelData.has(DataFrameworkModelData.CONNECTIONS)) {
            return modelData.get(DataFrameworkModelData.CONNECTIONS);
        }
        return EnumSet.noneOf(Direction.class);
    }

    private static boolean usesVerticalEdgeTexture(Direction side, Direction edge) {
        if (side.getAxis() != Axis.Y) {
            return edge == Direction.NORTH || edge == Direction.EAST || edge == Direction.WEST || edge == Direction.SOUTH;
        }
        return edge == Direction.EAST || edge == Direction.WEST;
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
    public TextureAtlasSprite getParticleIcon() {
        return centerTexture;
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return RENDER_TYPES;
    }
}
