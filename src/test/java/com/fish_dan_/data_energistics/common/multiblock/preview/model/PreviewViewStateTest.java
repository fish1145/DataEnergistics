package com.fish_dan_.data_energistics.common.multiblock.preview.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PreviewViewStateTest {

    @Test
    void visibleLayerExplicitlySeparatesAllAndOneLogicalLayer() {
        PreviewViewState all = PreviewViewState.initial();
        PreviewViewState secondLayer = all.showLogicalLayer(1);
        PreviewViewState restored = secondLayer.showAllLayers();

        assertEquals(PreviewVisibleLayer.all(), all.visibleLayer());
        assertTrue(all.visibleLayer().includes(0));
        assertTrue(all.visibleLayer().includes(4));
        assertEquals(PreviewVisibleLayer.logicalLayer(1), secondLayer.visibleLayer());
        assertFalse(secondLayer.visibleLayer().includes(0));
        assertTrue(secondLayer.visibleLayer().includes(1));
        assertEquals(all, restored);
        assertThrows(IllegalArgumentException.class, () -> all.showLogicalLayer(-1));
        assertThrows(IllegalArgumentException.class, () -> all.visibleLayer().includes(-1));
        assertThrows(IllegalArgumentException.class, () -> new PreviewViewState(null));
    }
}
