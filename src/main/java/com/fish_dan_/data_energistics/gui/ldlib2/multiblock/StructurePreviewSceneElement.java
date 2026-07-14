package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;

/**
 * Double-sided scene shell whose renderer and dummy world are installed only on the logical client.
 */
public class StructurePreviewSceneElement extends Scene {

    /**
     * Clears transient block interaction state before a snapshot or logical-layer replacement.
     */
    public void clearSelection() {
        this.dragging = false;
        this.lastClickPosFace = null;
        this.lastHoverPosFace = null;
        this.lastSelectedPosFace = null;
        this.lastHoverItem = null;
    }
}
