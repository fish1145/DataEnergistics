package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

/**
 * Reports a stale endpoint, invalid capability response, or failed FE transaction operation.
 */
public final class TowerEnergyTransferException extends RuntimeException {

    /**
     * Creates a transaction failure without a nested cause.
     *
     * @param message diagnostic message
     */
    public TowerEnergyTransferException(String message) {
        super(message);
    }

    /**
     * Creates a transaction failure retaining its original cause.
     *
     * @param message diagnostic message
     * @param cause   original failure
     */
    public TowerEnergyTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
