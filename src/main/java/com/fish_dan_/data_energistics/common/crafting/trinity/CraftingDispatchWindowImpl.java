package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.networking.crafting.ICraftingProvider;

import java.util.IdentityHashMap;
import java.util.Map;

/** Identity-based in-memory {@link CraftingDispatchWindow} implementation for one server tick. */
final class CraftingDispatchWindowImpl implements CraftingDispatchWindow {

    /** Mutable attempt state retained only for providers observed during this window. */
    private final Map<ICraftingProvider, ProviderState> states = new IdentityHashMap<>();

    @Override
    public boolean canAttempt(ICraftingProvider provider) {
        validateProvider(provider);
        ProviderState state = this.states.get(provider);
        return state == null || state.canAttempt();
    }

    @Override
    public boolean tryAcquire(ICraftingProvider provider) {
        validateProvider(provider);
        ProviderState state = this.states.computeIfAbsent(provider, ignored -> new ProviderState());
        if (!state.canAttempt()) {
            return false;
        }
        state.attemptCount++;
        return true;
    }

    @Override
    public void markUnavailable(ICraftingProvider provider) {
        validateProvider(provider);
        this.states.computeIfAbsent(provider, ignored -> new ProviderState()).unavailable = true;
    }

    private static void validateProvider(ICraftingProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Crafting dispatch provider must not be null");
        }
    }

    /** Per-provider state keeps exhaustion and no-capacity decisions independent. */
    private static final class ProviderState {

        /** Number of real physical submissions already attempted in this window. */
        private int attemptCount;

        /** Whether capacity preparation established that this provider cannot accept work this window. */
        private boolean unavailable;

        /** Returns whether neither the fixed quota nor the no-capacity state blocks another attempt. */
        private boolean canAttempt() {
            return !this.unavailable && this.attemptCount < MAX_ATTEMPTS_PER_PROVIDER;
        }
    }
}
