package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;

import net.minecraft.resources.ResourceLocation;

/**
 * Default catalog-backed implementation of the platform-neutral XEI composition factory.
 */
final class MultiblockXeiUiFactoryImpl implements MultiblockXeiUiFactory {

    private final MultiblockPreviewCatalog catalog;
    private final StructurePreviewUiFactory previewFactory;
    private final boolean logicalClient;

    MultiblockXeiUiFactoryImpl(MultiblockPreviewCatalog catalog,
                               StructurePreviewUiFactory previewFactory,
                               boolean logicalClient) {
        if (catalog == null || previewFactory == null) {
            throw new IllegalArgumentException("Multiblock XEI factory arguments cannot be null");
        }
        this.catalog = catalog;
        this.previewFactory = previewFactory;
        this.logicalClient = logicalClient;
    }

    @Override
    public MultiblockXeiComposition create(ResourceLocation controllerId, String idPrefix) {
        if (controllerId == null || idPrefix == null || idPrefix.isBlank()) {
            throw new IllegalArgumentException("Multiblock XEI composition arguments cannot be null or blank");
        }
        MultiblockPreviewCatalogSnapshot snapshot = this.catalog.snapshot();
        MultiblockPreviewSpec spec = snapshot.require(controllerId);
        return new MultiblockXeiComposition(
                this.catalog,
                spec,
                PreviewSelection.initial(spec),
                this.previewFactory,
                this.logicalClient,
                idPrefix);
    }
}
