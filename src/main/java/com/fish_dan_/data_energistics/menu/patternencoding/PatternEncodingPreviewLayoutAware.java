package com.fish_dan_.data_energistics.menu.patternencoding;

/**
 * Exposes the upload preview panel layout shared by pattern-encoding menu implementations.
 * <p>
 * The client preference repository owns persistence. Each open menu holds the current offset for rendering and
 * synchronization; callers must use the owning client or server main thread during that menu's lifetime.
 */
public interface PatternEncodingPreviewLayoutAware {

    /**
     * Returns the current menu's horizontal offset from the automatically selected panel anchor.
     */
    int data_energistics$getPreviewPanelOffsetX();

    /**
     * Returns the current menu's vertical offset from the automatically selected panel anchor.
     */
    int data_energistics$getPreviewPanelOffsetY();

    /**
     * Updates the menu offset relative to the automatic anchor, with synchronization handled by the menu.
     * Persistent changes must also be recorded through the client preference repository.
     */
    void data_energistics$setPreviewPanelOffset(int offsetX, int offsetY);

    /**
     * Resets the menu offset to automatic placement without changing the client preference file.
     */
    void data_energistics$resetPreviewPanelOffset();
}
