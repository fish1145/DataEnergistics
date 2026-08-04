package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.config.Config;
import com.fish_dan_.data_energistics.configuration.LegacyConfigBridge;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerCoverageImplTest {

    @Test
    @SuppressWarnings("UnstableApiUsage")
    void expandsOneChunkRingForEverySixteenWirelessBoosters() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int originalBaseRange = Config.dataDistributionTowerRange;
        Config.dataDistributionTowerRange = 1;
        LegacyConfigBridge.refresh();
        try {
            TowerCoverage coverage = new TowerCoverageImpl(BlockPos.ZERO);

            assertEquals(0, coverage.computeChunkRadius(0));
            assertEquals(0, coverage.computeChunkRadius(15));
            assertEquals(1, coverage.computeChunkRadius(16));
            assertEquals(4, coverage.computeChunkRadius(64));
            assertEquals(81, coverage.coveredChunkCount(coverage.computeChunkRadius(64)));
        } finally {
            Config.dataDistributionTowerRange = originalBaseRange;
            LegacyConfigBridge.refresh();
        }
    }
}
