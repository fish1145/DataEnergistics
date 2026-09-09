package com.fish_dan_.data_energistics.common.multiblock.preview.material;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/**
 * Component-aware material aggregation logic for projected multiblocks.
 */
public final class ComponentAwarePreviewMaterialAggregator implements PreviewMaterialAggregator {

    @Override
    public List<PreviewMaterial> aggregate(List<PreviewCellSnapshot> cells) {
        if (cells == null) {
            throw new IllegalArgumentException("Preview material cells cannot be null");
        }
        Object2LongMap<AEKey> amounts = new Object2LongLinkedOpenHashMap<>();
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
            AEKey key = candidate.placementKey().orElseThrow();
            amounts.mergeLong(key, key instanceof AEFluidKey ? AEFluidKey.AMOUNT_BLOCK : 1, Math::addExact);
        }
        List<PreviewMaterial> materials = new ObjectArrayList<>(amounts.size());
        for (Object2LongMap.Entry<AEKey> entry : amounts.object2LongEntrySet()) {
            materials.add(new PreviewMaterial(entry.getKey(), entry.getLongValue()));
        }
        return List.copyOf(materials);
    }
}
