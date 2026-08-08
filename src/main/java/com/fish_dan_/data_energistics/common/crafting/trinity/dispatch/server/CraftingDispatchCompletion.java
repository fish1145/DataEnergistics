package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

/**
 * Represents one Grid completion boundary registered for a server tick.
 *
 * <p>
 * A completion boundary is invoked exactly once after the server-wide physical rotation finishes. It may be used by a
 * Grid that has no runtime work and therefore must not participate in physical dispatch rotation.
 * </p>
 */
public interface CraftingDispatchCompletion {

    /**
     * Returns the stable identity used for diagnostics and isolated failure reporting.
     *
     * @return non-blank completion identity
     */
    String diagnosticIdentity();

    /**
     * Completes this Grid's current-tick metrics and observation boundary.
     */
    void completeTick();

    /**
     * Isolates an unexpected completion failure to this Grid.
     *
     * @param source  concise failing boundary identity
     * @param failure original unexpected runtime failure
     */
    void recordUnexpectedFailure(String source, RuntimeException failure);
}
