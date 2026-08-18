package com.fish_dan_.data_energistics.integration.tower.energy.oritech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OritechEnergyBridgeTest {

    @Test
    void isolatesRecoverableErrorsAndSneakyCheckedFailures() {
        assertNull(OritechEnergyBridge.isolateEnergyStorageLookup(() -> {
            throw new AssertionError("recoverable Oritech lookup failure");
        }, BlockPos.ZERO, Direction.NORTH));

        Exception checkedFailure = new Exception("sneaky Oritech lookup failure");
        assertNull(OritechEnergyBridge.isolateEnergyStorageLookup(
                () -> throwUnchecked(checkedFailure), BlockPos.ZERO, null));
    }

    @Test
    void rethrowsVirtualMachineErrorWithoutChangingItsIdentity() {
        TestVirtualMachineError virtualMachineError = new TestVirtualMachineError();
        TestVirtualMachineError thrownVirtualMachineError = assertThrows(
                TestVirtualMachineError.class,
                () -> OritechEnergyBridge.isolateEnergyStorageLookup(() -> {
                    throw virtualMachineError;
                }, BlockPos.ZERO, Direction.SOUTH));
        assertSame(virtualMachineError, thrownVirtualMachineError);
    }

    private static IEnergyStorage throwUnchecked(Throwable throwable) {
        OritechEnergyBridgeTest.<RuntimeException>throwAny(throwable);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAny(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {}
}
