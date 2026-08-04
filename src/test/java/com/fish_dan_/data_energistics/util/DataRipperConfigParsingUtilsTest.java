package com.fish_dan_.data_energistics.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DataRipperConfigParsingUtilsTest {

    @Test
    void compilesValidatedRulesForDirectGameplayQueries() {
        List<Pattern> blacklist = DataRipperConfigParsingUtils.precompilePatterns(List.of("minecraft:bedrock", "ae2:.*"));
        List<DataRipperConfigParsingUtils.MultiplierEntry> multipliers = DataRipperConfigParsingUtils.precompileMultipliers(List.of("minecraft:.*=1.5", "minecraft:hopper=3.0"));

        assertTrue(DataRipperConfigParsingUtils.isBlockBlacklisted("ae2:controller", blacklist));
        assertFalse(DataRipperConfigParsingUtils.isBlockBlacklisted("minecraft:hopper", blacklist));
        assertEquals(3.0D, DataRipperConfigParsingUtils.getMultiplierForBlock("minecraft:hopper", multipliers));
        assertEquals(1.0D, DataRipperConfigParsingUtils.getMultiplierForBlock("other:block", multipliers));
    }

    @Test
    void rejectsMalformedRulesInsteadOfSilentlyDroppingThem() {
        assertThrows(IllegalArgumentException.class,
                () -> DataRipperConfigParsingUtils.precompilePatterns(List.of(" ")));
        assertThrows(IllegalArgumentException.class,
                () -> DataRipperConfigParsingUtils.precompilePatterns(List.of("[")));
        assertThrows(IllegalArgumentException.class,
                () -> DataRipperConfigParsingUtils.precompileMultipliers(List.of("minecraft:stone")));
        assertThrows(IllegalArgumentException.class,
                () -> DataRipperConfigParsingUtils.precompileMultipliers(List.of("=2.0")));
        assertThrows(IllegalArgumentException.class,
                () -> DataRipperConfigParsingUtils.precompileMultipliers(List.of("minecraft:stone=NaN")));
        assertThrows(NumberFormatException.class,
                () -> DataRipperConfigParsingUtils.precompileMultipliers(List.of("minecraft:stone=invalid")));
    }
}
