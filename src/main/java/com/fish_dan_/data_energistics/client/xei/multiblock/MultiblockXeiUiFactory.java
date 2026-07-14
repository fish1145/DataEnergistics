package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.resources.ResourceLocation;

/**
 * Creates independent XEI compositions without exposing JEI, EMI, or REI types to shared preview logic.
 */
public interface MultiblockXeiUiFactory {

    /**
     * Creates the production factory backed by the live multiblock catalog and client scene bridge.
     */
    static MultiblockXeiUiFactory createDefault() {
        return create(
                ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS,
                StructurePreviewUiFactory.createDefault(),
                true);
    }

    /**
     * Creates an injected factory for direct behavior tests and future platform adapters.
     *
     * @param catalog        live definition-revision boundary
     * @param previewFactory shared LDLib2 preview/session factory
     * @param logicalClient  whether each composition should bind a physical client Scene
     */
    static MultiblockXeiUiFactory create(MultiblockPreviewCatalog catalog,
                                         StructurePreviewUiFactory previewFactory,
                                         boolean logicalClient) {
        return new MultiblockXeiUiFactoryImpl(catalog, previewFactory, logicalClient);
    }

    /**
     * Resolves one current catalog revision and creates a fresh independently owned composition.
     *
     * @param controllerId stable multiblock controller id
     * @param idPrefix     unique element-id prefix for the returned UI tree
     */
    MultiblockXeiComposition create(ResourceLocation controllerId, String idPrefix);
}
