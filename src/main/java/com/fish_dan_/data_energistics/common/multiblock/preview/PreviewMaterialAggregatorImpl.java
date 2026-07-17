package com.fish_dan_.data_energistics.common.multiblock.preview;

import appeng.api.stacks.AEItemKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default component-aware material aggregation logic for projected multiblocks.
 */
public final class PreviewMaterialAggregatorImpl implements PreviewMaterialAggregator {

    @Override
    public List<PreviewMaterial> aggregate(List<PreviewCellSnapshot> cells) {
        if (cells == null) {
            throw new IllegalArgumentException("Preview material cells cannot be null");
        }
        Map<AEItemKey, Long> amounts = new LinkedHashMap<>();
        for (PreviewCellSnapshot cell : cells) {
            if (cell == null) {
                throw new IllegalArgumentException("Preview material cells cannot contain null");
            }
            if (!cell.predicate().role().contributesMaterial()) {
                continue;
            }
            PreviewCandidate candidate = cell.predicate().selectedCandidate().orElseThrow(() -> new IllegalStateException("Material cell has no selected preview candidate at " +
                    cell.relativePosition()));
            if (!candidate.concrete()) {
                continue;
            }
            AEItemKey key = candidate.placementKey().orElseThrow();
            amounts.compute(key, (unused, current) -> current == null ? 1L : Math.addExact(current, 1L));
        }
        List<PreviewMaterial> materials = new ArrayList<>(amounts.size());
        for (Map.Entry<AEItemKey, Long> entry : amounts.entrySet()) {
            materials.add(new PreviewMaterial(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(materials);
    }
}
