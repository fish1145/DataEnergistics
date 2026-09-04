package com.fish_dan_.data_energistics.client.crafting.tree.export;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRenderer;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRouteDrawing;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanSegmentSelection;
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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Fixed-density tiled capture with bounded working memory, independent of the complete PNG's dimensions. */
public final class CraftingPlanGraphPngExport {

    private static final int PIXEL_SCALE = (int) CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE;
    private static final int TILE_SIDE = 2048;
    private static final int TILE_GUARD = 2;
    private static final int TILES_PER_CAPTURE = 8;
    private static final long STRIP_PIXELS = 16L * 1024 * 1024;

    private CraftingPlanGraphPngExport() {}

    /**
     * Captures the exact supplied layout at four pixels per layout unit, never scaled down to a texture budget.
     * Call on the render thread. The IO worker requests bounded capture batches on that thread and streams rows
     * into the PNG; neither thread allocates an image or byte array covering the full result. Feedback returns to
     * the client thread. The immutable graph/layout are retained until completion, even if their screen closes.
     */
    public static void export(CraftingPlanGraph graph, Layout layout, boolean showAmounts, Consumer<Component> feedback) {
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        try {
            PixelDimensions dimensions = dimensions(layout.bounds());
            int tileSide = Math.min(TILE_SIDE, RenderSystem.maxSupportedTextureSize() - TILE_GUARD * 2);
            if (tileSide <= 0) throw new IllegalStateException("The GPU cannot allocate a guarded export tile");
            CraftingPlanGraphRenderer renderer = new CraftingPlanGraphRenderer(graph);
            Util.ioPool().execute(() -> save(client, renderer, layout, dimensions, tileSide, showAmounts, feedback));
            feedback.accept(Component.translatable("gui.data_energistics.plan_tree.export_png_started",
                    dimensions.width(), dimensions.height(), PIXEL_SCALE));
        } catch (Exception failure) {
            reportFailure(client, feedback, failure);
        }
    }

    private static PixelDimensions dimensions(Bounds bounds) {
        double width = Math.ceil(bounds.width() * PIXEL_SCALE);
        double height = Math.ceil(bounds.height() * PIXEL_SCALE);
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(bounds.x()) || !Double.isFinite(bounds.y()) || width < 1 || height < 1 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The full-resolution graph dimensions must fit PNG's positive 31-bit width and height");
        }
        return new PixelDimensions((int) width, (int) height);
    }

    private static void save(Minecraft client, CraftingPlanGraphRenderer renderer, Layout layout,
                             PixelDimensions dimensions, int tileSide, boolean showAmounts, Consumer<Component> feedback) {
        Path destination = null;
        try {
            Path screenshots = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(screenshots);
            destination = Files.createTempFile(screenshots,
                    "DataEnergistics_CraftingPlan_" + Util.getFilenameFormattedDateTime() + "_", ".png");
            try (var output = new BufferedOutputStream(Files.newOutputStream(destination), 65536);
                    var png = new CraftingPlanPngWriter(output, dimensions.width(), dimensions.height())) {
                writeTiles(client, renderer, layout, dimensions, tileSide, showAmounts, png);
                png.finish();
            }
            Path savedDestination = destination;
            Component link = Component.literal(destination.getFileName().toString()).withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
                            savedDestination.toAbsolutePath().toString())));
            Component message = Component.translatable("screenshot.success", link).append(" ")
                    .append(Component.translatable("gui.data_energistics.plan_tree.export_png_resolution",
                            dimensions.width(), dimensions.height(), PIXEL_SCALE));
            client.execute(() -> feedback.accept(message));
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

    private static void writeTiles(Minecraft client, CraftingPlanGraphRenderer renderer, Layout layout,
                                   PixelDimensions dimensions, int maximumTileSide, boolean showAmounts,
                                   CraftingPlanPngWriter png) throws IOException {
        int tileWidth = Math.min(maximumTileSide, dimensions.width());
        int columns = (dimensions.width() - 1) / tileWidth + 1;
        long paddedWidth = (long) columns * (tileWidth + TILE_GUARD * 2);
        int stripRows = Math.clamp(STRIP_PIXELS / paddedWidth - TILE_GUARD * 2, 1, maximumTileSide);
        byte[] row = new byte[tileWidth * 4];
        ObjectArrayList<NativeImage> strip = new ObjectArrayList<>();
        for (int y = 0; y < dimensions.height();) {
            int rows = Math.min(stripRows, dimensions.height() - y);
            int stripY = y;
            try {
                for (int first = 0; first < columns; first += TILES_PER_CAPTURE) {
                    int start = first;
                    int count = Math.min(TILES_PER_CAPTURE, columns - first);
                    ObjectArrayList<NativeImage> batch = client.submit(() -> capture(client, renderer, layout,
                            tileWidth, rows, stripY, start, count, showAmounts)).join();
                    if (rows == 1) {
                        // Even a single row can exceed the strip budget: feed tile segments without retaining it.
                        try {
                            writeRow(png, dimensions.width(), tileWidth, batch, start, 0, row);
                        } finally {
                            batch.forEach(NativeImage::close);
                        }
                    } else {
                        strip.addAll(batch);
                    }
                }
                if (rows > 1) {
                    for (int line = 0; line < rows; line++) writeRow(png, dimensions.width(), tileWidth, strip, 0, line, row);
                }
            } finally {
                strip.forEach(NativeImage::close);
                strip.clear();
            }
            y += rows;
        }
    }

    private static void writeRow(CraftingPlanPngWriter png, int imageWidth, int tileWidth,
                                 ObjectArrayList<NativeImage> tiles, int firstColumn, int y, byte[] row) throws IOException {
        for (int index = 0; index < tiles.size(); index++) {
            int x = (firstColumn + index) * tileWidth;
            int width = Math.min(tileWidth, imageWidth - x);
            CraftingPlanGraphCapture.copyStraightRgbaRow(tiles.get(index), TILE_GUARD, y + TILE_GUARD, width, row);
            png.writeRowSegment(row, 0, width);
        }
    }

    private static ObjectArrayList<NativeImage> capture(Minecraft client, CraftingPlanGraphRenderer renderer,
                                                        Layout layout, int tileWidth, int rows, int y,
                                                        int firstColumn, int count, boolean showAmounts) {
        RenderSystem.assertOnRenderThread();
        ObjectArrayList<NativeImage> images = new ObjectArrayList<>(count);
        try (CraftingPlanGraphCapture capture = new CraftingPlanGraphCapture(tileWidth + TILE_GUARD * 2, rows + TILE_GUARD * 2)) {
            for (int index = 0; index < count; index++) {
                int x = (firstColumn + index) * tileWidth;
                double originX = layout.bounds().x() + (x - TILE_GUARD) / (double) PIXEL_SCALE;
                double originY = layout.bounds().y() + (y - TILE_GUARD) / (double) PIXEL_SCALE;
                GuiGraphics graphics = capture.begin(client, PIXEL_SCALE, -originX, -originY);
                // This is only tile-local clipping; every tile keeps the identical full-layout coordinate system.
                Bounds tile = new Bounds(originX, originY, (tileWidth + TILE_GUARD * 2) / (double) PIXEL_SCALE,
                        (rows + TILE_GUARD * 2) / (double) PIXEL_SCALE);
                renderer.draw(graphics, layout, GraphViewLod.FULL, showAmounts, -1, IntSets.emptySet(),
                        CraftingPlanSegmentSelection.NONE, tile, PIXEL_SCALE, 1, false);
                graphics.flush();
                images.add(capture.download());
            }
        } catch (Throwable failure) {
            images.forEach(NativeImage::close);
            throw failure;
        }
        return images;
    }

    private static void reportFailure(Minecraft client, Consumer<Component> feedback, Exception failure) {
        Data_Energistics.LOGGER.error("Could not export the full-resolution crafting plan graph as PNG", failure);
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        client.execute(() -> feedback.accept(Component.translatable("gui.data_energistics.plan_tree.export_failed", detail)));
    }

    private record PixelDimensions(int width, int height) {}
}
