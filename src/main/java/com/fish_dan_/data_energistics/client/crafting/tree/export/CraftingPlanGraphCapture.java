package com.fish_dan_.data_energistics.client.crafting.tree.export;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.FastColor.ABGR32;
import net.neoforged.neoforge.client.GlStateBackup;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/** Reusable render-thread transparent target, with IO-thread pixel conversion before PNG encoding. */
final class CraftingPlanGraphCapture implements AutoCloseable {

    private final RenderState previous;
    private final RenderTarget target = new CaptureTarget();
    private final ByteBufferBuilder vertexMemory;

    CraftingPlanGraphCapture(int width, int height) {
        RenderSystem.assertOnRenderThread();
        this.previous = new RenderState();
        ByteBufferBuilder allocated = null;
        try {
            allocated = new ByteBufferBuilder(786432);
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            this.target.resize(width, height, Minecraft.ON_OSX);
            this.target.setClearColor(0, 0, 0, 0);
            this.vertexMemory = allocated;
        } catch (Throwable failure) {
            try {
                this.target.destroyBuffers();
                if (allocated != null) allocated.close();
            } finally {
                this.previous.restore();
            }
            throw failure;
        }
    }

    /** Clears the previous capture and starts a fresh GUI pose; callers must flush before downloading. */
    GuiGraphics begin(Minecraft client, double scale, double x, double y) {
        RenderSystem.disableScissor();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        this.target.clear(Minecraft.ON_OSX);
        this.target.bindWrite(true);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, this.target.width, this.target.height,
                0, -10000, 10000), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiGraphics graphics = new GuiGraphics(client, MultiBufferSource.immediate(this.vertexMemory));
        graphics.pose().scale((float) scale, (float) scale, 1);
        graphics.pose().translate(x, y, 0);
        return graphics;
    }

    /** Transfers ownership of one native RGBA image to the caller; no second raw-pixel copy is retained. */
    NativeImage download() {
        NativeImage image = new NativeImage(this.target.width, this.target.height, false);
        try {
            RenderSystem.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
            RenderSystem.bindTexture(this.target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return image;
        } catch (Throwable failure) {
            image.close();
            throw failure;
        }
    }

    /**
     * Converts the downloaded framebuffer's premultiplied RGB to PNG's straight alpha in place.
     * The IO worker must own this RGBA image exclusively and call exactly once before encoding.
     * NativeImage's RGBA pixel API packs Java ints as ABGR, not ARGB.
     */
    static void unpremultiplyAlpha(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = ABGR32.alpha(pixel);
                if (alpha == 255) continue;
                if (alpha == 0) {
                    image.setPixelRGBA(x, y, 0);
                } else {
                    image.setPixelRGBA(x, y, ABGR32.color(alpha,
                            unpremultiplyChannel(ABGR32.blue(pixel), alpha),
                            unpremultiplyChannel(ABGR32.green(pixel), alpha),
                            unpremultiplyChannel(ABGR32.red(pixel), alpha)));
                }
            }
        }
    }

    private static int unpremultiplyChannel(int channel, int alpha) {
        // Round to the nearest byte; framebuffer quantization can otherwise exceed the 8-bit channel boundary.
        return Math.min(255, (channel * 255 + alpha / 2) / alpha);
    }

    @Override
    public void close() {
        try {
            this.vertexMemory.close();
        } finally {
            try {
                this.target.destroyBuffers();
            } finally {
                this.previous.restore();
            }
        }
    }

    /** Allocation is deferred so even partially initialized targets can be destroyed. */
    private static final class CaptureTarget extends RenderTarget {

        private CaptureTarget() {
            super(true);
        }
    }

    private static final class RenderState {

        private final GlStateBackup flags = new GlStateBackup();
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();
        private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
        private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
        private final @Nullable ShaderInstance shader = RenderSystem.getShader();
        private final int shaderProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final float[] shaderColor = RenderSystem.getShaderColor().clone();
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final int activeTexture = GlStateManager._getActiveTexture();
        private final int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        private final int packRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        private final int packSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        private final int packSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        private final int pixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        private final int pixelUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        private final boolean stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        private final Int2IntMap shaderTextures = new Int2IntOpenHashMap();
        private final Int2IntMap textureBindings = new Int2IntOpenHashMap();

        private RenderState() {
            RenderSystem.backupGlState(flags);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            try {
                // This Minecraft version owns twelve shader texture slots.
                for (int slot = 0; slot < 12; slot++) {
                    shaderTextures.put(slot, RenderSystem.getShaderTexture(slot));
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0 + slot);
                    textureBindings.put(slot, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                }
            } finally {
                RenderSystem.activeTexture(activeTexture);
            }
        }

        private void restore() {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.getModelViewStack().set(modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setTextureMatrix(textureMatrix);
            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(shaderProgram);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            for (int slot = 0; slot < 12; slot++) {
                RenderSystem.setShaderTexture(slot, shaderTextures.get(slot));
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + slot);
                RenderSystem.bindTexture(textureBindings.get(slot));
            }
            RenderSystem.activeTexture(activeTexture);
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, packAlignment);
            GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, packRowLength);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, packSkipRows);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, packSkipPixels);
            RenderSystem.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBuffer);
            RenderSystem.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.clearDepth(clearDepth);
            GlStateManager._scissorBox(scissor[0], scissor[1], scissor[2], scissor[3]);
            RenderSystem.restoreGlState(flags);
            if (stencilEnabled) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
