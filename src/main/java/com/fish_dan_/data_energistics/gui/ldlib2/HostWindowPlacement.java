package com.fish_dan_.data_energistics.gui.ldlib2;

/** Screen-bounded window constraints translated back into the parent-relative LDLib2 layout space. */
record HostWindowPlacement(int maximumWidth, int maximumHeight, float left, float top) {

    private static final int WINDOW_MARGIN = 4;

    /**
     * Clamps an absolute screen rectangle while retaining the layout coordinates expected by its parent.
     *
     * @param screenWidth  current viewport width
     * @param screenHeight current viewport height
     * @param screenX      window's current absolute screen X
     * @param screenY      window's current absolute screen Y
     * @param layoutLeft   window's current parent-relative layout X
     * @param layoutTop    window's current parent-relative layout Y
     * @param windowWidth  current rendered width
     * @param windowHeight current rendered height
     * @return maximum dimensions and corrected parent-relative position
     */
    static HostWindowPlacement clamp(int screenWidth,
                                     int screenHeight,
                                     float screenX,
                                     float screenY,
                                     float layoutLeft,
                                     float layoutTop,
                                     float windowWidth,
                                     float windowHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("Host window viewport must be positive");
        }
        if (windowWidth < 0 || windowHeight < 0) {
            throw new IllegalArgumentException("Host window dimensions must not be negative");
        }
        int horizontalMargin = Math.min(WINDOW_MARGIN, Math.max(0, (screenWidth - 1) / 2));
        int verticalMargin = Math.min(WINDOW_MARGIN, Math.max(0, (screenHeight - 1) / 2));
        int maximumWidth = Math.max(1, screenWidth - horizontalMargin * 2);
        int maximumHeight = Math.max(1, screenHeight - verticalMargin * 2);
        float boundedWidth = Math.min(windowWidth, maximumWidth);
        float boundedHeight = Math.min(windowHeight, maximumHeight);
        float maximumX = Math.max(horizontalMargin, screenWidth - boundedWidth - horizontalMargin);
        float maximumY = Math.max(verticalMargin, screenHeight - boundedHeight - verticalMargin);
        float boundedScreenX = Math.max(horizontalMargin, Math.min(screenX, maximumX));
        float boundedScreenY = Math.max(verticalMargin, Math.min(screenY, maximumY));
        return new HostWindowPlacement(
                maximumWidth,
                maximumHeight,
                layoutLeft + boundedScreenX - screenX,
                layoutTop + boundedScreenY - screenY);
    }
}
