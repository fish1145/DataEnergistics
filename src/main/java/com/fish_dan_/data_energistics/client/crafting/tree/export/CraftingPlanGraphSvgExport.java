package com.fish_dan_.data_energistics.client.crafting.tree.export;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Vector layout export, with native item/fluid/custom-key icons embedded as transparent PNG textures. */
public final class CraftingPlanGraphSvgExport {

    private static final int ICON_SIZE = 64;
    private static final int ICON_BATCH_SIZE = 32;
    private static final long MAX_ICON_PNG_BYTES = 32L * 1024 * 1024;

    private CraftingPlanGraphSvgExport() {}

    /**
     * Exports the exact supplied layout with no viewport clipping, geometric downscaling or selection highlights.
     * Call on the render thread. Font metrics are captured immediately; bounded icon batches return to that thread,
     * while PNG encoding, SVG serialization and filesystem operations run on the IO pool. Feedback returns to the
     * client thread. Graph/layout may be retained until completion; custom AEKey rendering failures fail the export.
     */
    public static void export(CraftingPlanGraph graph, Layout layout, boolean showAmounts, Consumer<Component> feedback) {
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        try {
            CraftingPlanGraphSvgWriter document = new CraftingPlanGraphSvgWriter(graph, layout, showAmounts);
            Util.ioPool().execute(() -> save(client, document, feedback));
        } catch (Exception failure) {
            reportFailure(client, feedback, failure);
        }
    }

    private static void save(Minecraft client, CraftingPlanGraphSvgWriter document, Consumer<Component> feedback) {
        Path destination = null;
        try {
            Path screenshots = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(screenshots);
            destination = Files.createTempFile(screenshots,
                    "DataEnergistics_CraftingPlan_" + Util.getFilenameFormattedDateTime() + "_", ".svg");
            try (BufferedWriter output = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
                document.begin(output);
                List<AEKey> keys = document.keys();
                long encodedBytes = 0;
                for (int first = 0; first < keys.size(); first += ICON_BATCH_SIZE) {
                    int start = first;
                    int end = Math.min(first + ICON_BATCH_SIZE, keys.size());
                    // Only the worker waits. One batch uses one target/state backup and at most 512 KiB of raw RGBA.
                    try (IconBatch batch = client.submit(() -> capture(client, keys, start, end)).join()) {
                        for (int index = 0; index < batch.images().size(); index++) {
                            NativeImage image = batch.images().get(index);
                            CraftingPlanGraphCapture.unpremultiplyAlpha(image);
                            byte[] png = image.asByteArray();
                            encodedBytes += png.length;
                            if (encodedBytes > MAX_ICON_PNG_BYTES) {
                                throw new IOException("Embedded crafting icons exceed the 32 MiB PNG budget");
                            }
                            output.write("<image id=\"icon-" + (start + index) + "\" width=\"16\" height=\"16\" xlink:href=\"data:image/png;base64,");
                            output.write(Base64.getEncoder().encodeToString(png));
                            output.write("\"/>\n");
                        }
                    }
                }
                document.finish(output);
            }
            Path savedDestination = destination;
            Component link = Component.literal(destination.getFileName().toString()).withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
                            savedDestination.toAbsolutePath().toString())));
            client.execute(() -> feedback.accept(Component.translatable("gui.data_energistics.plan_tree.export_svg_success", link)));
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

    private static IconBatch capture(Minecraft client, List<AEKey> keys, int start, int end) {
        RenderSystem.assertOnRenderThread();
        List<NativeImage> images = new ObjectArrayList<>(end - start);
        try (CraftingPlanGraphCapture capture = new CraftingPlanGraphCapture(ICON_SIZE, ICON_SIZE)) {
            for (int index = start; index < end; index++) {
                GuiGraphics graphics = capture.begin(client, ICON_SIZE / 16D, 0, 0);
                AEKeyRendering.drawInGui(client, graphics, 0, 0, keys.get(index));
                graphics.flush();
                images.add(capture.download());
            }
        } catch (Throwable failure) {
            images.forEach(NativeImage::close);
            throw failure;
        }
        return new IconBatch(images);
    }

    private static void reportFailure(Minecraft client, Consumer<Component> feedback, Exception failure) {
        Data_Energistics.LOGGER.error("Could not export the crafting plan graph as SVG", failure);
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        client.execute(() -> feedback.accept(Component.translatable("gui.data_energistics.plan_tree.export_svg_failed", detail)));
    }

    private record IconBatch(List<NativeImage> images) implements AutoCloseable {

        @Override
        public void close() {
            this.images.forEach(NativeImage::close);
        }
    }
}
