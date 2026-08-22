package com.fish_dan_.data_energistics.common.trinity.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCoreComponentTest {

    @Test
    void craftingCoreProfileAggregatesOnlyPatternProcessingCores() {
        TrinityDataCoreCraftingCoreProfile.Builder builder = TrinityDataCoreCraftingCoreProfile.builder();

        builder.add(TrinityCoreMetadata.patternProcessingCore(TrinityPatternCoreTier.STANDARD.patternCapacity()));
        builder.add(TrinityCoreMetadata.patternProcessingCore(TrinityPatternCoreTier.EXTENDED.patternCapacity()));
        builder.add(TrinityCoreMetadata.patternProcessingCore(TrinityPatternCoreTier.OVERLIMIT.patternCapacity()));
        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_256M));
        builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));

        TrinityDataCoreCraftingCoreProfile profile = builder.build();

        assertTrue(profile.active());
        assertEquals(3, profile.patternCoreCount());
        assertEquals(792, profile.patternCapacity());
    }

    @Test
    void craftingCoreProfileIsEmptyWhenNoPatternProcessingCoreExists() {
        TrinityDataCoreCraftingCoreProfile.Builder builder = TrinityDataCoreCraftingCoreProfile.builder();

        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_256M));
        builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));

        TrinityDataCoreCraftingCoreProfile profile = builder.build();

        assertFalse(profile.active());
        assertEquals(TrinityDataCoreCraftingCoreProfile.EMPTY, profile);
    }
}
