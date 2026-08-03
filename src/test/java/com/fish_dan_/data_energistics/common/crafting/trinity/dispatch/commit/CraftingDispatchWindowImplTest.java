package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchExhaustion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CraftingDispatchWindowImplTest {

    @Test
    void limitsOneProviderToItsConfiguredPhysicalAttempts() {
        CraftingDispatchLimits limits = new CraftingDispatchLimits(10, 2, 1_000L);
        CraftingDispatchWindow window = fixedWindow(limits);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");

        for (int attempt = 0; attempt < limits.maxAttemptsPerProvider(); attempt++) {
            assertTrue(window.canAttempt(provider, pattern, target));
            assertTrue(acquire(window, provider, pattern, target));
        }

        assertFalse(window.canAttempt(provider, pattern));
        assertEquals(limits.maxAttemptsPerProvider(), window.attemptCount());
        assertEquals(limits.maxAttemptsPerProvider(), window.serverSubmissionCount());
    }

    @Test
    void accountsByProviderIdentityInsteadOfEquality() {
        CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
        ICraftingProvider first = new EqualCraftingProvider();
        ICraftingProvider second = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");
        assertNotSame(first, second);

        for (int attempt = 0; attempt < CraftingDispatchLimits.DEFAULT_MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(acquire(window, first, pattern, target));
        }

        assertFalse(window.canAttempt(first, pattern));
        assertTrue(window.canAttempt(second, pattern));
        assertTrue(acquire(window, second, pattern, target));
    }

    @Test
    void targetRejectionDoesNotBlockAnotherTargetOrConsumePhysicalQuota() {
        CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget blocked = target("north");
        CraftingDispatchTarget available = target("south");

        window.recordResult(provider, pattern, blocked, CraftingDispatchStatus.BLOCKED);
        window.recordResult(provider, pattern, blocked, CraftingDispatchStatus.BLOCKED);

        assertTrue(window.canAttempt(provider, pattern));
        assertFalse(window.canAttempt(provider, pattern, blocked));
        assertFalse(acquire(window, provider, pattern, blocked));
        assertTrue(window.canAttempt(provider, pattern, available));
        assertTrue(acquire(window, provider, pattern, available));
        assertEquals(1, window.attemptCount());
        assertEquals(2, window.serverSubmissionCount());
        assertEquals(2, window.resultCount(CraftingDispatchStatus.BLOCKED));
    }

    @Test
    void scopedNoCapacityDoesNotBlockAnotherPatternOnTheSameProvider() {
        CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails unavailablePattern = new IdentityPatternDetails();
        IPatternDetails availablePattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");

        window.recordResult(provider, unavailablePattern, null, CraftingDispatchStatus.NO_CAPACITY);

        assertFalse(window.canAttempt(provider, unavailablePattern));
        assertTrue(window.canAttempt(provider, availablePattern));
        assertTrue(acquire(window, provider, availablePattern, target));
    }

    @Test
    void patternCacheUsesIdentityInsteadOfEquality() {
        CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails blocked = new EqualPatternDetails();
        IPatternDetails available = new EqualPatternDetails();
        assertNotSame(blocked, available);

        window.recordResult(provider, blocked, null, CraftingDispatchStatus.NO_CAPACITY);

        assertFalse(window.canAttempt(provider, blocked));
        assertTrue(window.canAttempt(provider, available));
    }

    @Test
    void providerWideStatusesIsolateEveryPatternAndTarget() {
        for (CraftingDispatchStatus status : List.of(
                CraftingDispatchStatus.LOCKED,
                CraftingDispatchStatus.BUSY,
                CraftingDispatchStatus.OFFLINE,
                CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP)) {
            CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
            ICraftingProvider provider = new EqualCraftingProvider();
            IPatternDetails firstPattern = new IdentityPatternDetails();
            IPatternDetails secondPattern = new IdentityPatternDetails();

            window.recordResult(provider, firstPattern, null, status);

            assertFalse(window.canAttempt(provider, firstPattern), status.name());
            assertFalse(window.canAttempt(provider, secondPattern, target("south")), status.name());
            assertEquals(1, window.resultCount(status), status.name());
        }
    }

    @Test
    void observationalStatusesDoNotCreateNegativeCacheEntries() {
        for (CraftingDispatchStatus status : List.of(
                CraftingDispatchStatus.ACCEPTED,
                CraftingDispatchStatus.STALE,
                CraftingDispatchStatus.REJECTED,
                CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP)) {
            CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
            ICraftingProvider provider = new EqualCraftingProvider();
            IPatternDetails pattern = new IdentityPatternDetails();
            CraftingDispatchTarget target = target("north");

            window.recordResult(provider, pattern, target, status);

            assertTrue(window.canAttempt(provider, pattern, target), status.name());
            assertEquals(1, window.resultCount(status), status.name());
        }
    }

    @Test
    void limitsTotalPhysicalAttemptsAcrossDifferentProviders() {
        CraftingDispatchLimits limits = new CraftingDispatchLimits(3, 3, 1_000L);
        CraftingDispatchWindow window = fixedWindow(limits);
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");

        for (int attempt = 0; attempt < limits.maxAttemptsPerGrid(); attempt++) {
            ICraftingProvider provider = new EqualCraftingProvider();
            assertTrue(window.canAttempt(provider, pattern));
            assertTrue(acquire(window, provider, pattern, target));
        }

        ICraftingProvider extraProvider = new EqualCraftingProvider();
        assertEquals(3, window.attemptCount());
        assertFalse(window.canAttempt(extraProvider, pattern));
        assertEquals(CraftingDispatchExhaustion.GRID_CALL_BUDGET, window.exhaustion());
    }

    @Test
    void activeSubmissionAtTimeBoundaryDoesNotAcquirePhysicalCall() {
        AtomicLong nanoClock = new AtomicLong();
        CraftingDispatchLimits limits = new CraftingDispatchLimits(4, 4, 100L);
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(limits, nanoClock::get);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();

        try (CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern)) {
            nanoClock.set(limits.maxServerSubmissionNanos());
            assertFalse(submission.tryAcquire(target("north")));
        }

        assertEquals(0, window.attemptCount());
        assertEquals(1, window.serverSubmissionCount());
        assertEquals(100L, window.serverSubmissionNanos());
        assertEquals(CraftingDispatchExhaustion.SERVER_TIME_BUDGET, window.exhaustion());
    }

    @Test
    void inFlightPhysicalCallMayCrossTimeBoundaryButLaterWorkStops() {
        AtomicLong nanoClock = new AtomicLong();
        CraftingDispatchLimits limits = new CraftingDispatchLimits(4, 4, 100L);
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(limits, nanoClock::get);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();

        try (CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern)) {
            nanoClock.set(99L);
            assertTrue(submission.tryAcquire(target("north")));
            nanoClock.set(101L);
        }

        assertEquals(1, window.attemptCount());
        assertEquals(101L, window.serverSubmissionNanos());
        assertFalse(window.canAttempt(provider, pattern));
        assertEquals(CraftingDispatchExhaustion.SERVER_TIME_BUDGET, window.exhaustion());
    }

    @Test
    void accumulatesOnlyMeasuredScopeDurations() {
        AtomicLong nanoClock = new AtomicLong(10L);
        CraftingDispatchLimits limits = new CraftingDispatchLimits(4, 4, 1_000L);
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(limits, nanoClock::get);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();

        try (CraftingDispatchWindow.SubmissionScope ignored = window.beginSubmission(provider, pattern)) {
            nanoClock.set(40L);
        }
        nanoClock.set(100L);
        try (CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern)) {
            assertTrue(submission.tryAcquire(target("north")));
            nanoClock.set(125L);
        }

        assertEquals(2, window.serverSubmissionCount());
        assertEquals(55L, window.serverSubmissionNanos());
        assertEquals(1, window.attemptCount());
        assertEquals(CraftingDispatchExhaustion.NONE, window.exhaustion());
    }

    @Test
    void capacityCaptureUsesAnIndependentTimeBudget() {
        AtomicLong nanoClock = new AtomicLong();
        CraftingDispatchLimits limits = new CraftingDispatchLimits(4, 4, 1_000L, 100L);
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(limits, nanoClock::get);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();

        try (CraftingDispatchWindow.CapacityCaptureScope ignored = window.beginProviderCapacityCapture()) {
            nanoClock.set(limits.maxCapacityCaptureNanos());
        }

        assertEquals(1, window.capacityCaptureCount());
        assertEquals(100L, window.capacityCaptureNanos());
        assertFalse(window.canCaptureProviderCapacity());
        assertTrue(window.canAttempt(provider, pattern));
        assertEquals(0L, window.serverSubmissionNanos());
        assertEquals(CraftingDispatchExhaustion.NONE, window.exhaustion());
    }

    @Test
    void independentWindowsResetAllTransientStateAndBudgets() {
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchWindow first = fixedWindow(new CraftingDispatchLimits(4, 4, 1L));
        CraftingDispatchWindow second = fixedWindow(new CraftingDispatchLimits(4, 4, 1L));
        CraftingDispatchTarget target = target("north");
        first.recordResult(provider, pattern, target, CraftingDispatchStatus.BLOCKED);

        assertFalse(first.canAttempt(provider, pattern, target));
        assertTrue(second.canAttempt(provider, pattern, target));
        assertTrue(acquire(second, provider, pattern, target));
    }

    @Test
    void rejectsNestedAndClosedSubmissionScopes() {
        CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern);

        assertIllegalState(
                "Crafting dispatch submission scopes must not be nested",
                () -> window.beginSubmission(provider, pattern));
        submission.close();
        assertIllegalState(
                "Crafting dispatch submission scope is already closed",
                submission::close);
    }

    @Test
    void rejectsBackwardClockAndReleasesFailedScope() {
        AtomicLong nanoClock = new AtomicLong(100L);
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(
                new CraftingDispatchLimits(4, 4, 100L),
                nanoClock::get);
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern);
        nanoClock.set(99L);

        assertIllegalState(
                "Crafting dispatch nano clock moved backwards",
                submission::close);
        assertEquals(0, window.serverSubmissionCount());
        assertEquals(0L, window.serverSubmissionNanos());

        nanoClock.set(200L);
        try (CraftingDispatchWindow.SubmissionScope ignored = window.beginSubmission(provider, pattern)) {
            assertTrue(window.canAttempt(provider, pattern));
        }
    }

    @Test
    void rejectsNullDispatchInputsBeforeMutatingState() {
        CraftingDispatchWindow window = fixedWindow(CraftingDispatchLimits.DEFAULT);
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");
        ICraftingProvider provider = new EqualCraftingProvider();

        assertIllegalArgument("Crafting dispatch provider must not be null", () -> window.canAttempt(null, pattern));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.canAttempt(provider, null));
        assertIllegalArgument("Crafting dispatch provider must not be null",
                () -> window.canAttempt(null, pattern, target));
        assertIllegalArgument("Crafting dispatch target must not be null",
                () -> window.canAttempt(provider, pattern, null));
        assertIllegalArgument("Crafting dispatch provider must not be null",
                () -> window.beginSubmission(null, pattern));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.beginSubmission(provider, null));
        try (CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern)) {
            assertIllegalArgument("Crafting dispatch target must not be null",
                    () -> submission.tryAcquire(null));
        }
        assertIllegalArgument("Crafting dispatch provider must not be null",
                () -> window.recordResult(null, pattern, target, CraftingDispatchStatus.REJECTED));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.recordResult(provider, null, target, CraftingDispatchStatus.REJECTED));
        assertIllegalArgument("Crafting dispatch status must not be null",
                () -> window.recordResult(provider, pattern, target, null));
        assertIllegalArgument("Crafting dispatch status must not be null", () -> window.resultCount(null));
        assertIllegalArgument(
                "Crafting dispatch limits must not be null",
                () -> new CraftingDispatchWindowImpl(null, () -> 0L));
        assertIllegalArgument(
                "Crafting dispatch nano clock must not be null",
                () -> new CraftingDispatchWindowImpl(CraftingDispatchLimits.DEFAULT, null));
    }

    @Test
    void rejectsNonpositiveHardLimits() {
        assertIllegalArgument(
                "Grid crafting dispatch limit must be positive",
                () -> new CraftingDispatchLimits(0, 1, 1L));
        assertIllegalArgument(
                "Provider crafting dispatch limit must be positive",
                () -> new CraftingDispatchLimits(1, 0, 1L));
        assertIllegalArgument(
                "Server crafting submission time limit must be positive",
                () -> new CraftingDispatchLimits(1, 1, 0L));
        assertIllegalArgument(
                "Provider capacity capture time limit must be positive",
                () -> new CraftingDispatchLimits(1, 1, 1L, 0L));
    }

    private static CraftingDispatchWindow fixedWindow(CraftingDispatchLimits limits) {
        return new CraftingDispatchWindowImpl(limits, () -> 0L);
    }

    private static boolean acquire(
                                   CraftingDispatchWindow window,
                                   ICraftingProvider provider,
                                   IPatternDetails pattern,
                                   CraftingDispatchTarget target) {
        try (CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(provider, pattern)) {
            return submission.tryAcquire(target);
        }
    }

    private static void assertIllegalArgument(String expectedMessage, Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static void assertIllegalState(String expectedMessage, Runnable action) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, action::run);
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static CraftingDispatchTarget target(String identity) {
        return new CraftingDispatchTarget(identity);
    }

    /** Provider whose equality deliberately collapses instances to verify identity-based accounting. */
    private static final class EqualCraftingProvider implements ICraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Dispatch window tests never submit crafting inputs");
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof EqualCraftingProvider;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    /** Distinct pattern identity used only as a dispatch-window cache key. */
    private static final class IdentityPatternDetails implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            throw new UnsupportedOperationException("Dispatch window tests never inspect pattern definitions");
        }

        @Override
        public IInput[] getInputs() {
            throw new UnsupportedOperationException("Dispatch window tests never inspect pattern inputs");
        }

        @Override
        public List<GenericStack> getOutputs() {
            throw new UnsupportedOperationException("Dispatch window tests never inspect pattern outputs");
        }
    }

    /** Pattern whose equality deliberately collapses instances to verify identity-based cache isolation. */
    private static final class EqualPatternDetails implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            throw new UnsupportedOperationException("Dispatch window tests never inspect pattern definitions");
        }

        @Override
        public IInput[] getInputs() {
            throw new UnsupportedOperationException("Dispatch window tests never inspect pattern inputs");
        }

        @Override
        public List<GenericStack> getOutputs() {
            throw new UnsupportedOperationException("Dispatch window tests never inspect pattern outputs");
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof EqualPatternDetails;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
