package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewVisibleLayer;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUi;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;

/**
 * Maps ALL plus every exact logical layer onto one editor-authored horizontal scroller.
 */
final class AutoBuildLayerScroller {

    private final StructurePreviewUi preview;
    private final Scroller.Horizontal scroller;
    private boolean refreshing;

    AutoBuildLayerScroller(StructurePreviewUi preview, Scroller.Horizontal scroller) {
        this.preview = preview;
        this.scroller = scroller;
        AuthoredScrollerThumbSize.bind(scroller);
        scroller.setRange(0.0F, 1.0F);
        scroller.setOnValueChanged(ignored -> selectFromScroller());
    }

    void refresh() {
        int layerCount = this.preview.session().snapshot().layers().size();
        int selection = selectedIndex();
        float normalized = layerCount == 0 ? 0.0F : (float) selection / layerCount;
        float scrollDelta = layerCount == 0 ? 1.0F : 1.0F / layerCount;
        this.refreshing = true;
        try {
            this.scroller.scrollerStyle(style -> style.scrollDelta(scrollDelta));
            this.scroller.setNormalizedValue(normalized, false);
            this.scroller.selfAndAllChildren()
                    .forEach(element -> element.setAllowHitTest(layerCount > 0));
        } finally {
            this.refreshing = false;
        }
    }

    private void selectFromScroller() {
        if (this.refreshing) {
            return;
        }
        int layerCount = this.preview.session().snapshot().layers().size();
        if (layerCount == 0) {
            return;
        }
        int selection = Math.round(this.scroller.getNormalizedValue() * layerCount);
        this.refreshing = true;
        try {
            this.scroller.setNormalizedValue((float) selection / layerCount, false);
        } finally {
            this.refreshing = false;
        }
        if (selection == 0) {
            this.preview.panel().showAllLayers();
        } else {
            this.preview.panel().showLayer(selection - 1);
        }
    }

    private int selectedIndex() {
        PreviewVisibleLayer visibleLayer = this.preview.session().viewState().visibleLayer();
        return visibleLayer instanceof PreviewVisibleLayer.LogicalLayer layer ? layer.layerIndex() + 1 : 0;
    }
}
