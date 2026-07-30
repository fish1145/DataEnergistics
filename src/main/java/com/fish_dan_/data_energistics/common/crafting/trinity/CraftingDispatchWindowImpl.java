package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Identity-based in-memory {@link CraftingDispatchWindow} implementation for one server tick. */
final class CraftingDispatchWindowImpl implements CraftingDispatchWindow {

    /** Mutable attempt state retained only for providers observed during this window. */
    private final Map<ICraftingProvider, ProviderState> states = new IdentityHashMap<>();
    /** Grid-wide hard ceiling prevents a rejection storm from scaling with provider count. */
    private final int maxAttemptsPerGrid;
    /** Number of real physical submissions already acquired across all providers. */
    private int attemptCount;

    CraftingDispatchWindowImpl() {
        this(MAX_ATTEMPTS_PER_GRID);
    }

    CraftingDispatchWindowImpl(int maxAttemptsPerGrid) {
        if (maxAttemptsPerGrid <= 0) {
            throw new IllegalArgumentException("Grid crafting dispatch limit must be positive");
        }
        this.maxAttemptsPerGrid = maxAttemptsPerGrid;
    }

    @Override
    public boolean canAttempt(ICraftingProvider provider, IPatternDetails pattern) {
        validateProvider(provider);
        validatePattern(pattern);
        ProviderState state = this.states.get(provider);
        return this.attemptCount < this.maxAttemptsPerGrid && (state == null || state.canAttempt(pattern));
    }

    @Override
    public boolean tryAcquire(ICraftingProvider provider, IPatternDetails pattern) {
        validateProvider(provider);
        validatePattern(pattern);
        ProviderState state = this.states.computeIfAbsent(provider, ignored -> new ProviderState());
        if (this.attemptCount >= this.maxAttemptsPerGrid || !state.canAttempt(pattern)) {
            return false;
        }
        state.attemptCount++;
        this.attemptCount++;
        return true;
    }

    @Override
    public void markUnavailable(ICraftingProvider provider, IPatternDetails pattern) {
        validateProvider(provider);
        validatePattern(pattern);
        this.states.computeIfAbsent(provider, ignored -> new ProviderState()).unavailablePatterns.add(pattern);
    }

    @Override
    public int attemptCount() {
        return this.attemptCount;
    }

    private static void validateProvider(ICraftingProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Crafting dispatch provider must not be null");
        }
    }

    private static void validatePattern(IPatternDetails pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("Crafting dispatch pattern must not be null");
        }
    }

    /** Per-provider state keeps exhaustion and no-capacity decisions independent. */
    private static final class ProviderState {

        /** Number of real physical submissions already attempted in this window. */
        private int attemptCount;

        /** Patterns for which capacity preparation established no capacity in this window. */
        private final Set<IPatternDetails> unavailablePatterns = new HashSet<>();

        /**
         * Returns whether neither the fixed quota nor the pattern-specific no-capacity state blocks another attempt.
         */
        private boolean canAttempt(IPatternDetails pattern) {
            return !this.unavailablePatterns.contains(pattern) && this.attemptCount < MAX_ATTEMPTS_PER_PROVIDER;
        }
    }
}
