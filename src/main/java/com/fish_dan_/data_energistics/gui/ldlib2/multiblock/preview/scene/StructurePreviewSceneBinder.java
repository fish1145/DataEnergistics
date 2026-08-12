package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.BiConsumer;

/**
 * Client-distribution entry point that attaches rendering resources to an already-owned common scene element.
 */
public interface StructurePreviewSceneBinder {

    /**
     * Creates a fresh dummy world and rendering lifetime for one scene instance.
     *
     * @param scene             common scene already attached beneath its resource-owning root
     * @param selectionConsumer client-local selected block callback
     * @return independent binding for snapshot and layer refreshes
     */
    StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                      BiConsumer<BlockPos, Direction> selectionConsumer);
}
