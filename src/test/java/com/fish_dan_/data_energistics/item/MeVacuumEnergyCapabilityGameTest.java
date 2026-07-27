package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MeVacuumEnergyCapabilityGameTest {

    private MeVacuumEnergyCapabilityGameTest() {}

    @TestHolder("me_vacuum_accepts_fe_charging")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void acceptsFeCharging(GameTestHelper helper) {
        ItemStack stack = ModItems.ME_VACUUM.toStack();
        IEnergyStorage energyStorage = stack.getCapability(Capabilities.EnergyStorage.ITEM);

        helper.assertTrue(energyStorage != null, "The ME Vacuum must expose an FE energy capability");
        helper.assertTrue(energyStorage.canReceive(), "The ME Vacuum FE capability must accept energy");
        helper.assertTrue(!energyStorage.canExtract(), "The ME Vacuum FE capability must not output energy");
        helper.assertValueEqual(energyStorage.getEnergyStored(), 0,
                "A new ME Vacuum must start without stored FE");

        int simulated = energyStorage.receiveEnergy(1_000, true);
        helper.assertValueEqual(simulated, 1_000,
                "The ME Vacuum FE capability must accept a simulated charge");
        helper.assertValueEqual(energyStorage.getEnergyStored(), 0,
                "A simulated FE charge must not change stored energy");

        int received = energyStorage.receiveEnergy(1_000, false);
        helper.assertValueEqual(received, 1_000,
                "The ME Vacuum FE capability must accept a real charge");
        helper.assertValueEqual(energyStorage.getEnergyStored(), 1_000,
                "A real FE charge must be stored in the ME Vacuum");
        helper.assertValueEqual(energyStorage.extractEnergy(1_000, false), 0,
                "The ME Vacuum FE capability must reject energy extraction");
        helper.succeed();
    }
}
