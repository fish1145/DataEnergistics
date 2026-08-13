package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.StructurePreviewSnapshot;

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

    /**
     * Recreates this preview through an off-screen render target whose final texture is constrained to the scene
     * element. Callers use this only for compositions where neighboring authored controls must never be overwritten
     * by the immediate world renderer.
     *
     * @param width  positive off-screen width matching the authored scene aspect ratio
     * @param height positive off-screen height matching the authored scene aspect ratio
     */
    void constrainToViewport(int width, int height);

    /**
     * Attempts every resource cleanup owned by this exact scene binding once. Repeated calls are no-ops, including
     * after the first call reports an aggregated cleanup failure.
     */
    void release();
}
