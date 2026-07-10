package com.fish_dan_.data_energistics.common.trinity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCoreComponentTest {

    private static final Map<TrinityCoreTier, Integer> EXPECTED_CAPACITY_VALUES = Map.of(
            TrinityCoreTier.SIZE_1M, 2,
            TrinityCoreTier.SIZE_4M, 8,
            TrinityCoreTier.SIZE_16M, 32,
            TrinityCoreTier.SIZE_64M, 128,
            TrinityCoreTier.SIZE_256M, 512,
            TrinityCoreTier.SIZE_1G, 2048,
            TrinityCoreTier.SIZE_4G, 8192,
            TrinityCoreTier.SIZE_16G, 32768,
            TrinityCoreTier.SIZE_64G, 131072,
            TrinityCoreTier.SIZE_256G, 524288);

    @Test
    void tierCapacityValuesUseMUnitsTimesTwo() {
        for (Map.Entry<TrinityCoreTier, Integer> entry : EXPECTED_CAPACITY_VALUES.entrySet()) {
            assertEquals(entry.getValue(), entry.getKey().capacityValue(), entry.getKey().displayName());
            assertEquals(entry.getValue() / 2, entry.getKey().mUnits(), entry.getKey().displayName());
        }
    }

    @Test
    void tierSuffixesMatchResourceNames() {
        assertEquals("1m", TrinityCoreTier.SIZE_1M.idSuffix());
        assertEquals("256m", TrinityCoreTier.SIZE_256M.idSuffix());
        assertEquals("1G", TrinityCoreTier.SIZE_1G.displayName());
        assertEquals("256g", TrinityCoreTier.SIZE_256G.idSuffix());
    }

    @Test
    void storageCoreExposesTypeCapacity() {
        TrinityCoreMetadata metadata = TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_256M);

        assertEquals(TrinityCoreKind.STORAGE_TYPES, metadata.kind());
        assertEquals(512, metadata.capacityValue());
        assertEquals(0, metadata.patternCapacity());
    }

    @Test
    void mergedStorageCoreExposesParallelCpuCapacity() {
        TrinityCoreMetadata metadata = TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_1G);

        assertEquals(TrinityCoreKind.PARALLEL_CPU, metadata.kind());
        assertEquals(2048, metadata.capacityValue());
        assertEquals(0, metadata.patternCapacity());
    }

    @Test
    void cpuCoreProfileAggregatesMergedStorageCoreCapacity() {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder();
        builder.actualRepeatCount(TrinityDataCoreCpuCoreProfile.MAX_REPEAT_COUNT);

        for (int index = 0; index < 256; index++) {
            builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        }

        TrinityDataCoreCpuCoreProfile profile = builder.build();

        assertEquals(68_719_476_736L, profile.storageBytes());
        assertEquals(131_072, profile.coProcessors());
        assertEquals(256, profile.filledCoreSlots());
        assertEquals(256, profile.threadCount());
        assertTrue(profile.fullCpu());
        assertEquals(Long.MAX_VALUE, profile.contribution().storageBytes());
        assertEquals(Integer.MAX_VALUE, profile.contribution().coProcessors());
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
        for (int index = 0; index < 256; index++) {
            builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        }

        TrinityDataCoreCpuCoreProfile profile = builder.build();

        assertFalse(profile.fullCpu());
        assertEquals(68_719_476_736L, profile.contribution().storageBytes());
        assertEquals(131_072, profile.contribution().coProcessors());
        assertEquals(256, profile.contribution().partitionCount());
    }

    @Test
    void cpuCoreProfileUsesFiniteCapacityWhenAnyCoreSlotIsMissing() {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder()
                .actualRepeatCount(TrinityDataCoreCpuCoreProfile.MAX_REPEAT_COUNT);
        for (int index = 0; index < 255; index++) {
            builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        }

        TrinityDataCoreCpuCoreProfile profile = builder.build();

        assertFalse(profile.fullCpu());
        assertEquals(68_451_041_280L, profile.contribution().storageBytes());
        assertEquals(130_560, profile.contribution().coProcessors());
        assertEquals(256, profile.contribution().partitionCount());
    }

    private static TrinityDataCoreCpuCoreProfile cpuProfileForRepeatHeight(int repeatHeight) {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder()
                .actualRepeatCount(repeatHeight);
        builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));
        return builder.build();
    }

    @Test
    void patternProcessingCoreExposesPatternCapacity() {
        TrinityCoreMetadata ordinary = TrinityCoreMetadata.patternProcessingCore(64);
        TrinityCoreMetadata extended = TrinityCoreMetadata.patternProcessingCore(128);
        TrinityCoreMetadata overlimit = TrinityCoreMetadata.patternProcessingCore(512);

        assertEquals(TrinityCoreKind.PATTERN_PROCESSING, ordinary.kind());
        assertEquals(0, ordinary.capacityValue());
        assertEquals(64, ordinary.patternCapacity());
        assertEquals(128, extended.patternCapacity());
        assertEquals(512, overlimit.patternCapacity());
    }

    @Test
    void craftingCoreProfileAggregatesOnlyPatternProcessingCores() {
        TrinityDataCoreCraftingCoreProfile.Builder builder = TrinityDataCoreCraftingCoreProfile.builder();

        builder.add(TrinityCoreMetadata.patternProcessingCore(64));
        builder.add(TrinityCoreMetadata.patternProcessingCore(128));
        builder.add(TrinityCoreMetadata.patternProcessingCore(512));
        builder.add(TrinityCoreMetadata.storageCore(TrinityCoreTier.SIZE_256M));
        builder.add(TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_256M));

        TrinityDataCoreCraftingCoreProfile profile = builder.build();

        assertTrue(profile.active());
        assertEquals(3, profile.patternCoreCount());
        assertEquals(704, profile.patternCapacity());
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
