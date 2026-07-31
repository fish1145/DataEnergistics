package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Identity-based in-memory {@link CraftingDispatchWindow} implementation for one server tick. */
final class CraftingDispatchWindowImpl implements CraftingDispatchWindow {

    /** Mutable attempt state retained only for providers observed during this window. */
    private final Map<ICraftingProvider, ProviderState> states = new IdentityHashMap<>();
    /** Result counters expose rejection and ownership behavior without leaking mutable provider state. */
    private final Map<CraftingDispatchStatus, Integer> resultCounts = new EnumMap<>(CraftingDispatchStatus.class);
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
        return this.attemptCount < this.maxAttemptsPerGrid &&
                (state == null || state.canAttempt(pattern, null));
    }

    @Override
    public boolean canAttempt(
                              ICraftingProvider provider,
                              IPatternDetails pattern,
                              CraftingDispatchTarget target) {
        validateProvider(provider);
        validatePattern(pattern);
        validateTarget(target);
        ProviderState state = this.states.get(provider);
        return this.attemptCount < this.maxAttemptsPerGrid &&
                (state == null || state.canAttempt(pattern, target));
    }

    @Override
    public boolean tryAcquire(
                              ICraftingProvider provider,
                              IPatternDetails pattern,
                              CraftingDispatchTarget target) {
        validateProvider(provider);
        validatePattern(pattern);
        validateTarget(target);
        ProviderState state = this.states.computeIfAbsent(provider, ignored -> new ProviderState());
        if (this.attemptCount >= this.maxAttemptsPerGrid || !state.canAttempt(pattern, target)) {
            return false;
        }
        state.attemptCount++;
        this.attemptCount++;
        return true;
    }

    @Override
    public void recordResult(
                             ICraftingProvider provider,
                             IPatternDetails pattern,
                             @Nullable CraftingDispatchTarget target,
                             CraftingDispatchStatus status) {
        validateProvider(provider);
        validatePattern(pattern);
        validateStatus(status);
        if (target != null) {
            validateTarget(target);
        }

        this.resultCounts.merge(status, 1, Math::addExact);
        ProviderState state = this.states.computeIfAbsent(provider, ignored -> new ProviderState());
        switch (status) {
            case BLOCKED, NO_CAPACITY -> state.block(pattern, target);
            case LOCKED, BUSY, OFFLINE, FAILED_BEFORE_OWNERSHIP -> state.providerUnavailable = true;
            case ACCEPTED, STALE, REJECTED, FAILED_AFTER_OWNERSHIP -> {
                // These outcomes remain observable but do not prove a reusable current-window exclusion.
            }
        }
    }

    @Override
    public int attemptCount() {
        return this.attemptCount;
    }

    @Override
    public int resultCount(CraftingDispatchStatus status) {
        validateStatus(status);
        return this.resultCounts.getOrDefault(status, 0);
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

    private static void validateTarget(CraftingDispatchTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("Crafting dispatch target must not be null");
        }
    }

    private static void validateStatus(CraftingDispatchStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Crafting dispatch status must not be null");
        }
    }

    /** Per-provider state keeps exhaustion and no-capacity decisions independent. */
    private static final class ProviderState {

        /** Number of real physical submissions already attempted in this window. */
        private int attemptCount;

        /** Provider-wide isolation covers busy, offline, locked and pre-ownership failure states. */
        private boolean providerUnavailable;

        /** Pattern identity prevents equality-collapsing wrappers from sharing transient routing state. */
        private final Map<IPatternDetails, PatternState> patternStates = new IdentityHashMap<>();

        /**
         * Returns whether neither quota nor provider/pattern/target state blocks another attempt.
         */
        private boolean canAttempt(
                                   IPatternDetails pattern,
                                   @Nullable CraftingDispatchTarget target) {
            if (this.providerUnavailable || this.attemptCount >= MAX_ATTEMPTS_PER_PROVIDER) {
                return false;
            }
            PatternState patternState = this.patternStates.get(pattern);
            return patternState == null || patternState.canAttempt(target);
        }

        /** Applies a capacity or Blocking exclusion to the narrowest reported scope. */
        private void block(
                           IPatternDetails pattern,
                           @Nullable CraftingDispatchTarget target) {
            PatternState patternState = this.patternStates.computeIfAbsent(pattern, ignored -> new PatternState());
            if (target == null) {
                patternState.patternUnavailable = true;
            } else {
                patternState.unavailableTargets.add(target);
            }
        }
    }

    /** Per-pattern state separates a complete provider-pattern rejection from exact target failures. */
    private static final class PatternState {

        /** Whether no target for this provider-pattern pair should be prepared again in this window. */
        private boolean patternUnavailable;

        /** Stable target identities rejected for Blocking or capacity in this window. */
        private final Set<CraftingDispatchTarget> unavailableTargets = new HashSet<>();

        /** Returns whether the complete pattern and optional exact target remain eligible. */
        private boolean canAttempt(@Nullable CraftingDispatchTarget target) {
            return !this.patternUnavailable &&
                    (target == null || !this.unavailableTargets.contains(target));
        }
    }
}
