package com.fish_dan_.data_energistics.client.render.item.crossbow.plasma;

import java.util.function.IntBinaryOperator;

/** An emissive hue derived from visible ABGR texture pixels, including all animation frames. */
public record PlasmaPalette(int rgb) {

    public static final PlasmaPalette NEUTRAL = new PlasmaPalette(0xFFFFFF);

    public static PlasmaPalette sample(int width, int height, IntBinaryOperator pixels) {
        double red = 0, green = 0, blue = 0, weight = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pixel = pixels.applyAsInt(x, y);
            int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255, a = pixel >>> 24;
            int maximum = Math.max(r, Math.max(g, b));
            int minimum = Math.min(r, Math.min(g, b));
            // Transparent pixels and dark backgrounds must not drown out the texture's colored energy.
            double w = a * (maximum - minimum + 1.0) * maximum;
            red += r * w;
            green += g * w;
            blue += b * w;
            weight += w;
        }
        if (weight == 0) return new PlasmaPalette(0);
        double maximum = Math.max(red, Math.max(green, blue));
        return new PlasmaPalette((int) Math.round(red * 255 / maximum) << 16 | (int) Math.round(green * 255 / maximum) << 8 | (int) Math.round(blue * 255 / maximum));
    }

    public float red(float whiteMix) {
        return channel(rgb >>> 16 & 255, whiteMix);
    }

    public float green(float whiteMix) {
        return channel(rgb >>> 8 & 255, whiteMix);
    }

    public float blue(float whiteMix) {
        return channel(rgb & 255, whiteMix);
    }

    private static float channel(int value, float whiteMix) {
        return value / 255.0F * (1 - whiteMix) + whiteMix;
    }
}
