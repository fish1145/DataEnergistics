package com.fish_dan_.data_energistics.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

final class FaceQuadBakery {

    private FaceQuadBakery() {}

    static BakedQuad bakeFace(Direction side, TextureAtlasSprite sprite) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setDirection(side);
        builder.setShade(true);
        Vec3i normal = side.getNormal();

        switch (side) {
            case DOWN -> {
                putVertex(builder, sprite, normal, 0, 0, 1, 0, 1);
                putVertex(builder, sprite, normal, 0, 0, 0, 0, 0);
                putVertex(builder, sprite, normal, 1, 0, 0, 1, 0);
                putVertex(builder, sprite, normal, 1, 0, 1, 1, 1);
            }
            case UP -> {
                putVertex(builder, sprite, normal, 0, 1, 0, 0, 0);
                putVertex(builder, sprite, normal, 0, 1, 1, 0, 1);
                putVertex(builder, sprite, normal, 1, 1, 1, 1, 1);
                putVertex(builder, sprite, normal, 1, 1, 0, 1, 0);
            }
            case NORTH -> {
                putVertex(builder, sprite, normal, 1, 1, 0, 0, 0);
                putVertex(builder, sprite, normal, 1, 0, 0, 0, 1);
                putVertex(builder, sprite, normal, 0, 0, 0, 1, 1);
                putVertex(builder, sprite, normal, 0, 1, 0, 1, 0);
            }
            case SOUTH -> {
                putVertex(builder, sprite, normal, 0, 1, 1, 0, 0);
                putVertex(builder, sprite, normal, 0, 0, 1, 0, 1);
                putVertex(builder, sprite, normal, 1, 0, 1, 1, 1);
                putVertex(builder, sprite, normal, 1, 1, 1, 1, 0);
            }
            case WEST -> {
                putVertex(builder, sprite, normal, 0, 1, 0, 0, 0);
                putVertex(builder, sprite, normal, 0, 0, 0, 0, 1);
                putVertex(builder, sprite, normal, 0, 0, 1, 1, 1);
                putVertex(builder, sprite, normal, 0, 1, 1, 1, 0);
            }
            case EAST -> {
                putVertex(builder, sprite, normal, 1, 1, 1, 0, 0);
                putVertex(builder, sprite, normal, 1, 0, 1, 0, 1);
                putVertex(builder, sprite, normal, 1, 0, 0, 1, 1);
                putVertex(builder, sprite, normal, 1, 1, 0, 1, 0);
            }
        }

        return builder.bakeQuad();
    }

    private static void putVertex(QuadBakingVertexConsumer builder, TextureAtlasSprite sprite, Vec3i normal,
                                  float x, float y, float z, float u, float v) {
        builder.addVertex(x, y, z);
        builder.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
        builder.setUv(sprite.getU(u), sprite.getV(v));
    }
}
