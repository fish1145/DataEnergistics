package com.fish_dan_.data_energistics.integration.energy;

/**
 * Reports a failed unlimited-energy operation after a direct access plan has been selected.
 *
 * <p>
 * Callers must treat this failure as terminal for the endpoint during the current tick. Falling back to the standard
 * capability could apply a second mutation when the direct operation or its rollback left the storage state uncertain.
 */
public final class UnlimitedEnergyAccessException extends RuntimeException {

    /**
     * Creates a direct-access failure without an underlying invocation exception.
     *
     * @param message failure description
     */
    public UnlimitedEnergyAccessException(String message) {
        super(message);
    }

    /**
     * Creates a direct-access failure caused by an invocation or state-access exception.
     *
     * @param message failure description
     * @param cause   underlying exception
     */
    public UnlimitedEnergyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
