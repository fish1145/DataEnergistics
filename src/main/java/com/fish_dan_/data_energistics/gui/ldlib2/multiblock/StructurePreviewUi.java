package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

/**
 * Fresh hosted preview elements and session returned by one factory invocation.
 *
 * @param panel   independently owned element tree
 * @param session independently owned selection and projection state
 * @param scene   independently owned physical-side-neutral scene shell contained by {@code panel}
 */
public record StructurePreviewUi(StructurePreviewPanel panel,
                                 StructurePreviewSession session,
                                 StructurePreviewSceneElement scene) {

    /**
     * Verifies that all returned pieces belong to the same newly created preview.
     */
    public StructurePreviewUi {
        if (panel == null || session == null || scene == null) {
            throw new IllegalArgumentException("Structure preview UI arguments cannot be null");
        }
        if (!panel.isAncestorOf(scene)) {
            throw new IllegalArgumentException("Structure preview scene must belong to its returned panel");
        }
        if (panel.session() != session || panel.scene() != scene) {
            throw new IllegalArgumentException("Structure preview UI components do not share one session and scene");
        }
    }
}
