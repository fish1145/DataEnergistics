package com.fish_dan_.data_energistics.common.trinity;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(0, metadata.patternRows());
    }

    @Test
    void mergedStorageCoreExposesParallelCpuCapacity() {
        TrinityCoreMetadata metadata = TrinityCoreMetadata.parallelCpuCore(TrinityCoreTier.SIZE_1G);

        assertEquals(TrinityCoreKind.PARALLEL_CPU, metadata.kind());
        assertEquals(2048, metadata.capacityValue());
        assertEquals(0, metadata.patternRows());
    }

    @Test
    void patternProcessingCoreExposesPatternRows() {
        TrinityCoreMetadata ordinary = TrinityCoreMetadata.patternProcessingCore(4);
        TrinityCoreMetadata extended = TrinityCoreMetadata.patternProcessingCore(8);
        TrinityCoreMetadata overlimit = TrinityCoreMetadata.patternProcessingCore(12);

        assertEquals(TrinityCoreKind.PATTERN_PROCESSING, ordinary.kind());
        assertEquals(0, ordinary.capacityValue());
        assertEquals(4, ordinary.patternRows());
        assertEquals(8, extended.patternRows());
        assertEquals(12, overlimit.patternRows());
    }
}
