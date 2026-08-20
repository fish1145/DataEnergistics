package com.fish_dan_.data_energistics.common.trinity.core;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCoreComponentTest {

    @Test
    void emptyTrinityUnitExposesLowestTierAcrossAllCapabilityDomains() {
        TrinityCoreMetadata unit = TrinityCoreMetadata.emptyTrinityUnit();

        assertEquals(TrinityCoreKind.STORAGE_TYPES, unit.kind());
        assertTrue(unit.supportsKind(TrinityCoreKind.STORAGE_TYPES));
        assertTrue(unit.supportsKind(TrinityCoreKind.PARALLEL_CPU));
        assertTrue(unit.supportsKind(TrinityCoreKind.PATTERN_PROCESSING));
        assertEquals(1, unit.capacityValue(TrinityCoreKind.STORAGE_TYPES));
        assertEquals(1_024L, unit.byteCapacity(TrinityCoreKind.STORAGE_TYPES));
        assertEquals(1_024L, unit.byteCapacity(TrinityCoreKind.PARALLEL_CPU));
        assertEquals(72, unit.patternCapacity(TrinityCoreKind.PATTERN_PROCESSING));
    }

    @Test
    void capabilityProfilesAcceptEmptyTrinityUnitInTheirOwnDomains() {
        TrinityCoreMetadata unit = TrinityCoreMetadata.emptyTrinityUnit();

        TrinityDataCoreStorageProfile.Builder storageBuilder = TrinityDataCoreStorageProfile.builder(1);
        storageBuilder.add(unit);
        TrinityDataCoreStorageProfile storageProfile = storageBuilder.build();
        assertEquals(1, storageProfile.typeCapacity());
        assertEquals(BigInteger.valueOf(1_024L), storageProfile.totalCapacity());

        TrinityDataCoreCpuCoreProfile.Builder cpuBuilder = TrinityDataCoreCpuCoreProfile.builder()
                .actualRepeatCount(1);
        cpuBuilder.add(unit);
        TrinityDataCoreCpuCoreProfile cpuProfile = cpuBuilder.build();
        assertEquals(1_024L, cpuProfile.storageBytes());
        assertEquals(1, cpuProfile.filledCoreSlots());

        TrinityDataCoreCraftingCoreProfile.Builder craftingBuilder = TrinityDataCoreCraftingCoreProfile.builder();
        craftingBuilder.add(unit);
        TrinityDataCoreCraftingCoreProfile craftingProfile = craftingBuilder.build();
        assertEquals(72, craftingProfile.patternCapacity());
        assertEquals(1, craftingProfile.patternCoreCount());
    }

    @Test
    void cpuCoreProfileAggregatesMergedStorageCoreCapacity() {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder();
        builder.actualRepeatCount(TrinityDataCoreCpuCoreProfile.MAX_REPEAT_COUNT);

        for (int index = 0; index < 272; index++) {
            builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        }

        TrinityDataCoreCpuCoreProfile profile = builder.build();

        assertEquals(73_014_444_032L, profile.storageBytes());
        assertEquals(272, profile.filledCoreSlots());
        assertEquals(256, profile.threadCount());
        assertTrue(profile.fullCpu());
        assertEquals(Long.MAX_VALUE, profile.contribution().storageBytes());
        assertEquals(256, profile.contribution().partitionCount());
    }

    @Test
    void cpuCoreProfileMapsRepeatHeightToThreadCount() {
        assertEquals(19, cpuProfileForRepeatHeight(1).threadCount());
        assertEquals(118, cpuProfileForRepeatHeight(6).threadCount());
        assertEquals(256, cpuProfileForRepeatHeight(13).threadCount());
        assertEquals(256, cpuProfileForRepeatHeight(20).threadCount());
    }

    @Test
    void cpuCoreProfileCountsOnlyContinuousRepeatLayersFromRepeatStart() {
        assertEquals(0, TrinityDataCoreCpuCoreProfile.actualRepeatCount(Set.of(2, 4, 5, 6, 16)));
        assertEquals(3, TrinityDataCoreCpuCoreProfile.actualRepeatCount(Set.of(3, 4, 5, 7, 15)));
        assertEquals(13, TrinityDataCoreCpuCoreProfile.actualRepeatCount(Set.of(
                0,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                16)));
    }

    @Test
    void cpuCoreProfileRequiresExactRepeatHeightForFullCpuCapacity() {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder()
                .actualRepeatCount(20);
        for (int index = 0; index < 272; index++) {
            builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        }

        TrinityDataCoreCpuCoreProfile profile = builder.build();

        assertFalse(profile.fullCpu());
        assertEquals(73_014_444_032L, profile.contribution().storageBytes());
        assertEquals(256, profile.contribution().partitionCount());
    }

    @Test
    void cpuCoreProfileUsesFiniteCapacityWhenAnyCoreSlotIsMissing() {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder()
                .actualRepeatCount(TrinityDataCoreCpuCoreProfile.MAX_REPEAT_COUNT);
        for (int index = 0; index < 271; index++) {
            builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        }

        TrinityDataCoreCpuCoreProfile profile = builder.build();

        assertFalse(profile.fullCpu());
        assertEquals(72_746_008_576L, profile.contribution().storageBytes());
        assertEquals(256, profile.contribution().partitionCount());
    }

    private static TrinityDataCoreCpuCoreProfile cpuProfileForRepeatHeight(int repeatHeight) {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder()
                .actualRepeatCount(repeatHeight);
        builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        return builder.build();
    }

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

    @Test
    void storageProfileAggregatesTypeAndTotalCapacity() {
        TrinityDataCoreStorageProfile.Builder builder = TrinityDataCoreStorageProfile.builder(3);

        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_1M));
        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_4M));

        TrinityDataCoreStorageProfile profile = builder.build();

        assertEquals(10, profile.typeCapacity());
        assertEquals(BigInteger.valueOf(5L).multiply(TrinityDataCoreStorageProfile.AMOUNT_PER_M), profile.totalCapacity());
        assertEquals(2, profile.coreCount());
        assertEquals(3, profile.fullCoreCount());
        assertEquals(false, profile.unlimited());
    }

    @Test
    void storageProfileBecomesUnlimitedWhenAllCorePositionsAreFilled() {
        TrinityDataCoreStorageProfile.Builder builder = TrinityDataCoreStorageProfile.builder(2);

        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_1M));
        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_4M));

        TrinityDataCoreStorageProfile profile = builder.build();

        assertEquals(true, profile.unlimited());
    }
}
