package com.fish_dan_.data_energistics.gui.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** Fresh element tree and title drag handle produced for one opening of a hosted child UI. */
public record HostSubUi(HostSubUiRoot root, UIElement dragHandle) {

    /** Validates that the drag handle belongs exclusively to the returned element tree. */
    public HostSubUi {
        if (root == null) {
            throw new IllegalArgumentException("Host sub UI root must not be null");
        }
        if (dragHandle == null) {
            throw new IllegalArgumentException("Host sub UI drag handle must not be null");
        }
        if (root == dragHandle) {
            throw new IllegalArgumentException("Host sub UI drag handle must be a dedicated child element");
        }
        if (!root.isAncestorOf(dragHandle)) {
            throw new IllegalArgumentException("Host sub UI drag handle must belong to its root element tree");
        }
    }
}
