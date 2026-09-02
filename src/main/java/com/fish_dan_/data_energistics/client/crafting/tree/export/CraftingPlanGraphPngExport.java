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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphViewLod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntSets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Full-layout capture on the render thread, with PNG encoding and filesystem work on Minecraft's IO pool. */
public final class CraftingPlanGraphPngExport {

    private static final int MAX_SIDE = 8192;
    private static final long MAX_PIXELS = 16L * 1024 * 1024;
    private static final double PREFERRED_SCALE = 4;

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
        NativeImage image = null;
        try (CraftingPlanGraphCapture capture = new CraftingPlanGraphCapture(dimensions.width(), dimensions.height())) {
            GuiGraphics graphics = capture.begin(client, dimensions.scale(), -layout.bounds().x(), -layout.bounds().y());
            new CraftingPlanGraphRenderer(graph).draw(graphics, layout, GraphViewLod.FULL, showAmounts,
                    -1, IntSets.emptySet(), null, (float) dimensions.scale());
            graphics.flush();
            image = capture.download();
        } catch (Throwable failure) {
            if (image != null) image.close();
            throw failure;
        }
        return image;
    }

    private static void save(Minecraft client, NativeImage image, PixelDimensions dimensions, Consumer<Component> feedback) {
        Path destination = null;
        try (image) {
            Path screenshots = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(screenshots);
            // Atomic reservation prevents simultaneous exports from overwriting one another.
            destination = Files.createTempFile(screenshots,
                    "DataEnergistics_CraftingPlan_" + Util.getFilenameFormattedDateTime() + "_", ".png");
            CraftingPlanGraphCapture.unpremultiplyAlpha(image);
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
}
