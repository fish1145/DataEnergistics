package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;

/**
 * Client rendering lifetime bound to one independently owned structure preview scene.
 */
public interface StructurePreviewSceneBinding {

    /**
     * Applies a projected structure or changes only its visible logical layer when the snapshot is unchanged.
     *
     * @param snapshot  exact immutable structure projection
     * @param viewState client-local logical-layer view
     */
    void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState);
}
