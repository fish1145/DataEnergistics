package com.fish_dan_.data_energistics.menu.patternencoding;

/**
 * Stores the upload preview panel layout for the concrete pattern terminal host.
 * <p>
 * Regular pattern terminals persist this data with their panel logic NBT, while
 * universal/wireless terminals persist it in the terminal item/part data.
 */
public interface PatternEncodingPreviewLayoutAware {

    /**
     * Returns the stored horizontal offset from the automatically selected panel anchor.
     */
    int data_energistics$getPreviewPanelOffsetX();

    /**
     * Returns the stored vertical offset from the automatically selected panel anchor.
     */
    int data_energistics$getPreviewPanelOffsetY();

    /**
     * Updates and persists the panel offset relative to the automatic anchor.
     */
    void data_energistics$setPreviewPanelOffset(int offsetX, int offsetY);

    /**
     * Clears the persisted panel offset, returning the panel to automatic placement.
     */
    void data_energistics$resetPreviewPanelOffset();
}
