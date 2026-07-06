package com.fish_dan_.data_energistics.common.crafting.flower;

import appeng.api.config.CpuSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class DigitalConstructFlowerCpuProfileTest {

    @Test
    void rejectsNegativeContributionValues() {
        assertThrows(IllegalArgumentException.class, () -> DigitalConstructFlowerCpuContribution.of(-1L, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DigitalConstructFlowerCpuContribution.of(0L, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> DigitalConstructFlowerCpuContribution.of(0L, 0, -1));
    }

    @Test
    void aggregatesNamedContributionsDeterministically() {
        DigitalConstructFlowerCpuProfile profile = DigitalConstructFlowerCpuProfile.fromContributions(Map.of(
                "zeta", new DigitalConstructFlowerCpuContribution(9L, 2, 2, CpuSelectionMode.ANY),
                "alpha", new DigitalConstructFlowerCpuContribution(6L, 1, 1, CpuSelectionMode.PLAYER_ONLY)));

        assertEquals(15L, profile.storageBytes());
        assertEquals(3, profile.coProcessors());
        assertEquals(3, profile.partitionCount());
        assertEquals(CpuSelectionMode.PLAYER_ONLY, profile.selectionMode());
    }

    @Test
    void partitionsCopyFullStorageAndCoProcessors() {
        DigitalConstructFlowerCpuProfile profile = new DigitalConstructFlowerCpuProfile(
                10L,
                5,
                3,
                CpuSelectionMode.ANY);

        var partitions = profile.partitions();

        assertEquals(3, partitions.size());
        assertEquals(10L, partitions.get(0).storageBytes());
        assertEquals(10L, partitions.get(1).storageBytes());
        assertEquals(10L, partitions.get(2).storageBytes());
        assertEquals(5, partitions.get(0).coProcessors());
        assertEquals(5, partitions.get(1).coProcessors());
        assertEquals(5, partitions.get(2).coProcessors());
    }

    @Test
    void partitionsCopyMaxStorageAndCoProcessorsWithoutDivision() {
        DigitalConstructFlowerCpuProfile profile = new DigitalConstructFlowerCpuProfile(
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                256,
                CpuSelectionMode.ANY);

        var partitions = profile.partitions();

        assertEquals(256, partitions.size());
        assertEquals(Long.MAX_VALUE, partitions.get(0).storageBytes());
        assertEquals(Long.MAX_VALUE, partitions.get(255).storageBytes());
        assertEquals(Integer.MAX_VALUE, partitions.get(0).coProcessors());
        assertEquals(Integer.MAX_VALUE, partitions.get(255).coProcessors());
    }

    @Test
    void rejectsProfilesThatWouldCreateZeroStoragePartitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DigitalConstructFlowerCpuProfile(2L, 0, 3, CpuSelectionMode.ANY));
    }

    @Test
    void rejectsConflictingSelectionModeContributions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DigitalConstructFlowerCpuProfile.fromContributions(Map.of(
                        "player", new DigitalConstructFlowerCpuContribution(4L, 0, 1, CpuSelectionMode.PLAYER_ONLY),
                        "machine", new DigitalConstructFlowerCpuContribution(4L, 0, 1, CpuSelectionMode.MACHINE_ONLY))));
    }
}
