package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.implementations.UpgradeableScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataRipperReassemblerScreenTest {

    @Test
    void mainScreenUsesTheNativeAe2ScreenStack() {
        assertEquals(UpgradeableScreen.class, DataRipperReassemblerScreen.class.getSuperclass());
    }

    @Test
    void outputSideScreenSharesTheDigitalStorageDepotImplementation() {
        assertEquals(TypedOutputSideScreen.class, DataRipperReassemblerOutputSideScreen.class.getSuperclass());
        assertEquals(TypedOutputSideScreen.class, DigitalStorageDepotOutputSideScreen.class.getSuperclass());
    }
}
