package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PoweredMachineEnergyCapabilityGameTest {

    private static final BlockPos MACHINE_POS = new BlockPos(2, 1, 2);

    private PoweredMachineEnergyCapabilityGameTest() {}

    @TestHolder("powered_machines_expose_input_energy_capability")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void poweredMachinesExposeInputEnergyCapability(GameTestHelper helper) {
        assertInputEnergyCapability(helper, DEBlocks.DATA_EXTRACTOR.get().defaultBlockState(), "Data extractor");
        assertInputEnergyCapability(helper, DEBlocks.DATA_RIPPER_REASSEMBLER.get().defaultBlockState(), "Data reassembler");
        assertInputEnergyCapability(helper, DEBlocks.DATA_MIMETIC_FIELD.get().defaultBlockState(), "Data mimetic field");
        assertInputEnergyCapability(helper, DEBlocks.DATA_TELEPORT_ANCHOR.get().defaultBlockState(), "Data teleport anchor");
        assertInputEnergyCapability(helper, DEBlocks.DATA_SANCTUM.get().defaultBlockState(), "Data sanctum");

        helper.setBlock(MACHINE_POS, DEBlocks.DATA_SOLAR_PANEL.get().defaultBlockState());
        IEnergyStorage solarEnergy = helper.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, helper.absolutePos(MACHINE_POS), Direction.NORTH);
        helper.assertTrue(solarEnergy == null, "The ME solar panel must not expose an external FE capability");
        helper.succeed();
    }

    private static void assertInputEnergyCapability(GameTestHelper helper, BlockState state, String machineName) {
        helper.setBlock(MACHINE_POS, state);
        BlockEntity blockEntity = helper.getBlockEntity(MACHINE_POS);
        if (!(blockEntity instanceof AENetworkedPoweredBlockEntity poweredMachine)) {
            throw new GameTestAssertException(machineName + " must have an AE-powered block entity");
        }

        IEnergyStorage energy = helper.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, helper.absolutePos(MACHINE_POS), Direction.NORTH);
        if (energy == null) {
            throw new GameTestAssertException(machineName + " must expose an input energy capability while disconnected from an AE network");
        }
        helper.assertTrue(energy.canReceive(), machineName + " energy capability must accept FE");
        helper.assertTrue(!energy.canExtract(), machineName + " energy capability must not extract FE");

        double storedBefore = poweredMachine.getInternalCurrentPower();
        helper.assertTrue(energy.receiveEnergy(100, false) > 0, machineName + " must accept a real FE transfer");
        helper.assertTrue(poweredMachine.getInternalCurrentPower() > storedBefore,
                machineName + " FE transfer must increase its AE power buffer");
    }
}
