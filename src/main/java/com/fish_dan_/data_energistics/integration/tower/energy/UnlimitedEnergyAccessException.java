package com.fish_dan_.data_energistics.integration.tower.energy;

/**
 * Reports a failed unlimited-energy operation after a direct access plan has been selected.
 *
 * <p>
 * Callers must treat this failure as terminal for the endpoint during the current tick. Falling back to the standard
 * capability could apply a second mutation when the direct operation or its rollback left the storage state uncertain.
 */
public final class UnlimitedEnergyAccessException extends RuntimeException {

    private final boolean mutationAmountKnown;
    private final long mutationAmount;

    /**
     * Creates a direct-access failure without an underlying invocation exception.
     *
     * @param message failure description
     */
    public UnlimitedEnergyAccessException(String message) {
        this(message, null, true, 0L);
    }

    /**
     * Creates a direct-access failure caused by an invocation or state-access exception.
     *
     * @param message failure description
     * @param cause   underlying exception
     */
    public UnlimitedEnergyAccessException(String message, Throwable cause) {
        this(message, cause, true, 0L);
    }

    /**
     * Creates a direct-access failure whose final mutation amount could not be read back.
     *
     * <p>
     * This factory allows alternate {@link UnlimitedEnergyAccess} implementations to preserve the same settlement
     * contract when a selected direct-access path leaves the target state unreadable.
     *
     * @param message failure description
     * @return failure carrying an unknown mutation amount
     */
    public static UnlimitedEnergyAccessException withUnknownMutationAmount(String message) {
        return new UnlimitedEnergyAccessException(message, null, false, 0L);
    }

    UnlimitedEnergyAccessException(String message, Throwable cause, boolean mutationAmountKnown, long mutationAmount) {
        super(message, cause);
        if (mutationAmount < 0L) {
            throw new IllegalArgumentException("Confirmed mutation amount must not be negative: " + mutationAmount);
        }
        this.mutationAmountKnown = mutationAmountKnown;
        this.mutationAmount = mutationAmount;
    }

    /**
     * Checks whether post-failure read-back proved the net amount changed in the requested direction.
     *
     * @return true when {@link #mutationAmount()} is exact
     */
    public boolean isMutationAmountKnown() {
        return this.mutationAmountKnown;
    }

    /**
     * Returns the verified net amount changed before the failure was reported.
     *
     * @return non-negative mutation amount; meaningful only when {@link #isMutationAmountKnown()} is true
     */
    public long mutationAmount() {
        return this.mutationAmount;
    }
}
