package com.fish_dan_.data_energistics.client.crafting.tree.export;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRenderer;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.GlStateBackup;

import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphViewLod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Full-layout capture on the render thread, with PNG encoding and filesystem work on Minecraft's IO pool. */
public final class CraftingPlanGraphPngExport {

    private static final int MAX_SIDE = 8192;
    private static final long MAX_PIXELS = 16L * 1024 * 1024;
    private static final double PREFERRED_SCALE = 2;

    private CraftingPlanGraphPngExport() {}

    /**
     * Captures every node and route in the supplied layout, without viewport clipping or selection highlights.
     * Must be called on the render thread; feedback is delivered on the client main thread, including IO failures.
     * The immutable graph/layout may be retained until this asynchronous export completes.
     */
    public static void export(CraftingPlanGraph graph, Layout layout, boolean showAmounts, Consumer<Component> feedback) {
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        NativeImage captured = null;
        try {
            PixelDimensions dimensions = dimensions(layout.bounds());
            captured = capture(client, graph, layout, dimensions, showAmounts);
            NativeImage output = captured;
            Util.ioPool().execute(() -> save(client, output, dimensions, feedback));
            // The IO task now owns the image; rejected submissions leave ownership here for cleanup.
            captured = null;
        } catch (Exception failure) {
            reportFailure(client, feedback, failure);
        } finally {
            if (captured != null) {
                captured.close();
            }
        }
    }

    private static PixelDimensions dimensions(Bounds bounds) {
        double width = bounds.width();
        double height = bounds.height();
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(bounds.x()) || !Double.isFinite(bounds.y()) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("The crafting graph must have finite, positive export bounds");
        }
        int maximum = Math.min(MAX_SIDE, RenderSystem.maxSupportedTextureSize());
        double scale = Math.min(PREFERRED_SCALE, Math.min(maximum / width, maximum / height));
        scale = Math.min(scale, Math.sqrt(MAX_PIXELS / width / height));
        int pixelsWide = Math.max(1, (int) Math.floor(width * scale));
        int pixelsHigh = Math.max(1, (int) Math.floor(height * scale));
        // Recompute one uniform scale after integer rounding so neither dimension is cropped.
        scale = Math.min(pixelsWide / width, pixelsHigh / height);
        return new PixelDimensions(pixelsWide, pixelsHigh, scale);
    }

    private static NativeImage capture(Minecraft client, CraftingPlanGraph graph, Layout layout,
                                       PixelDimensions dimensions, boolean showAmounts) {
        NativeImage image = new NativeImage(dimensions.width(), dimensions.height(), false);
        boolean complete = false;
        try {
            RenderState previous = new RenderState();
            RenderTarget target = new PngRenderTarget();
            try (ByteBufferBuilder vertexMemory = new ByteBufferBuilder(786432)) {
                RenderSystem.disableScissor();
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                RenderSystem.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                target.resize(dimensions.width(), dimensions.height(), Minecraft.ON_OSX);
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, dimensions.width(), dimensions.height(),
                        0, -10000, 10000), VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.getModelViewStack().identity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                GuiGraphics graphics = new GuiGraphics(client, MultiBufferSource.immediate(vertexMemory));
                graphics.pose().scale((float) dimensions.scale(), (float) dimensions.scale(), 1);
                graphics.pose().translate(-layout.bounds().x(), -layout.bounds().y(), 0);
                new CraftingPlanGraphRenderer(graph).draw(graphics, layout, GraphViewLod.FULL, showAmounts,
                        -1, IntSets.emptySet(), null);
                graphics.flush();
                RenderSystem.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
                GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
                GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
                RenderSystem.bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
            } finally {
                try {
                    target.destroyBuffers();
                } finally {
                    previous.restore();
                }
            }
            complete = true;
            return image;
        } finally {
            if (!complete) {
                image.close();
            }
        }
    }

    private static void save(Minecraft client, NativeImage image, PixelDimensions dimensions, Consumer<Component> feedback) {
        Path destination = null;
        try (image) {
            Path screenshots = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(screenshots);
            // Atomic reservation prevents simultaneous exports from overwriting one another.
            destination = Files.createTempFile(screenshots,
                    "DataEnergistics_CraftingPlan_" + Util.getFilenameFormattedDateTime() + "_", ".png");
            image.writeToFile(destination);
            Path savedDestination = destination;
            Component link = Component.literal(destination.getFileName().toString()).withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
                            savedDestination.toAbsolutePath().toString())));
            Component message = Component.translatable("screenshot.success", link);
            if (dimensions.scale() < PREFERRED_SCALE - 0.000001) {
                message = message.copy().append(" ").append(Component.translatable(
                        "gui.data_energistics.plan_tree.export_scaled", dimensions.width(), dimensions.height()));
            }
            Component result = message;
            client.execute(() -> feedback.accept(result));
        } catch (Exception failure) {
            if (destination != null) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            reportFailure(client, feedback, failure);
        }
    }

    private static void reportFailure(Minecraft client, Consumer<Component> feedback, Exception failure) {
        Data_Energistics.LOGGER.error("Could not export the crafting plan graph as PNG", failure);
        String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        client.execute(() -> feedback.accept(Component.translatable("gui.data_energistics.plan_tree.export_failed", detail)));
    }

    private record PixelDimensions(int width, int height, double scale) {}

    /** Allocation is deliberately deferred to resize(), so partially allocated buffers can always be destroyed. */
    private static final class PngRenderTarget extends RenderTarget {

        private PngRenderTarget() {
            super(true);
        }
    }

    /** Export is nested inside an existing frame; restoring the main target would be wrong for an offscreen caller. */
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
                // RenderSystem in this Minecraft version owns twelve shader texture slots.
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
