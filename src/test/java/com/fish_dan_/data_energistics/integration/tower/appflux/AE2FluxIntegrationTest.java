package com.fish_dan_.data_energistics.integration.tower.appflux;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AE2FluxIntegrationTest {

    @Test
    void isolatesRecoverableErrorsAndSneakyCheckedFailures() {
        assertEquals(0L, AppFluxThrowableBoundary.isolateExtraction(() -> {
            throw new AssertionError("recoverable AppFlux failure");
        }));

        Exception checkedFailure = new Exception("sneaky AppFlux failure");
        assertEquals(0L, AppFluxThrowableBoundary.isolateRestoration(() -> throwUnchecked(checkedFailure)));
    }

    @Test
    void rethrowsVirtualMachineErrorWithoutChangingItsIdentity() {
        TestVirtualMachineError virtualMachineError = new TestVirtualMachineError();
        TestVirtualMachineError thrownVirtualMachineError = assertThrows(
                TestVirtualMachineError.class,
                () -> AppFluxThrowableBoundary.isolateExtraction(() -> {
                    throw virtualMachineError;
                }));
        assertSame(virtualMachineError, thrownVirtualMachineError);
    }

    private static long throwUnchecked(Throwable throwable) {
        AE2FluxIntegrationTest.<RuntimeException>throwAny(throwable);
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAny(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {}
}
