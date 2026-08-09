package com.fish_dan_.data_energistics.blockentity.tower.topology;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerCoverageGeometryTest {

    @Test
    void expandsOneChunkRingForEverySixteenWirelessBoosters() {
        TowerCoverageGeometry coverage = new TowerCoverageGeometry(BlockPos.ZERO, () -> 1);

        assertEquals(0, coverage.computeChunkRadius(0));
        assertEquals(0, coverage.computeChunkRadius(15));
        assertEquals(1, coverage.computeChunkRadius(16));
        assertEquals(4, coverage.computeChunkRadius(64));
        assertEquals(81, coverage.coveredChunkCount(coverage.computeChunkRadius(64)));
    }
}
