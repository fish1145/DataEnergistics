package com.fish_dan_.data_energistics.common.crafting.trinity.profile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDataCoreCpuProfileTest {

    @Test
    void activeProfileDelegatesParallelismToConfiguredDispatchLimits() {
        TrinityDataCoreCpuContribution structure = TrinityDataCoreCpuContribution.of(1L, 0, 1);

        TrinityDataCoreCpuProfile profile = TrinityDataCoreCpuProfile.fromContributions(Map.of("cpu", structure));

        assertTrue(profile.active());
        assertEquals(Integer.MAX_VALUE, profile.coProcessors());
        assertEquals(Integer.MAX_VALUE, profile.partition(1).coProcessors());
    }
}
