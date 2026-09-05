package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.MdlibNorthFacingStructurePreviewProjection;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Supplier;

/**
 * Production factory implementation that resolves one atomic catalog generation per invocation.
 */
final class CatalogBackedStructurePreviewUiFactory implements StructurePreviewUiFactory {

    private final Supplier<StructurePreviewSceneBinder> sceneBinder;

    private CatalogBackedStructurePreviewUiFactory(Supplier<StructurePreviewSceneBinder> sceneBinder) {
        this.sceneBinder = sceneBinder;
    }

    static StructurePreviewUiFactory createDefault() {
        return new CatalogBackedStructurePreviewUiFactory(
                () -> DataEnergisticsClientBridgeAccess.get().structurePreviewSceneBinder());
    }

    static StructurePreviewUiFactory create(StructurePreviewSceneBinder sceneBinder) {
        return new CatalogBackedStructurePreviewUiFactory(() -> sceneBinder);
    }

    @Override
    public StructurePreviewUi create(ResourceLocation controllerId,
                                     String structureKey,
                                     String idPrefix,
                                     boolean logicalClient) {
        if (structureKey.isBlank() || idPrefix.isBlank()) {
            throw new IllegalArgumentException("Structure preview UI factory strings cannot be blank");
        }
        try {
            MultiblockPreviewSpec spec = DEVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot().require(controllerId);
            PreviewSelection selection = PreviewSelection.initial(spec).select(structureKey);
            return createResolved(spec, selection, List.of(structureKey), idPrefix, logicalClient);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to create structure preview UI for {} structure {}",
                    controllerId,
                    structureKey,
                    failure);
            throw failure;
        }
    }

    @Override
    public StructurePreviewUi create(MultiblockPreviewSpec spec,
                                     PreviewSelection initialSelection,
                                     List<String> allowedStructureKeys,
                                     String idPrefix,
                                     boolean logicalClient) {
        if (idPrefix.isBlank()) {
            throw new IllegalArgumentException("Structure preview UI factory id prefix cannot be blank");
        }
        try {
            return createResolved(
                    spec,
                    initialSelection,
                    allowedStructureKeys,
                    idPrefix,
                    logicalClient);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to create structure preview UI for {} structure {}",
                    spec.controllerId(),
                    initialSelection.activeSubstructureId(),
                    failure);
            throw failure;
        }
    }

    private StructurePreviewUi createResolved(MultiblockPreviewSpec spec,
                                              PreviewSelection initialSelection,
                                              List<String> allowedStructureKeys,
                                              String idPrefix,
                                              boolean logicalClient) {
        StructurePreviewSession session = new ProjectedStructurePreviewSession(
                spec,
                initialSelection,
                allowedStructureKeys,
                new MdlibNorthFacingStructurePreviewProjection());
        StructurePreviewPanel panel = new StructurePreviewPanel(idPrefix, session);
        if (logicalClient) {
            StructurePreviewSceneBinding binding = this.sceneBinder.get().bind(
                    panel.scene(),
                    (position, direction) -> panel.selectBlock(position));
            try {
                panel.bindScene(binding);
            } catch (RuntimeException | Error failure) {
                try {
                    binding.release();
                } catch (RuntimeException | Error releaseFailure) {
                    if (failure != releaseFailure) {
                        failure.addSuppressed(releaseFailure);
                    }
                }
                throw failure;
            }
        }
        return new StructurePreviewUi(panel, session, panel.scene());
    }
}
