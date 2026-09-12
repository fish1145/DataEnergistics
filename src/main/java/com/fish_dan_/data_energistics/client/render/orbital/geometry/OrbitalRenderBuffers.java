package com.fish_dan_.data_energistics.client.render.orbital.geometry;

import net.minecraft.client.renderer.MultiBufferSource;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;

/** Render-thread-owned scratch vertices, flushed immediately instead of entering a mod's deferred entity batches. */
public final class OrbitalRenderBuffers implements AutoCloseable {

    private final ByteBufferBuilder vertices = new ByteBufferBuilder(1_048_576);
    private final MultiBufferSource.BufferSource source = MultiBufferSource.immediate(vertices);

    public MultiBufferSource.BufferSource source() {
        return source;
    }

    @Override
    public void close() {
        vertices.close();
    }
}
