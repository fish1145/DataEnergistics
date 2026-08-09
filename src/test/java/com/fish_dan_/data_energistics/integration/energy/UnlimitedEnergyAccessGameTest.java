package com.fish_dan_.data_energistics.integration.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.mixin.core.NeoForgeEnergyStorageAccessor;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

/** Verifies the unlimited adapter against a real Mixin-transformed NeoForge energy storage. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class UnlimitedEnergyAccessGameTest {

    private UnlimitedEnergyAccessGameTest() {}

    @TestHolder("unlimited_energy_access_uses_real_neoforge_accessor")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesRealNeoForgeAccessor(GameTestHelper helper) {
        EnergyStorage storage = new EnergyStorage(1_000, 1, 1, 400);
        UnlimitedEnergyAccess access = new VerifiedUnlimitedEnergyAccess();

        helper.assertTrue(storage instanceof NeoForgeEnergyStorageAccessor,
                "NeoForge EnergyStorage must receive the unlimited-energy Mixin accessor");
        helper.assertValueEqual(storage.receiveEnergy(500, true), 1,
                "The public NeoForge receive path must retain its configured rate limit");
        helper.assertValueEqual(storage.extractEnergy(500, true), 1,
                "The public NeoForge extract path must retain its configured rate limit");

        helper.assertValueEqual(access.insert(storage, 500L, false), 500L,
                "The unlimited accessor must bypass the NeoForge receive rate");
        helper.assertValueEqual(storage.getEnergyStored(), 900,
                "The Mixin accessor must update the real NeoForge storage field");
        helper.assertValueEqual(access.extract(storage, 700L, false), 700L,
                "The unlimited accessor must bypass the NeoForge extract rate");
        helper.assertValueEqual(storage.getEnergyStored(), 200,
                "The Mixin accessor must preserve the verified final amount");
        helper.succeed();
    }
}
