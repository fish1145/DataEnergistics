package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewLayerSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewVisibleLayer;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;

import net.minecraft.core.BlockPos;

import appeng.api.stacks.AEItemKey;
import com.modularmc.mdl.api.multiblock.PatternBounds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Complete immutable common-layer result for one active substructure selection.
 *
 * @param selection     full session selection retaining all substructure parameters
 * @param definitionKey active named definition
 * @param layers        complete logical layers in expanded order
 * @param cells         flattened cells in layer order
 * @param bounds        exact inclusive controller-relative bounds
 * @param materials     selected material inputs in first-occurrence order
 */
public record StructurePreviewSnapshot(PreviewSelection selection,
                                       JsonMultiBlockStructureKey definitionKey,
                                       List<PreviewLayerSnapshot> layers,
                                       List<PreviewCellSnapshot> cells,
                                       PatternBounds bounds,
                                       List<PreviewMaterial> materials) {

    /**
     * Copies collections and verifies the flattened structure, ownership, layer indexes, and exact bounds.
     */
    public StructurePreviewSnapshot {
        if (selection == null || definitionKey == null || layers == null || cells == null || bounds == null ||
                materials == null) {
            throw new IllegalArgumentException("Structure preview snapshot arguments cannot be null");
        }
        layers = List.copyOf(layers);
        cells = List.copyOf(cells);
        materials = List.copyOf(materials);
        if (!definitionKey.machineId().equals(selection.controllerId()) ||
                !definitionKey.structureName().equals(selection.activeSubstructureId())) {
            throw new IllegalArgumentException("Structure preview definition does not match the active selection");
        }
        validateContents(layers, cells, bounds);
        Set<AEItemKey> materialKeys = new HashSet<>();
        for (PreviewMaterial material : materials) {
            if (!materialKeys.add(material.key())) {
                throw new IllegalArgumentException("Structure preview materials contain a duplicate item key");
            }
        }
    }

    /**
     * Returns the definition generation used to build every field in this snapshot.
     */
    public long definitionRevision() {
        return this.selection.definitionRevision();
    }

    /**
     * Selects layers for rendering without rebuilding cells, materials, bounds, or recipe identity.
     *
     * @param viewState view-only logical layer selection
     * @return all layers or one existing logical layer
     */
    public List<PreviewLayerSnapshot> visibleLayers(PreviewViewState viewState) {
        if (viewState == null) {
            throw new IllegalArgumentException("Structure preview visible layers require view state");
        }
        PreviewVisibleLayer visibleLayer = viewState.visibleLayer();
        if (visibleLayer instanceof PreviewVisibleLayer.All) {
            return this.layers;
        }
        int layerIndex = ((PreviewVisibleLayer.LogicalLayer) visibleLayer).layerIndex();
        if (layerIndex >= this.layers.size()) {
            throw new IllegalArgumentException("Preview logical layer index " + layerIndex + " is outside 0.." +
                    (this.layers.size() - 1));
        }
        return List.of(this.layers.get(layerIndex));
    }

    private static void validateContents(List<PreviewLayerSnapshot> layers,
                                         List<PreviewCellSnapshot> cells,
                                         PatternBounds bounds) {
        if (layers.isEmpty() || cells.isEmpty()) {
            throw new IllegalArgumentException("Structure preview snapshot requires layers and cells");
        }
        int flattenedIndex = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            PreviewLayerSnapshot layer = layers.get(layerIndex);
            if (layer.index() != layerIndex) {
                throw new IllegalArgumentException("Structure preview layer indexes must be contiguous from zero");
            }
            for (PreviewCellSnapshot cell : layer.cells()) {
                if (flattenedIndex >= cells.size() || !cell.equals(cells.get(flattenedIndex))) {
                    throw new IllegalArgumentException("Structure preview flattened cells must match layer order");
                }
                BlockPos position = cell.relativePosition();
                minX = Math.min(minX, position.getX());
                minY = Math.min(minY, position.getY());
                minZ = Math.min(minZ, position.getZ());
                maxX = Math.max(maxX, position.getX());
                maxY = Math.max(maxY, position.getY());
                maxZ = Math.max(maxZ, position.getZ());
                flattenedIndex++;
            }
        }
        if (flattenedIndex != cells.size()) {
            throw new IllegalArgumentException("Structure preview flattened cells contain entries outside its layers");
        }
        PatternBounds actualBounds = new PatternBounds(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ));
        if (!actualBounds.equals(bounds)) {
            throw new IllegalArgumentException("Structure preview bounds must exactly cover every projected cell");
        }
    }
}
