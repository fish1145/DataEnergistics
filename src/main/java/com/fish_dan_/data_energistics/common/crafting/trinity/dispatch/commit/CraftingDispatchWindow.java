package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchExhaustion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingServerDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Bounds physical crafting submissions for one AE grid tick while sharing provider state across Trinity runtimes.
 *
 * <p>
 * Accounting is based on provider and pattern object identity. A {@link SubmissionScope} measures the complete
 * server-thread provider path, while each successful {@link SubmissionScope#tryAcquire(CraftingDispatchTarget)} call
 * records one real physical attempt regardless of its result. Explicit result facts drive provider, pattern and target
 * scoped exclusions that expire with this window.
 * </p>
 * <p>
 * Identity-based in-memory {@link CraftingDispatchWindow} implementation for one server tick.
 */
public final class CraftingDispatchWindow {

    /**
     * Creates an empty dispatch window for one AE grid tick.
     *
     * @return independent dispatch window
     */
    public static CraftingDispatchWindow create() {
        return new CraftingDispatchWindow();
    }

    /**
     * Creates an empty dispatch window with an explicit immutable hard-limit snapshot.
     *
     * @param limits physical call and server submission time limits
     * @return independent dispatch window
     */
    public static CraftingDispatchWindow create(CraftingDispatchLimits limits) {
        return new CraftingDispatchWindow(limits);
    }

    /**
     * Creates a grid window sharing one current-tick server budget with every other Trinity grid.
     *
     * @param limits       grid-local hard limits
     * @param serverBudget server-wide current-tick boundary
     * @return independent grid window backed by the shared server boundary
     */
    public static CraftingDispatchWindow create(
                                                CraftingDispatchLimits limits,
                                                CraftingServerDispatchBudget serverBudget) {
        return new CraftingDispatchWindow(limits, System::nanoTime, serverBudget);
    }

    /**
     * Creates a dispatch window with an explicit monotonic clock for deterministic budget verification.
     *
     * @param limits    physical call and server submission time limits
     * @param nanoClock monotonic nanosecond source
     * @return independent dispatch window
     */
    public static CraftingDispatchWindow create(CraftingDispatchLimits limits, LongSupplier nanoClock) {
        return new CraftingDispatchWindow(limits, nanoClock);
    }

    /**
     * Checks whether a grid-wide hard budget has been exhausted.
     *
     * @return whether later workers and runtimes must defer their work
     */
    public boolean isExhausted() {
        return exhaustion() != CraftingDispatchExhaustion.NONE;
    }

    /**
     * Measures one provider path and controls every physical call made from that path.
     */
    public interface SubmissionScope extends AutoCloseable {

        /**
         * Atomically acquires and records one physical submission attempt when quota, time and route state permit it.
         *
         * @param target stable provider-local target fixed during preparation
         * @return structured acquisition outcome
         */
        Acquisition tryAcquire(CraftingDispatchTarget target);

        /**
         * Completes timing for this provider path without declaring checked cleanup failures.
         */
        @Override
        void close();
    }

    /**
     * Final provider-call admission result produced immediately before ownership transfer.
     */
    public enum Acquisition {
        /**
         * The physical attempt was recorded and the provider may be called.
         */
        ACQUIRED,
        /**
         * A grid-local or server-wide physical/time budget was exhausted.
         */
        WINDOW_EXHAUSTED,
        /**
         * The selected provider route became unavailable inside this grid window.
         */
        ROUTE_UNAVAILABLE
    }

    /**
     * Measures one server-thread-confined read-only capacity capture.
     */
    public interface CapacityCaptureScope extends AutoCloseable {

        /**
         * Completes capture timing without declaring checked cleanup failures.
         */
        @Override
        void close();
    }

    /**
     * Mutable attempt state retained only for providers observed during this window.
     */
    private final Map<ICraftingProvider, ProviderState> states = new IdentityHashMap<>();
    /**
     * Result counters expose rejection and ownership behavior without leaking mutable provider state.
     */
    private final Map<CraftingDispatchStatus, Integer> resultCounts = new EnumMap<>(CraftingDispatchStatus.class);
    /**
     * Immutable hard limits shared by physical call and measured server-time accounting.
     */
    private final CraftingDispatchLimits limits;
    /**
     * Injectable monotonic source keeps time-boundary behavior deterministic in direct logic tests.
     */
    private final LongSupplier nanoClock;
    /**
     * Shared current-tick limit prevents independent grids from multiplying the server latency budget.
     */
    private final CraftingServerDispatchBudget serverBudget;
    /**
     * Number of real physical submissions already acquired across all providers.
     */
    private int attemptCount;
    /**
     * Number of completed provider preparation and submission paths.
     */
    private int serverSubmissionCount;
    /**
     * Accumulated time spent in completed provider preparation and submission paths.
     */
    private long serverSubmissionNanos;
    /**
     * Number of completed read-only provider-capacity captures.
     */
    private int capacityCaptureCount;
    /**
     * Accumulated time spent in completed read-only provider-capacity captures.
     */
    private long capacityCaptureNanos;
    /**
     * Logical firings committed through counted physical submissions.
     */
    private BigInteger committedLogicalCrafts = BigInteger.ZERO;
    /**
     * Single server-thread scope currently measuring provider work.
     */
    @Nullable
    private MeasuredSubmissionScope activeSubmission;
    /**
     * Single server-thread scope currently measuring capacity simulation.
     */
    @Nullable
    private MeasuredCapacityCaptureScope activeCapacityCapture;

    CraftingDispatchWindow() {
        this(CraftingDispatchLimits.DEFAULT);
    }

    CraftingDispatchWindow(CraftingDispatchLimits limits) {
        this(limits, System::nanoTime, CraftingServerDispatchBudget.unbounded());
    }

    CraftingDispatchWindow(CraftingDispatchLimits limits, LongSupplier nanoClock) {
        this(limits, nanoClock, CraftingServerDispatchBudget.unbounded());
    }

    CraftingDispatchWindow(
                           CraftingDispatchLimits limits,
                           LongSupplier nanoClock,
                           CraftingServerDispatchBudget serverBudget) {
        if (limits == null) {
            throw new IllegalArgumentException("Crafting dispatch limits must not be null");
        }
        if (nanoClock == null) {
            throw new IllegalArgumentException("Crafting dispatch nano clock must not be null");
        }
        if (serverBudget == null) {
            throw new IllegalArgumentException("Server crafting dispatch budget must not be null");
        }
        this.limits = limits;
        this.nanoClock = nanoClock;
        this.serverBudget = serverBudget;
    }

    /**
     * Checks whether one provider-pattern pair is available and still has physical submission quota.
     *
     * @param provider provider instance to inspect
     * @param pattern  pattern about to be prepared
     * @return whether a caller may prepare another submission for this provider
     */
    public boolean canAttempt(ICraftingProvider provider, IPatternDetails pattern) {
        validateProvider(provider);
        validatePattern(pattern);
        ProviderState state = this.states.get(provider);
        return hasCompletedGlobalCapacity() &&
                this.serverBudget.canStart(0L) &&
                (state == null || state.canAttempt(pattern, null, this.limits.maxAttemptsPerProvider()));
    }

    /**
     * Checks whether one exact provider-pattern-target route remains available in this window.
     *
     * <p>
     * During an active submission scope this method deliberately ignores that scope's still-running elapsed time so a
     * provider can filter targets consistently. The scope must still acquire the final physical call immediately
     * before commit.
     * </p>
     *
     * @param provider provider instance to inspect
     * @param pattern  pattern about to be prepared
     * @param target   stable provider-local target
     * @return whether the target remains eligible and physical quota remains
     */
    public boolean canAttempt(
                              ICraftingProvider provider,
                              IPatternDetails pattern,
                              CraftingDispatchTarget target) {
        return canAttempt(provider, pattern, target, this.limits.maxAttemptsPerProvider());
    }

    /**
     * Checks a counted provider route whose first physical call consumes this provider's complete current-window
     * allowance. Other provider identities retain their own independent allowance.
     *
     * @param provider counted provider instance to inspect
     * @param pattern  pattern about to be prepared
     * @param target   stable provider-local target
     * @return whether this provider has not submitted a counted batch in the current window
     */
    public boolean canAttemptCounted(
                                     ICraftingProvider provider,
                                     IPatternDetails pattern,
                                     CraftingDispatchTarget target) {
        return canAttempt(provider, pattern, target, 1);
    }

    private boolean canAttempt(
                               ICraftingProvider provider,
                               IPatternDetails pattern,
                               CraftingDispatchTarget target,
                               int providerAttemptLimit) {
        validateProvider(provider);
        validatePattern(pattern);
        validateTarget(target);
        ProviderState state = this.states.get(provider);
        return hasCompletedGlobalCapacity() &&
                this.serverBudget.canStart(0L) &&
                (state == null || state.canAttempt(pattern, target, providerAttemptLimit));
    }

    /**
     * Starts measuring one provider's complete server-thread preparation and submission path.
     *
     * <p>
     * Callers must first verify {@link #canAttempt(ICraftingProvider, IPatternDetails)} and must close the returned
     * scope. Dispatch windows are server-thread confined and reject nested submission scopes.
     * </p>
     *
     * @param provider provider instance about to be prepared
     * @param pattern  exact pattern being prepared
     * @return closeable scope that owns physical attempt acquisition for this provider path
     */
    public SubmissionScope beginSubmission(ICraftingProvider provider, IPatternDetails pattern) {
        return beginSubmission(provider, pattern, this.limits.maxAttemptsPerProvider());
    }

    /**
     * Starts a counted provider path whose physical acquisition is limited to one call for this provider identity in
     * the current window.
     *
     * @param provider counted provider instance about to be prepared
     * @param pattern  exact pattern being prepared
     * @return closeable counted submission scope
     */
    public SubmissionScope beginCountedSubmission(ICraftingProvider provider, IPatternDetails pattern) {
        return beginSubmission(provider, pattern, 1);
    }

    private SubmissionScope beginSubmission(
                                            ICraftingProvider provider,
                                            IPatternDetails pattern,
                                            int providerAttemptLimit) {
        validateProvider(provider);
        validatePattern(pattern);
        if (this.activeSubmission != null) {
            throw new IllegalStateException("Crafting dispatch submission scopes must not be nested");
        }
        if (this.activeCapacityCapture != null) {
            throw new IllegalStateException("Crafting dispatch submission cannot overlap capacity capture");
        }
        ProviderState state = this.states.get(provider);
        if (!hasCompletedGlobalCapacity() ||
                !this.serverBudget.canStart(0L) ||
                (state != null && !state.canAttempt(pattern, null, providerAttemptLimit))) {
            throw new IllegalStateException("Crafting dispatch submission is unavailable");
        }
        MeasuredSubmissionScope submission = new MeasuredSubmissionScope(
                provider,
                pattern,
                providerAttemptLimit,
                this.nanoClock.getAsLong());
        this.activeSubmission = submission;
        return submission;
    }

    /**
     * Checks the independent server-thread budget for another provider-capacity snapshot.
     *
     * @return whether a caller may begin one more read-only capacity capture
     */
    public boolean canCaptureProviderCapacity() {
        return this.activeSubmission == null &&
                this.activeCapacityCapture == null &&
                this.capacityCaptureNanos < this.limits.maxCapacityCaptureNanos() &&
                hasCompletedGlobalCapacity() &&
                this.serverBudget.canStart(0L);
    }

    /**
     * Starts measuring one read-only provider-capacity capture.
     *
     * @return closeable scope that charges elapsed time only to the capacity-capture budget
     */
    public CapacityCaptureScope beginProviderCapacityCapture() {
        if (this.activeCapacityCapture != null) {
            throw new IllegalStateException("Provider capacity capture scopes must not be nested");
        }
        if (this.activeSubmission != null) {
            throw new IllegalStateException("Provider capacity capture cannot overlap crafting submission");
        }
        if (!canCaptureProviderCapacity()) {
            throw new IllegalStateException("Provider capacity capture budget is exhausted");
        }
        MeasuredCapacityCaptureScope capture = new MeasuredCapacityCaptureScope(this.nanoClock.getAsLong());
        this.activeCapacityCapture = capture;
        return capture;
    }

    /**
     * Records one explicit preparation or submission result and updates only the status-defined negative-cache scope.
     *
     * @param provider provider that produced the result
     * @param pattern  exact pattern being dispatched
     * @param target   exact target involved, or {@code null} for a provider/pattern scoped result
     * @param status   explicit result status
     */
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

    /**
     * Returns the number of real physical submissions acquired across the complete grid window.
     *
     * @return total acquired physical attempts
     */
    public int attemptCount() {
        return this.attemptCount;
    }

    /**
     * Returns how many times one explicit result was observed in this window.
     *
     * @param status result status to inspect
     * @return number of recorded occurrences
     */
    public int resultCount(CraftingDispatchStatus status) {
        validateStatus(status);
        return this.resultCounts.getOrDefault(status, 0);
    }

    /**
     * Records a logical counted batch after the provider has taken ownership.
     *
     * @param logicalCrafts positive committed logical firing count
     */
    public void recordCommittedLogicalCrafts(long logicalCrafts) {
        if (logicalCrafts <= 0L) {
            throw new IllegalArgumentException("Committed logical craft count must be positive");
        }
        this.committedLogicalCrafts = this.committedLogicalCrafts.add(BigInteger.valueOf(logicalCrafts));
    }

    /**
     * @return total logical firings committed through physical calls in this window
     */
    public BigInteger committedLogicalCrafts() {
        return this.committedLogicalCrafts;
    }

    /**
     * Returns the grid-wide hard budget that currently prevents more submission work.
     *
     * @return exhaustion reason, or {@link CraftingDispatchExhaustion#NONE}
     */
    public CraftingDispatchExhaustion exhaustion() {
        if (this.attemptCount >= this.limits.maxAttemptsPerGrid()) {
            return CraftingDispatchExhaustion.GRID_CALL_BUDGET;
        }
        long activeDispatchNanos = activeDispatchNanos();
        if (currentServerWorkNanos() >= this.limits.maxServerSubmissionNanos()) {
            return CraftingDispatchExhaustion.SERVER_TIME_BUDGET;
        }
        if (!this.serverBudget.canStart(activeDispatchNanos)) {
            return CraftingDispatchExhaustion.SERVER_TICK_BUDGET;
        }
        return CraftingDispatchExhaustion.NONE;
    }

    /**
     * Returns how many measured provider submission paths completed in this window.
     *
     * @return completed server submission scopes
     */
    public int serverSubmissionCount() {
        return this.serverSubmissionCount;
    }

    /**
     * Returns the accumulated monotonic time spent in completed provider submission paths.
     *
     * @return measured server submission nanoseconds
     */
    public long serverSubmissionNanos() {
        return this.serverSubmissionNanos;
    }

    /**
     * Returns how many provider-capacity captures completed in this grid window.
     *
     * @return completed capture count
     */
    public int capacityCaptureCount() {
        return this.capacityCaptureCount;
    }

    /**
     * Returns accumulated monotonic time spent capturing provider capacity.
     *
     * @return measured capture nanoseconds
     */
    public long capacityCaptureNanos() {
        return this.capacityCaptureNanos;
    }

    /**
     * Checks global budgets using only completed work so an active preparation can still filter exact targets.
     */
    private boolean hasCompletedGlobalCapacity() {
        return this.attemptCount < this.limits.maxAttemptsPerGrid() &&
                completedServerWorkNanos() < this.limits.maxServerSubmissionNanos();
    }

    private long completedServerWorkNanos() {
        return Math.addExact(this.serverSubmissionNanos, this.capacityCaptureNanos);
    }

    private long currentServerWorkNanos() {
        return Math.addExact(completedServerWorkNanos(), activeDispatchNanos());
    }

    private long activeDispatchNanos() {
        MeasuredSubmissionScope submission = this.activeSubmission;
        if (submission != null) {
            return submission.elapsedNanos();
        }
        MeasuredCapacityCaptureScope capture = this.activeCapacityCapture;
        return capture == null ? 0L : capture.elapsedNanos();
    }

    /**
     * Verifies an active scope still has time before acquiring another irreversible physical call.
     */
    private boolean hasServerSubmissionTime(MeasuredSubmissionScope submission) {
        long activeNanos = submission.elapsedNanos();
        long projectedNanos = Math.addExact(completedServerWorkNanos(), activeNanos);
        return projectedNanos < this.limits.maxServerSubmissionNanos() &&
                this.serverBudget.canStart(activeNanos);
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

    /**
     * Server-thread-confined timing scope for one provider preparation and submission path.
     */
    private final class MeasuredSubmissionScope implements SubmissionScope {

        /**
         * Provider whose path owns every physical call acquired by this scope.
         */
        private final ICraftingProvider provider;
        /**
         * Exact pattern prepared by this scope.
         */
        private final IPatternDetails pattern;
        /**
         * Physical-call ceiling selected for this provider kind before preparation begins.
         */
        private final int providerAttemptLimit;
        /**
         * Monotonic timestamp captured immediately before provider work begins.
         */
        private final long startedAtNanos;
        /**
         * Closed scopes reject reuse and double-close mistakes.
         */
        private boolean closed;

        private MeasuredSubmissionScope(
                                        ICraftingProvider provider,
                                        IPatternDetails pattern,
                                        int providerAttemptLimit,
                                        long startedAtNanos) {
            this.provider = provider;
            this.pattern = pattern;
            this.providerAttemptLimit = providerAttemptLimit;
            this.startedAtNanos = startedAtNanos;
        }

        @Override
        public Acquisition tryAcquire(CraftingDispatchTarget target) {
            requireActive();
            validateTarget(target);
            ProviderState state = states.computeIfAbsent(this.provider, ignored -> new ProviderState());
            if (attemptCount >= limits.maxAttemptsPerGrid() ||
                    !state.hasAttemptCapacity(this.providerAttemptLimit) ||
                    !hasServerSubmissionTime(this)) {
                return Acquisition.WINDOW_EXHAUSTED;
            }
            if (!state.routeAvailable(this.pattern, target)) {
                return Acquisition.ROUTE_UNAVAILABLE;
            }
            state.attemptCount = Math.incrementExact(state.attemptCount);
            attemptCount = Math.incrementExact(attemptCount);
            return Acquisition.ACQUIRED;
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
                serverBudget.record(elapsedNanos);
            } finally {
                activeSubmission = null;
            }
        }

        /**
         * Returns elapsed monotonic time and rejects a clock that moved backwards.
         */
        private long elapsedNanos() {
            long elapsedNanos = nanoClock.getAsLong() - this.startedAtNanos;
            if (elapsedNanos < 0L) {
                throw new IllegalStateException("Crafting dispatch nano clock moved backwards");
            }
            return elapsedNanos;
        }

        /**
         * Ensures only the current, open scope can mutate its parent window.
         */
        private void requireActive() {
            if (this.closed) {
                throw new IllegalStateException("Crafting dispatch submission scope is already closed");
            }
            if (activeSubmission != this) {
                throw new IllegalStateException("Crafting dispatch submission scope is not active");
            }
        }
    }

    /**
     * Server-thread-confined timing scope for one immutable provider-capacity capture.
     */
    private final class MeasuredCapacityCaptureScope implements CapacityCaptureScope {

        /**
         * Monotonic timestamp captured immediately before capacity simulation begins.
         */
        private final long startedAtNanos;
        /**
         * Closed scopes reject reuse and double-close mistakes.
         */
        private boolean closed;

        private MeasuredCapacityCaptureScope(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }

        @Override
        public void close() {
            requireActive();
            this.closed = true;
            try {
                long elapsedNanos = elapsedNanos();
                long completedNanos = Math.addExact(capacityCaptureNanos, elapsedNanos);
                int completedCount = Math.incrementExact(capacityCaptureCount);
                capacityCaptureNanos = completedNanos;
                capacityCaptureCount = completedCount;
                serverBudget.record(elapsedNanos);
            } finally {
                activeCapacityCapture = null;
            }
        }

        /**
         * Ensures only the current, open scope can mutate its parent window.
         */
        private long elapsedNanos() {
            long elapsedNanos = nanoClock.getAsLong() - this.startedAtNanos;
            if (elapsedNanos < 0L) {
                throw new IllegalStateException("Crafting dispatch nano clock moved backwards");
            }
            return elapsedNanos;
        }

        private void requireActive() {
            if (this.closed) {
                throw new IllegalStateException("Provider capacity capture scope is already closed");
            }
            if (activeCapacityCapture != this) {
                throw new IllegalStateException("Provider capacity capture scope is not active");
            }
        }
    }

    /**
     * Per-provider state keeps exhaustion and no-capacity decisions independent.
     */
    private static final class ProviderState {

        /**
         * Number of real physical submissions already attempted in this window.
         */
        private int attemptCount;

        /**
         * Provider-wide isolation covers busy, offline, locked and pre-ownership failure states.
         */
        private boolean providerUnavailable;

        /**
         * Pattern identity prevents equality-collapsing wrappers from sharing transient routing state.
         */
        private final Map<IPatternDetails, PatternState> patternStates = new IdentityHashMap<>();

        /**
         * Returns whether neither quota nor provider/pattern/target state blocks another attempt.
         */
        private boolean canAttempt(
                                   IPatternDetails pattern,
                                   @Nullable CraftingDispatchTarget target,
                                   int maxAttemptsPerProvider) {
            return hasAttemptCapacity(maxAttemptsPerProvider) && routeAvailable(pattern, target);
        }

        /**
         * Returns whether this provider still has a physical-call slot in the current grid window.
         */
        private boolean hasAttemptCapacity(int maxAttemptsPerProvider) {
            return this.attemptCount < maxAttemptsPerProvider;
        }

        /**
         * Returns whether live provider, pattern and target state still permit the selected route.
         */
        private boolean routeAvailable(
                                       IPatternDetails pattern,
                                       @Nullable CraftingDispatchTarget target) {
            if (this.providerUnavailable) {
                return false;
            }
            PatternState patternState = this.patternStates.get(pattern);
            return patternState == null || patternState.canAttempt(target);
        }

        /**
         * Applies a capacity or Blocking exclusion to the narrowest reported scope.
         */
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

    /**
     * Per-pattern state separates a complete provider-pattern rejection from exact target failures.
     */
    private static final class PatternState {

        /**
         * Whether no target for this provider-pattern pair should be prepared again in this window.
         */
        private boolean patternUnavailable;

        /**
         * Stable target identities rejected for Blocking or capacity in this window.
         */
        private final Set<CraftingDispatchTarget> unavailableTargets = new HashSet<>();

        /**
         * Returns whether the complete pattern and optional exact target remain eligible.
         */
        private boolean canAttempt(@Nullable CraftingDispatchTarget target) {
            return !this.patternUnavailable &&
                    (target == null || !this.unavailableTargets.contains(target));
        }
    }
}
