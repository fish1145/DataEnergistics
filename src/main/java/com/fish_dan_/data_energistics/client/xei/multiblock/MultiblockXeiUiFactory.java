package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

/**
 * Creates independent XEI compositions without exposing JEI, EMI, or REI types to shared preview logic.
 */
public interface MultiblockXeiUiFactory {

    /**
     * Creates the production factory backed by the live multiblock catalog and client scene bridge.
     */
    static MultiblockXeiUiFactory createDefault() {
        return create(
                DEVerticalMultiBlocks.MULTIBLOCK_PREVIEWS,
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
        return new CatalogBackedMultiblockXeiUiFactory(catalog, previewFactory, logicalClient);
    }

    /**
     * Resolves one current catalog revision and creates a fresh independently owned composition.
     *
     * @param controllerId stable multiblock controller id
     * @param idPrefix     unique element-id prefix for the returned UI tree
     */
    default MultiblockXeiComposition create(ResourceLocation controllerId, String idPrefix) {
        return create(controllerId, null, idPrefix);
    }

    /**
     * Resolves one current catalog revision and restores a compatible recipe-affecting selection.
     * A selection from an older definition revision is intentionally reset to the current defaults.
     *
     * @param controllerId      stable multiblock controller id
     * @param retainedSelection last selection retained by the controller-level recipe wrapper, if any
     * @param idPrefix          unique element-id prefix for the returned UI tree
     */
    MultiblockXeiComposition create(ResourceLocation controllerId,
                                    @Nullable PreviewSelection retainedSelection,
                                    String idPrefix);
}
