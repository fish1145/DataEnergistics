package com.fish_dan_.data_energistics.client.model.ctm;

import com.fish_dan_.data_energistics.client.model.quad.MutableQuadView;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static com.fish_dan_.data_energistics.client.model.ctm.CtmTextureManager.CTM_SPRITE_CACHE;

public class CTMMeshBuilder {

    private static final int SHEET_SIZE = 8;

    public static List<BakedQuad> buildCTMQuads(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                                List<BakedQuad> quads, Direction cullFace) {
        int textureIndex = getTextureIndex(buildContext(level, pos, state, cullFace));
        if (textureIndex < 0) {
            return quads;
        }

        List<BakedQuad> result = new ArrayList<>(quads.size());
        MutableQuadView emitter = MutableQuadView.getInstance();

        for (BakedQuad originalQuad : quads) {
            TextureAtlasSprite originalSprite = originalQuad.getSprite();
            @SuppressWarnings("resource")
            TextureAtlasSprite connectedSprite = CTM_SPRITE_CACHE.get(originalSprite.contents().name());
            if (connectedSprite == null) {
                result.add(originalQuad);
                continue;
            }

            emitter.fromVanilla(originalQuad, cullFace);
            for (int vertex = 0; vertex < 4; vertex++) {
                emitter.uv(vertex, getTargetU(originalSprite, connectedSprite, emitter.u(vertex), textureIndex),
                        getTargetV(originalSprite, connectedSprite, emitter.v(vertex), textureIndex));
            }
            result.add(emitter.toBakedQuad(connectedSprite));
        }

        return result;
    }

    private static CTContext buildContext(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction face) {
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        Direction horizontal = getRightDirection(face);
        Direction vertical = getUpDirection(face);
        horizontal = positive ? horizontal.getOpposite() : horizontal;
        if (face == Direction.DOWN) {
            vertical = vertical.getOpposite();
            horizontal = horizontal.getOpposite();
        }

        CTContext context = new CTContext();
        context.up = testConnection(level, pos, state, face, horizontal, vertical, 0, 1);
        context.down = testConnection(level, pos, state, face, horizontal, vertical, 0, -1);
        context.left = testConnection(level, pos, state, face, horizontal, vertical, -1, 0);
        context.right = testConnection(level, pos, state, face, horizontal, vertical, 1, 0);
        context.topLeft = context.up && context.left
                && testConnection(level, pos, state, face, horizontal, vertical, -1, 1);
        context.topRight = context.up && context.right
                && testConnection(level, pos, state, face, horizontal, vertical, 1, 1);
        context.bottomLeft = context.down && context.left
                && testConnection(level, pos, state, face, horizontal, vertical, -1, -1);
        context.bottomRight = context.down && context.right
                && testConnection(level, pos, state, face, horizontal, vertical, 1, -1);
        return context;
    }

    private static boolean testConnection(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                          Direction textureSide, Direction horizontal, Direction vertical,
                                          int horizontalOffset, int verticalOffset) {
        BlockPos targetPos = pos.relative(horizontal, horizontalOffset)
                .relative(vertical, verticalOffset);
        BlockState targetState = getCTBlockState(level, level.getBlockState(pos), textureSide, pos, targetPos);
        return connectsTo(state, targetState, level, pos, targetPos, textureSide);
    }

    private static BlockState getCTBlockState(BlockAndTintGetter level, BlockState reference, Direction face,
                                              BlockPos fromPos, BlockPos toPos) {
        BlockState blockState = level.getBlockState(toPos);
        return blockState.getAppearance(level, toPos, face, reference, fromPos);
    }

    private static boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter level, BlockPos pos,
                                      BlockPos otherPos, Direction face) {
        return !isBeingBlocked(state, level, pos, otherPos, face) && state.getBlock() == other.getBlock();
    }

    private static boolean isBeingBlocked(BlockState state, BlockAndTintGetter level, BlockPos pos, BlockPos otherPos,
                                          Direction face) {
        BlockPos blockingPos = otherPos.relative(face);
        BlockState blockState = level.getBlockState(pos);
        BlockState blockingState = level.getBlockState(blockingPos);

        if (!Block.isFaceFull(blockingState.getShape(level, blockingPos), face.getOpposite())) {
            return false;
        }
        if (face.getAxis().choose(pos.getX(), pos.getY(), pos.getZ())
                != face.getAxis().choose(otherPos.getX(), otherPos.getY(), otherPos.getZ())) {
            return false;
        }

        return connectsTo(state,
                getCTBlockState(level, blockState, face.getOpposite(), pos.relative(face), blockingPos),
                level, pos, blockingPos, face);
    }

    private static Direction getUpDirection(Direction face) {
        return face.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
    }

    private static Direction getRightDirection(Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.WEST;
    }

    private static int getTextureIndex(CTContext context) {
        int tileX = 0;
        int tileY = 0;
        int borders = (!context.up ? 1 : 0)
                + (!context.down ? 1 : 0)
                + (!context.left ? 1 : 0)
                + (!context.right ? 1 : 0);

        if (context.up) {
            tileX++;
        }
        if (context.down) {
            tileX += 2;
        }
        if (context.left) {
            tileY++;
        }
        if (context.right) {
            tileY += 2;
        }

        if (borders == 0) {
            if (context.topRight) {
                tileX++;
            }
            if (context.topLeft) {
                tileX += 2;
            }
            if (context.bottomRight) {
                tileY += 2;
            }
            if (context.bottomLeft) {
                tileY++;
            }
        }

        if (borders == 1) {
            if (!context.right && (context.topLeft || context.bottomLeft)) {
                tileY = 4;
                tileX = -1 + (context.bottomLeft ? 1 : 0) + (context.topLeft ? 1 : 0) * 2;
            }
            if (!context.left && (context.topRight || context.bottomRight)) {
                tileY = 5;
                tileX = -1 + (context.bottomRight ? 1 : 0) + (context.topRight ? 1 : 0) * 2;
            }
            if (!context.down && (context.topLeft || context.topRight)) {
                tileY = 6;
                tileX = -1 + (context.topLeft ? 1 : 0) + (context.topRight ? 1 : 0) * 2;
            }
            if (!context.up && (context.bottomLeft || context.bottomRight)) {
                tileY = 7;
                tileX = -1 + (context.bottomLeft ? 1 : 0) + (context.bottomRight ? 1 : 0) * 2;
            }
        }

        if (borders == 2
                && ((context.up && context.left && context.topLeft)
                || (context.down && context.left && context.bottomLeft)
                || (context.up && context.right && context.topRight)
                || (context.down && context.right && context.bottomRight))) {
            tileX += 3;
        }

        return tileX + SHEET_SIZE * tileY;
    }

    private static float getTargetU(TextureAtlasSprite original, TextureAtlasSprite target, float localU, int index) {
        float uOffset = index % SHEET_SIZE;
        return target.getU((getUnInterpolatedU(original, localU) + uOffset) / SHEET_SIZE);
    }

    private static float getTargetV(TextureAtlasSprite original, TextureAtlasSprite target, float localV, int index) {
        float vOffset = index / (float) SHEET_SIZE;
        return target.getV((getUnInterpolatedV(original, localV) + vOffset) / SHEET_SIZE);
    }

    private static float getUnInterpolatedU(TextureAtlasSprite sprite, float u) {
        return (u - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
    }

    private static float getUnInterpolatedV(TextureAtlasSprite sprite, float v) {
        return (v - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
    }

    private static class CTContext {
        private boolean up;
        private boolean down;
        private boolean left;
        private boolean right;
        private boolean topLeft;
        private boolean topRight;
        private boolean bottomLeft;
        private boolean bottomRight;
    }
}
