package com.fish_dan_.data_energistics.client.hud.orbital.layout;

/** GUI-space placement shared by HUD rendering and dragging, including fitting oversized scales to a small window. */
public record OrbitalHudPlacement(int x, int y, int width, int height, float scale) {

    public static OrbitalHudPlacement resolve(int viewportWidth, int viewportHeight, int baseWidth, int baseHeight,
                                              float requestedScale, boolean right, boolean bottom, int offsetX, int offsetY) {
        if (viewportWidth < 1 || viewportHeight < 1 || baseWidth < 1 || baseHeight < 1) {
            throw new IllegalArgumentException("HUD and viewport dimensions must be positive");
        }
        float scale = Math.min(requestedScale, Math.min((float) viewportWidth / baseWidth, (float) viewportHeight / baseHeight));
        int width = Math.min(viewportWidth, Math.round(baseWidth * scale));
        int height = Math.min(viewportHeight, Math.round(baseHeight * scale));
        int x = right ? viewportWidth - width - offsetX : offsetX;
        int y = bottom ? viewportHeight - height - offsetY : offsetY;
        return new OrbitalHudPlacement(Math.clamp(x, 0, viewportWidth - width),
                Math.clamp(y, 0, viewportHeight - height), width, height, scale);
    }
}
