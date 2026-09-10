package com.fish_dan_.data_energistics.orbital.control.ui.layout;

/** Pure GUI-space layout. Narrow windows show one workspace page; wide windows keep the map and form together. */
public record OrbitalTerminalLayout(int width, int height, boolean wide, int bodyTop, int bodyHeight,
                                    int listWidth, int mapLeft, int mapWidth, int formLeft, int formWidth) {

    public static OrbitalTerminalLayout forViewport(int viewportWidth, int viewportHeight) {
        if (viewportWidth < 1 || viewportHeight < 1) {
            throw new IllegalArgumentException("A terminal viewport must have positive dimensions");
        }
        int width = Math.max(1, Math.min(900, viewportWidth - Math.min(16, viewportWidth - 1)));
        int height = Math.max(1, Math.min(540, viewportHeight - Math.min(16, viewportHeight - 1)));
        boolean wide = width >= 660;
        int bodyTop = 54;
        int bodyHeight = Math.max(1, height - bodyTop - 52);
        int listWidth = wide ? 132 : width - 12;
        int mapLeft = wide ? listWidth + 10 : 6;
        int formWidth = 242;
        int formLeft = wide ? width - formWidth - 6 : 6;
        int mapWidth = wide ? formLeft - mapLeft - 4 : width - 12;
        return new OrbitalTerminalLayout(width, height, wide, bodyTop, bodyHeight,
                listWidth, mapLeft, mapWidth, formLeft, wide ? formWidth : width - 12);
    }
}
