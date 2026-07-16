package com.fish_dan_.data_energistics.client.ui;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import java.util.function.DoubleSupplier;

/**
 * Draws a fixed texture region as a bottom-up progress bar driven by a strict 0..1 supplier.
 */
public final class DataReassemblerProgressElement extends UIElement {

    private final ResourceLocation texture;
    private final int textureU;
    private final int textureV;
    private final int regionWidth;
    private final int regionHeight;
    private final int textureWidth;
    private final int textureHeight;
    private final DoubleSupplier progressSupplier;

    public DataReassemblerProgressElement(ResourceLocation texture,
                                          int textureU,
                                          int textureV,
                                          int regionWidth,
                                          int regionHeight,
                                          int textureWidth,
                                          int textureHeight,
                                          DoubleSupplier progressSupplier) {
        this.texture = texture;
        this.progressSupplier = progressSupplier;
        if (textureU < 0 || textureV < 0 || regionWidth <= 0 || regionHeight <= 0 || textureWidth <= 0 || textureHeight <= 0 || textureU + regionWidth > textureWidth || textureV + regionHeight > textureHeight) {
            Data_Energistics.LOGGER.error(
                    "Invalid data reassembler progress texture region: uv=({}, {}), region={}x{}, texture={}x{}",
                    textureU,
                    textureV,
                    regionWidth,
                    regionHeight,
                    textureWidth,
                    textureHeight);
            throw new IllegalArgumentException("Progress texture region must fit inside a positive texture");
        }
        this.textureU = textureU;
        this.textureV = textureV;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        getLayout().width(regionWidth);
        getLayout().height(regionHeight);
    }

    /**
     * Reads and validates the current progress value for rendering and direct logic tests.
     */
    public double progress() {
        double progress = this.progressSupplier.getAsDouble();
        if (!Double.isFinite(progress) || progress < 0.0D || progress > 1.0D) {
            Data_Energistics.LOGGER.error("Data reassembler progress supplier returned an invalid value: {}", progress);
            throw new IllegalStateException("Progress supplier must return a finite value from 0 to 1: " + progress);
        }
        return progress;
    }

    /** Returns the bottom-up fill height using the same truncation as AE2's integer progress calculation. */
    public int filledPixels() {
        return (int) Math.floor(progress() * this.regionHeight);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        int filled = filledPixels();
        if (filled == 0) {
            return;
        }

        int x = Math.round(getPositionX());
        int yOffset = this.regionHeight - filled;
        int y = Math.round(getPositionY()) + yOffset;
        guiContext.graphics.blit(
                this.texture,
                x,
                y,
                0,
                (float) this.textureU,
                (float) (this.textureV + yOffset),
                this.regionWidth,
                filled,
                this.textureWidth,
                this.textureHeight);
    }
}
