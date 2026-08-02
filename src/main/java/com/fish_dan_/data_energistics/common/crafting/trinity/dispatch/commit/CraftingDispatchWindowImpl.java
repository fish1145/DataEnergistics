package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchExhaustion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Identity-based in-memory {@link CraftingDispatchWindow} implementation for one server tick. */
final class CraftingDispatchWindowImpl implements CraftingDispatchWindow {

    /** Mutable attempt state retained only for providers observed during this window. */
    private final Map<ICraftingProvider, ProviderState> states = new IdentityHashMap<>();
    /** Result counters expose rejection and ownership behavior without leaking mutable provider state. */
    private final Map<CraftingDispatchStatus, Integer> resultCounts = new EnumMap<>(CraftingDispatchStatus.class);
    /** Immutable hard limits shared by physical call and measured server-time accounting. */
    private final CraftingDispatchLimits limits;
    /** Injectable monotonic source keeps time-boundary behavior deterministic in direct logic tests. */
    private final LongSupplier nanoClock;
    /** Number of real physical submissions already acquired across all providers. */
    private int attemptCount;
    /** Number of completed provider preparation and submission paths. */
    private int serverSubmissionCount;
    /** Accumulated time spent in completed provider preparation and submission paths. */
    private long serverSubmissionNanos;
    /** Single server-thread scope currently measuring provider work. */
    @Nullable
    private SubmissionScopeImpl activeSubmission;

    CraftingDispatchWindowImpl() {
        this(CraftingDispatchLimits.DEFAULT);
    }

    CraftingDispatchWindowImpl(CraftingDispatchLimits limits) {
        this(limits, System::nanoTime);
    }

    CraftingDispatchWindowImpl(CraftingDispatchLimits limits, LongSupplier nanoClock) {
        if (limits == null) {
            throw new IllegalArgumentException("Crafting dispatch limits must not be null");
        }
        if (nanoClock == null) {
            throw new IllegalArgumentException("Crafting dispatch nano clock must not be null");
        }
        this.limits = limits;
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean canAttempt(ICraftingProvider provider, IPatternDetails pattern) {
        validateProvider(provider);
        validatePattern(pattern);
        ProviderState state = this.states.get(provider);
        return hasCompletedGlobalCapacity() &&
                (state == null || state.canAttempt(pattern, null, this.limits.maxAttemptsPerProvider()));
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
        return hasCompletedGlobalCapacity() &&
                (state == null || state.canAttempt(pattern, target, this.limits.maxAttemptsPerProvider()));
    }

    @Override
    public SubmissionScope beginSubmission(ICraftingProvider provider, IPatternDetails pattern) {
        validateProvider(provider);
        validatePattern(pattern);
        if (this.activeSubmission != null) {
            throw new IllegalStateException("Crafting dispatch submission scopes must not be nested");
        }
        if (!canAttempt(provider, pattern)) {
            throw new IllegalStateException("Crafting dispatch submission is unavailable");
        }
        SubmissionScopeImpl submission = new SubmissionScopeImpl(
                provider,
                pattern,
                this.nanoClock.getAsLong());
        this.activeSubmission = submission;
        return submission;
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

    @Override
    public CraftingDispatchExhaustion exhaustion() {
        if (this.attemptCount >= this.limits.maxAttemptsPerGrid()) {
            return CraftingDispatchExhaustion.GRID_CALL_BUDGET;
        }
        if (currentServerSubmissionNanos() >= this.limits.maxServerSubmissionNanos()) {
            return CraftingDispatchExhaustion.SERVER_TIME_BUDGET;
        }
        return CraftingDispatchExhaustion.NONE;
    }

    @Override
    public int serverSubmissionCount() {
        return this.serverSubmissionCount;
    }

    @Override
    public long serverSubmissionNanos() {
        return this.serverSubmissionNanos;
    }

    /** Checks global budgets using only completed work so an active preparation can still filter exact targets. */
    private boolean hasCompletedGlobalCapacity() {
        return this.attemptCount < this.limits.maxAttemptsPerGrid() &&
                this.serverSubmissionNanos < this.limits.maxServerSubmissionNanos();
    }

    /** Includes an active provider path when reporting whether later work must stop. */
    private long currentServerSubmissionNanos() {
        SubmissionScopeImpl submission = this.activeSubmission;
        if (submission == null) {
            return this.serverSubmissionNanos;
        }
        return Math.addExact(this.serverSubmissionNanos, submission.elapsedNanos());
    }

    /** Verifies an active scope still has time before acquiring another irreversible physical call. */
    private boolean hasServerSubmissionTime(SubmissionScopeImpl submission) {
        long projectedNanos = Math.addExact(this.serverSubmissionNanos, submission.elapsedNanos());
        return projectedNanos < this.limits.maxServerSubmissionNanos();
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

    /** Server-thread-confined timing scope for one provider preparation and submission path. */
    private final class SubmissionScopeImpl implements SubmissionScope {

        /** Provider whose path owns every physical call acquired by this scope. */
        private final ICraftingProvider provider;
        /** Exact pattern prepared by this scope. */
        private final IPatternDetails pattern;
        /** Monotonic timestamp captured immediately before provider work begins. */
        private final long startedAtNanos;
        /** Closed scopes reject reuse and double-close mistakes. */
        private boolean closed;

        private SubmissionScopeImpl(
                                    ICraftingProvider provider,
                                    IPatternDetails pattern,
                                    long startedAtNanos) {
            this.provider = provider;
            this.pattern = pattern;
            this.startedAtNanos = startedAtNanos;
        }

        @Override
        public boolean tryAcquire(CraftingDispatchTarget target) {
            requireActive();
            validateTarget(target);
            ProviderState state = states.computeIfAbsent(this.provider, ignored -> new ProviderState());
            if (attemptCount >= limits.maxAttemptsPerGrid() ||
                    !state.canAttempt(this.pattern, target, limits.maxAttemptsPerProvider()) ||
                    !hasServerSubmissionTime(this)) {
                return false;
            }
            state.attemptCount = Math.incrementExact(state.attemptCount);
            attemptCount = Math.incrementExact(attemptCount);
            return true;
        }

        @Override
        public void close() {
            requireActive();
            this.closed = true;
            try {
                long elapsedNanos = elapsedNanos();
                long completedNanos = Math.addExact(serverSubmissionNanos, elapsedNanos);
                int completedCount = Math.incrementExact(serverSubmissionCount);
                serverSubmissionNanos = completedNanos;
                serverSubmissionCount = completedCount;
            } finally {
                activeSubmission = null;
            }
        }

        /** Returns elapsed monotonic time and rejects a clock that moved backwards. */
        private long elapsedNanos() {
            long elapsedNanos = nanoClock.getAsLong() - this.startedAtNanos;
            if (elapsedNanos < 0L) {
                throw new IllegalStateException("Crafting dispatch nano clock moved backwards");
            }
            return elapsedNanos;
        }

        /** Ensures only the current, open scope can mutate its parent window. */
        private void requireActive() {
            if (this.closed) {
                throw new IllegalStateException("Crafting dispatch submission scope is already closed");
            }
            if (activeSubmission != this) {
                throw new IllegalStateException("Crafting dispatch submission scope is not active");
            }
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
                                   @Nullable CraftingDispatchTarget target,
                                   int maxAttemptsPerProvider) {
            if (this.providerUnavailable || this.attemptCount >= maxAttemptsPerProvider) {
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
