package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrinityDataCoreStatusPanelTest {

    @Test
    void compactsBinaryCapacityAtExactBoundaries() {
        assertEquals("0", TrinityDataCoreStatusPanel.compactNumber(""));
        assertEquals("0", TrinityDataCoreStatusPanel.compactNumber("0"));
        assertEquals("1023", TrinityDataCoreStatusPanel.compactNumber("1023"));
        assertEquals("1K", TrinityDataCoreStatusPanel.compactNumber("1024"));
        assertEquals("1.5K", TrinityDataCoreStatusPanel.compactNumber("1536"));
        assertEquals("10K", TrinityDataCoreStatusPanel.compactNumber("10240"));
        assertEquals("-1.5K", TrinityDataCoreStatusPanel.compactNumber("-1536"));
    }

    @Test
    void rejectsMalformedCapacityInsteadOfGuessing() {
        assertThrows(NumberFormatException.class, () -> TrinityDataCoreStatusPanel.compactNumber("not-a-number"));
    }
}
