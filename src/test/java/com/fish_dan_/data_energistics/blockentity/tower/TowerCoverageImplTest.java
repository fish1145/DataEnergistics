package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.config.Config;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerCoverageImplTest {

    @Test
    void expandsOneChunkRingForEverySixteenWirelessBoosters() {
        int originalBaseRange = Config.dataDistributionTowerRange;
        Config.dataDistributionTowerRange = 1;
        try {
            TowerCoverage coverage = new TowerCoverageImpl(BlockPos.ZERO);

            assertEquals(0, coverage.computeChunkRadius(0));
            assertEquals(0, coverage.computeChunkRadius(15));
            assertEquals(1, coverage.computeChunkRadius(16));
            assertEquals(4, coverage.computeChunkRadius(64));
            assertEquals(81, coverage.coveredChunkCount(coverage.computeChunkRadius(64)));
        } finally {
            Config.dataDistributionTowerRange = originalBaseRange;
        }
    }
}
