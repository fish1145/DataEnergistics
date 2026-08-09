package com.fish_dan_.data_energistics.common.multiblock.preview.model;

/**
 * Immutable common view state kept outside structure selection, projection, materials, and recipe identity.
 *
 * @param visibleLayer all layers or one logical snapshot layer
 */
public record PreviewViewState(PreviewVisibleLayer visibleLayer) {

    public PreviewViewState {
        if (visibleLayer == null) {
            throw new IllegalArgumentException("Preview visible layer cannot be null");
        }
    }

    /**
     * Creates the default all-layers view.
     */
    public static PreviewViewState initial() {
        return new PreviewViewState(PreviewVisibleLayer.all());
    }

    /**
     * Returns a view showing every logical layer.
     */
    public PreviewViewState showAllLayers() {
        return new PreviewViewState(PreviewVisibleLayer.all());
    }

    /**
     * Returns a view showing one logical snapshot layer.
     *
     * @param layerIndex zero-based logical layer index
     */
    public PreviewViewState showLogicalLayer(int layerIndex) {
        return new PreviewViewState(PreviewVisibleLayer.logicalLayer(layerIndex));
    }
}
