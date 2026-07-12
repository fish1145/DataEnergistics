package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreCpuCoreProfile;

import appeng.api.config.CpuSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityDataCoreCpuProfileTest {

    @Test
    void rejectsNegativeContributionValues() {
        assertThrows(IllegalArgumentException.class, () -> TrinityDataCoreCpuContribution.of(-1L, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TrinityDataCoreCpuContribution.of(0L, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> TrinityDataCoreCpuContribution.of(0L, 0, -1));
    }

    @Test
    void rejectsWorkerCapacityAboveMaximumAcrossCpuProfiles() {
        assertThrows(IllegalArgumentException.class, () -> TrinityDataCoreCpuContribution.of(257L, 0, 257));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreCpuProfile(257L, 0, 257, CpuSelectionMode.ANY));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreCpuPartitionProfile(1, 257, 257L, 0, CpuSelectionMode.ANY));
    }

    @Test
    void aggregatesNamedContributionsDeterministically() {
        TrinityDataCoreCpuProfile profile = TrinityDataCoreCpuProfile.fromContributions(Map.of(
                "zeta", new TrinityDataCoreCpuContribution(9L, 2, 2, CpuSelectionMode.ANY),
                "alpha", new TrinityDataCoreCpuContribution(6L, 1, 1, CpuSelectionMode.PLAYER_ONLY)));

        assertEquals(15L, profile.storageBytes());
        assertEquals(3, profile.coProcessors());
        assertEquals(3, profile.partitionCount());
        assertEquals(CpuSelectionMode.PLAYER_ONLY, profile.selectionMode());
    }

    @Test
    void partitionsResolveFullStorageAndCoProcessorsOnDemand() {
        TrinityDataCoreCpuProfile profile = new TrinityDataCoreCpuProfile(
                10L,
                5,
                3,
                CpuSelectionMode.ANY);

        assertEquals(10L, profile.partition(0).storageBytes());
        assertEquals(10L, profile.partition(1).storageBytes());
        assertEquals(10L, profile.partition(3).storageBytes());
        assertEquals(5, profile.partition(0).coProcessors());
        assertEquals(5, profile.partition(1).coProcessors());
        assertEquals(5, profile.partition(3).coProcessors());
        assertThrows(IllegalArgumentException.class, () -> profile.partition(4));
    }

    @Test
    void maxPartitionResolvesMaxStorageAndCoProcessorsWithoutDivision() {
        TrinityDataCoreCpuProfile profile = new TrinityDataCoreCpuProfile(
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                256,
                CpuSelectionMode.ANY);

        assertEquals(Long.MAX_VALUE, profile.partition(0).storageBytes());
        assertEquals(Long.MAX_VALUE, profile.partition(256).storageBytes());
        assertEquals(Integer.MAX_VALUE, profile.partition(0).coProcessors());
        assertEquals(Integer.MAX_VALUE, profile.partition(256).coProcessors());
    }

    @Test
    void rejectsProfilesThatWouldCreateZeroStoragePartitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreCpuProfile(2L, 0, 3, CpuSelectionMode.ANY));
    }

    @Test
    void rejectsConflictingSelectionModeContributions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreCpuProfile.fromContributions(Map.of(
                        "player", new TrinityDataCoreCpuContribution(4L, 0, 1, CpuSelectionMode.PLAYER_ONLY),
                        "machine", new TrinityDataCoreCpuContribution(4L, 0, 1, CpuSelectionMode.MACHINE_ONLY))));
    }

    @Test
    void exportedControllerLayerMapsFullCpuRepeatToMaxThreads() {
        assertEquals(1, TrinityDataCoreCpuCoreProfile.CONTROLLER_LOCAL_Y);
        Set<Integer> exportedRawLayers = new HashSet<>();
        for (int rawY = 4; rawY <= 16; rawY++) {
            exportedRawLayers.add(rawY - TrinityDataCoreCpuCoreProfile.CONTROLLER_LOCAL_Y);
        }

        int repeatCount = TrinityDataCoreCpuCoreProfile.actualRepeatCount(exportedRawLayers);
        TrinityDataCoreCpuCoreProfile profile = new TrinityDataCoreCpuCoreProfile(
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                TrinityDataCoreCpuCoreProfile.FULL_CORE_SLOT_COUNT,
                TrinityDataCoreCpuCoreProfile.FULL_CORE_SLOT_COUNT,
                repeatCount,
                TrinityDataCoreCpuCoreProfile.MAX_REPEAT_COUNT,
                TrinityDataCoreCpuCoreProfile.MAX_THREADS);

        assertEquals(TrinityDataCoreCpuCoreProfile.MAX_REPEAT_COUNT, repeatCount);
        assertEquals(TrinityDataCoreCpuCoreProfile.MAX_THREADS, profile.threadCount());
    }
}
