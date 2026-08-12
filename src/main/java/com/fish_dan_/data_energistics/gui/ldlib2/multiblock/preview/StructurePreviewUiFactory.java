package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Stateless entry point shared by hosted windows and future XEI adapters while returning fresh runtime state.
 */
public interface StructurePreviewUiFactory {

    /**
     * Creates the production factory backed by the current preview catalog and logical-client bridge.
     */
    static StructurePreviewUiFactory createDefault() {
        return CatalogBackedStructurePreviewUiFactory.createDefault();
    }

    /**
     * Creates a factory with an explicit scene binder for isolated platform adapters and direct behavior tests.
     *
     * @param sceneBinder binder invoked only when a caller identifies the logical menu side as client
     * @return stateless fresh-preview factory
     */
    static StructurePreviewUiFactory create(StructurePreviewSceneBinder sceneBinder) {
        return CatalogBackedStructurePreviewUiFactory.create(sceneBinder);
    }

    /**
     * Creates a fresh session, projection, recipe view, element tree, and scene shell for one fixed substructure.
     *
     * @param controllerId  controller whose current catalog snapshot must be resolved
     * @param structureKey  stable named structure fixed for this preview
     * @param idPrefix      non-blank element id namespace unique within the owning UI
     * @param logicalClient whether the menu player's current level is the logical client
     * @return fresh independently owned preview UI
     */
    default StructurePreviewUi create(ResourceLocation controllerId,
                                      String structureKey,
                                      String idPrefix,
                                      boolean logicalClient) {
        return create(controllerId, structureKey, idPrefix, logicalClient, StructurePreviewPresentation.HOSTED);
    }

    /**
     * Creates a fresh fixed-substructure preview in the requested shared composition.
     *
     * @param controllerId  controller whose current catalog snapshot must be resolved
     * @param structureKey  stable named structure fixed for this preview
     * @param idPrefix      non-blank element id namespace unique within the owning UI
     * @param logicalClient whether the menu player's current level is the logical client
     * @param presentation  hosted or compact XEI composition
     * @return fresh independently owned preview UI
     */
    StructurePreviewUi create(ResourceLocation controllerId,
                              String structureKey,
                              String idPrefix,
                              boolean logicalClient,
                              StructurePreviewPresentation presentation);

    /**
     * Creates a fresh preview from one already resolved catalog generation and retained multi-structure selection.
     *
     * @param spec                 revision-bound preview definition
     * @param initialSelection     complete initial selection belonging to {@code spec}
     * @param allowedStructureKeys ordered structures this UI may activate
     * @param idPrefix             non-blank element id namespace unique within the owning UI
     * @param logicalClient        whether the menu player's current level is the logical client
     * @return fresh independently owned preview UI
     */
    default StructurePreviewUi create(MultiblockPreviewSpec spec,
                                      PreviewSelection initialSelection,
                                      List<String> allowedStructureKeys,
                                      String idPrefix,
                                      boolean logicalClient) {
        return create(
                spec,
                initialSelection,
                allowedStructureKeys,
                idPrefix,
                logicalClient,
                StructurePreviewPresentation.HOSTED);
    }

    /**
     * Creates a fresh retained-selection preview in the requested shared composition.
     *
     * @param spec                 revision-bound preview definition
     * @param initialSelection     complete initial selection belonging to {@code spec}
     * @param allowedStructureKeys ordered structures this UI may activate
     * @param idPrefix             non-blank element id namespace unique within the owning UI
     * @param logicalClient        whether the menu player's current level is the logical client
     * @param presentation         hosted or compact XEI composition
     * @return fresh independently owned preview UI
     */
    StructurePreviewUi create(MultiblockPreviewSpec spec,
                              PreviewSelection initialSelection,
                              List<String> allowedStructureKeys,
                              String idPrefix,
                              boolean logicalClient,
                              StructurePreviewPresentation presentation);
}
