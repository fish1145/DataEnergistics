package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

import java.util.function.BiConsumer;

/**
 * Lightweight server-thread completion boundary for a Grid without physical runtime work.
 */
public final class GridCraftingDispatchCompletion implements CraftingDispatchCompletion {

    private final String diagnosticIdentity;
    private final Runnable tickCompletion;
    private final BiConsumer<String, RuntimeException> failureRecorder;

    /**
     * Creates one completion-only boundary without allocating physical dispatch state.
     *
     * @param diagnosticIdentity stable Grid identity for diagnostics
     * @param tickCompletion     callback executed exactly once after the server rotation
     * @param failureRecorder    callback that isolates unexpected completion failures
     */
    public GridCraftingDispatchCompletion(String diagnosticIdentity,
                                          Runnable tickCompletion,
                                          BiConsumer<String, RuntimeException> failureRecorder) {
        if (diagnosticIdentity == null || diagnosticIdentity.isBlank()) {
            throw new IllegalArgumentException("Crafting dispatch completion identity is required");
        }
        if (tickCompletion == null || failureRecorder == null) {
            throw new IllegalArgumentException("Crafting dispatch completion callbacks are required");
        }
        this.diagnosticIdentity = diagnosticIdentity;
        this.tickCompletion = tickCompletion;
        this.failureRecorder = failureRecorder;
    }

    @Override
    public String diagnosticIdentity() {
        return this.diagnosticIdentity;
    }

    @Override
    public void completeTick() {
        this.tickCompletion.run();
    }

    @Override
    public void recordUnexpectedFailure(String source, RuntimeException failure) {
        this.failureRecorder.accept(source, failure);
    }
}
