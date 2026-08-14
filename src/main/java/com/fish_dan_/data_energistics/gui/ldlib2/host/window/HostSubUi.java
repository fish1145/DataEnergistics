package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/**
 * Fresh element tree and drag surface produced for one opening of a hosted child UI.
 */
public record HostSubUi(HostSubUiRoot root, UIElement dragSurface) {

    /**
     * Validates that the drag surface is the returned root or one of its descendants.
     */
    public HostSubUi {
        if (root == null) {
            throw new IllegalArgumentException("Host sub UI root must not be null");
        }
        if (dragSurface == null) {
            throw new IllegalArgumentException("Host sub UI drag surface must not be null");
        }
        if (root != dragSurface && !root.isAncestorOf(dragSurface)) {
            throw new IllegalArgumentException("Host sub UI drag surface must belong to its root element tree");
        }
    }
}
