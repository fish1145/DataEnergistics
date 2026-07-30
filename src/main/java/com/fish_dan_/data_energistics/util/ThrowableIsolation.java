package com.fish_dan_.data_energistics.util;

/**
 * Defines the process-level boundary between recoverable third-party failures and fatal JVM failures.
 *
 * <p>
 * Integration code uses this utility after catching {@link Throwable}. Recoverable failures remain available to the
 * caller for logging, rollback, or endpoint isolation, while failures that must terminate the current execution are
 * rethrown unchanged.
 */
public final class ThrowableIsolation {

    private ThrowableIsolation() {}

    /**
     * Rethrows fatal JVM failures and preserves interruption before a recoverable failure is handled.
     *
     * <p>
     * {@link VirtualMachineError} is never an integration failure and therefore must retain its original identity and
     * propagation semantics. A sneaky {@link InterruptedException} is recoverable at the integration boundary, but
     * its thread interruption signal must not be lost.
     *
     * @param throwable failure caught while invoking third-party code
     * @throws VirtualMachineError when {@code throwable} is a fatal JVM failure
     */
    public static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
