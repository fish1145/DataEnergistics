package com.fish_dan_.data_energistics.common.crafting.dynamic;

/**
 * Terminates one crafting job when registered dynamic-output semantics cannot be resolved safely before dispatch.
 */
public final class DynamicCraftingOutputResolutionException extends RuntimeException {

    /**
     * @param message deterministic job diagnostic
     */
    public DynamicCraftingOutputResolutionException(String message) {
        super(message);
    }

    /**
     * @param message deterministic job diagnostic
     * @param cause   adapter or validation failure
     */
    public DynamicCraftingOutputResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
