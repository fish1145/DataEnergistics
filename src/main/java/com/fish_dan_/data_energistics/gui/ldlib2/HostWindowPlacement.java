package com.fish_dan_.data_energistics.gui.ldlib2;

import java.util.List;

/** Screen-bounded window constraints translated back into the parent-relative LDLib2 layout space. */
record HostWindowPlacement(int maximumWidth, int maximumHeight, float left, float top) {

    private static final int WINDOW_MARGIN = 4;
    private static final int INITIAL_CASCADE_STEP = 7;

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

    /**
     * Cascades a fresh default position only when it would hide every reachable edge of a lower window.
     *
     * <p>
     * Saved or user-dragged positions intentionally bypass this policy in the host. The seven-pixel step leaves
     * at least five pixels of a two-pixel-inset title handle reachable at Minecraft's minimum 320x240 GUI size.
     * </p>
     */
    static ScreenPosition cascadeInitial(int screenWidth,
                                         int screenHeight,
                                         float preferredScreenX,
                                         float preferredScreenY,
                                         float windowWidth,
                                         float windowHeight,
                                         List<ScreenBounds> lowerWindows) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("Host window viewport must be positive");
        }
        if (windowWidth < 0 || windowHeight < 0) {
            throw new IllegalArgumentException("Host window dimensions must not be negative");
        }
        if (lowerWindows == null) {
            throw new IllegalArgumentException("Lower host window positions cannot be null");
        }
        for (ScreenBounds lowerWindow : lowerWindows) {
            if (lowerWindow == null) {
                throw new IllegalArgumentException("Lower host window positions cannot contain null");
            }
        }

        int horizontalMargin = Math.min(WINDOW_MARGIN, Math.max(0, (screenWidth - 1) / 2));
        int verticalMargin = Math.min(WINDOW_MARGIN, Math.max(0, (screenHeight - 1) / 2));
        float boundedWidth = Math.min(windowWidth, Math.max(1, screenWidth - horizontalMargin * 2));
        float boundedHeight = Math.min(windowHeight, Math.max(1, screenHeight - verticalMargin * 2));
        float maximumX = Math.max(horizontalMargin, screenWidth - boundedWidth - horizontalMargin);
        float maximumY = Math.max(verticalMargin, screenHeight - boundedHeight - verticalMargin);
        ScreenPosition preferred = new ScreenPosition(
                Math.max(horizontalMargin, Math.min(preferredScreenX, maximumX)),
                Math.max(verticalMargin, Math.min(preferredScreenY, maximumY)));
        if (keepsLowerWindowsReachable(preferred, boundedWidth, boundedHeight, lowerWindows)) {
            return preferred;
        }

        float longestRange = Math.max(maximumX - horizontalMargin, maximumY - verticalMargin);
        int candidateCount = (int) Math.ceil(longestRange / INITIAL_CASCADE_STEP) + 1;
        for (int index = 0; index < candidateCount; index++) {
            ScreenPosition candidate = new ScreenPosition(
                    Math.min(maximumX, horizontalMargin + index * INITIAL_CASCADE_STEP),
                    Math.min(maximumY, verticalMargin + index * INITIAL_CASCADE_STEP));
            if (keepsLowerWindowsReachable(candidate, boundedWidth, boundedHeight, lowerWindows)) {
                return candidate;
            }
        }
        return preferred;
    }

    private static boolean keepsLowerWindowsReachable(
                                                      ScreenPosition candidate,
                                                      float candidateWidth,
                                                      float candidateHeight,
                                                      List<ScreenBounds> lowerWindows) {
        float candidateRight = candidate.left() + candidateWidth;
        float candidateBottom = candidate.top() + candidateHeight;
        for (ScreenBounds lowerWindow : lowerWindows) {
            float lowerRight = lowerWindow.left() + lowerWindow.width();
            float lowerBottom = lowerWindow.top() + lowerWindow.height();
            boolean overlaps = candidate.left() < lowerRight && candidateRight > lowerWindow.left() &&
                    candidate.top() < lowerBottom && candidateBottom > lowerWindow.top();
            if (!overlaps) {
                continue;
            }
            boolean exposesLeftTitleStrip = candidate.left() >= lowerWindow.left() + INITIAL_CASCADE_STEP;
            boolean exposesRightTitleStrip = candidateRight <= lowerRight - INITIAL_CASCADE_STEP;
            boolean exposesTopTitleStrip = candidate.top() >= lowerWindow.top() + INITIAL_CASCADE_STEP;
            if (!exposesLeftTitleStrip && !exposesRightTitleStrip && !exposesTopTitleStrip) {
                return false;
            }
        }
        return true;
    }

    /** Absolute screen position used only while resolving a fresh window's default placement. */
    record ScreenPosition(float left, float top) {}

    /** Absolute bounds of a lower hosted window that must retain an accessible title strip. */
    record ScreenBounds(float left, float top, float width, float height) {

        ScreenBounds {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Lower host window dimensions must not be negative");
            }
        }
    }
}
