package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity.MachineMode;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class DataIntegratedChargerMachineModeGameTest {

    private DataIntegratedChargerMachineModeGameTest() {}

    @TestHolder("data_integrated_charger_mode_button_cycles_all_processing_families")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void modeButtonCyclesAllProcessingFamilies(GameTestHelper helper) {
        assertEquals(MachineMode.CRYSTAL_GROWTH, MachineMode.POWDER.next());
        assertEquals(MachineMode.INSCRIBER, MachineMode.CRYSTAL_GROWTH.next());
        assertEquals(MachineMode.CHARGER, MachineMode.INSCRIBER.next());
        assertEquals(MachineMode.POWDER, MachineMode.CHARGER.next());
        assertEquals(MachineMode.POWDER, MachineMode.fromOrdinal(-1));
        assertEquals(MachineMode.POWDER, MachineMode.fromOrdinal(MachineMode.values().length));
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_eae_circuit_cutter_rates")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void eaeCircuitCutterRatesFollowOriginalOutputCount(GameTestHelper helper) {
        assertEquals(5, DataIntegratedChargerBlockEntity.getEaeCircuitCutterResultCount(4));
        assertEquals(750, DataIntegratedChargerBlockEntity.getEaeCircuitCutterFluidAmount(4));
        assertEquals(14, DataIntegratedChargerBlockEntity.getEaeCircuitCutterResultCount(9));
        assertEquals(1_050, DataIntegratedChargerBlockEntity.getEaeCircuitCutterFluidAmount(9));
        helper.succeed();
    }

    private static void assertEquals(MachineMode expected, MachineMode actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
