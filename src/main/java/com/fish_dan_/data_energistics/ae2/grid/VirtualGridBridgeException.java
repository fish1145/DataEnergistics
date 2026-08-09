package com.fish_dan_.data_energistics.ae2.grid;

/**
 * Reports an atomic virtual-member validation or grid-service registration failure.
 */
public final class VirtualGridBridgeException extends RuntimeException {

    /**
     * Creates a bridge failure with diagnostic context.
     *
     * @param message failure description
     */
    public VirtualGridBridgeException(String message) {
        super(message);
    }

    /**
     * Creates a bridge failure while retaining the provider exception.
     *
     * @param message failure description
     * @param cause   provider failure
     */
    public VirtualGridBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
