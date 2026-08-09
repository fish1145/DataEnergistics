package com.fish_dan_.data_energistics.common.multiblock.preview.model;

/**
 * Pure view-only selection of all projected layers or one logical snapshot layer.
 */
public sealed interface PreviewVisibleLayer permits PreviewVisibleLayer.All, PreviewVisibleLayer.LogicalLayer {

    /**
     * Returns the explicit all-layers selection.
     */
    static PreviewVisibleLayer all() {
        return All.INSTANCE;
    }

    /**
     * Selects one zero-based logical snapshot layer.
     *
     * @param layerIndex logical layer index from a projected snapshot
     * @return immutable single-layer selection
     */
    static PreviewVisibleLayer logicalLayer(int layerIndex) {
        return new LogicalLayer(layerIndex);
    }

    /**
     * Returns whether this selection includes the supplied logical layer.
     *
     * @param layerIndex non-negative logical layer index
     * @return true when the layer is visible
     */
    default boolean includes(int layerIndex) {
        if (layerIndex < 0) {
            throw new IllegalArgumentException("Preview logical layer index cannot be negative: " + layerIndex);
        }
        return this instanceof All || ((LogicalLayer) this).layerIndex() == layerIndex;
    }

    /**
     * Explicit singleton value representing every logical layer.
     */
    enum All implements PreviewVisibleLayer {
        INSTANCE
    }

    /**
     * One zero-based logical layer in snapshot expansion order.
     *
     * @param layerIndex logical layer index
     */
    record LogicalLayer(int layerIndex) implements PreviewVisibleLayer {

        public LogicalLayer {
            if (layerIndex < 0) {
                throw new IllegalArgumentException("Preview logical layer index cannot be negative: " + layerIndex);
            }
        }
    }
}
