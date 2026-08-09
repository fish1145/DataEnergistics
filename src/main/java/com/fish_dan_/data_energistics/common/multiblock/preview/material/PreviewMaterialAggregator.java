package com.fish_dan_.data_energistics.common.multiblock.preview.material;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;

import java.util.List;

/**
 * Aggregates selected placement items without depending on UI, XEI, or world state.
 */
public interface PreviewMaterialAggregator {

    /**
     * Aggregates one item per required concrete cell in first-occurrence order.
     *
     * @param cells complete projected cells
     * @return component-aware exact material amounts
     */
    List<PreviewMaterial> aggregate(List<PreviewCellSnapshot> cells);
}
