package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CraftingDispatchWindowImplTest {

    @Test
    void limitsOneProviderToSixteenPhysicalAttempts() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");

        for (int attempt = 0; attempt < CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(window.canAttempt(provider, pattern, target));
            assertTrue(window.tryAcquire(provider, pattern, target));
        }

        assertFalse(window.canAttempt(provider, pattern));
        assertFalse(window.tryAcquire(provider, pattern, target));
    }

    @Test
    void accountsByProviderIdentityInsteadOfEquality() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider first = new EqualCraftingProvider();
        ICraftingProvider second = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");
        assertNotSame(first, second);

        for (int attempt = 0; attempt < CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(window.tryAcquire(first, pattern, target));
        }

        assertFalse(window.canAttempt(first, pattern));
        assertTrue(window.canAttempt(second, pattern));
        assertTrue(window.tryAcquire(second, pattern, target));
    }

    @Test
    void targetRejectionDoesNotBlockAnotherTargetOrConsumePhysicalQuota() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget blocked = target("north");
        CraftingDispatchTarget available = target("south");

        window.recordResult(provider, pattern, blocked, CraftingDispatchStatus.BLOCKED);
        window.recordResult(provider, pattern, blocked, CraftingDispatchStatus.BLOCKED);

        assertTrue(window.canAttempt(provider, pattern));
        assertFalse(window.canAttempt(provider, pattern, blocked));
        assertFalse(window.tryAcquire(provider, pattern, blocked));
        assertTrue(window.canAttempt(provider, pattern, available));
        assertTrue(window.tryAcquire(provider, pattern, available));
        assertEquals(1, window.attemptCount());
        assertEquals(2, window.resultCount(CraftingDispatchStatus.BLOCKED));
    }

    @Test
    void scopedNoCapacityDoesNotBlockAnotherPatternOnTheSameProvider() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails unavailablePattern = new IdentityPatternDetails();
        IPatternDetails availablePattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");

        window.recordResult(provider, unavailablePattern, null, CraftingDispatchStatus.NO_CAPACITY);

        assertFalse(window.canAttempt(provider, unavailablePattern));
        assertTrue(window.canAttempt(provider, availablePattern));
        assertTrue(window.tryAcquire(provider, availablePattern, target));
    }

    @Test
    void patternCacheUsesIdentityInsteadOfEquality() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
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
            CraftingDispatchWindow window = CraftingDispatchWindow.create();
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
            CraftingDispatchWindow window = CraftingDispatchWindow.create();
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
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(3);
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchTarget target = target("north");

        for (int attempt = 0; attempt < 3; attempt++) {
            ICraftingProvider provider = new EqualCraftingProvider();
            assertTrue(window.canAttempt(provider, pattern));
            assertTrue(window.tryAcquire(provider, pattern, target));
        }

        ICraftingProvider extraProvider = new EqualCraftingProvider();
        assertEquals(3, window.attemptCount());
        assertFalse(window.canAttempt(extraProvider, pattern));
        assertFalse(window.tryAcquire(extraProvider, pattern, target));
    }

    @Test
    void independentWindowsResetAllTransientState() {
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchWindow first = CraftingDispatchWindow.create();
        CraftingDispatchWindow second = CraftingDispatchWindow.create();
        CraftingDispatchTarget target = target("north");
        first.recordResult(provider, pattern, target, CraftingDispatchStatus.BLOCKED);

        assertFalse(first.canAttempt(provider, pattern, target));
        assertTrue(second.canAttempt(provider, pattern, target));
        assertTrue(second.tryAcquire(provider, pattern, target));
    }

    @Test
    void rejectsNullProvidersBeforeMutatingState() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
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
                () -> window.tryAcquire(null, pattern, target));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.tryAcquire(provider, null, target));
        assertIllegalArgument("Crafting dispatch target must not be null",
                () -> window.tryAcquire(provider, pattern, null));
        assertIllegalArgument("Crafting dispatch provider must not be null",
                () -> window.recordResult(null, pattern, target, CraftingDispatchStatus.REJECTED));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.recordResult(provider, null, target, CraftingDispatchStatus.REJECTED));
        assertIllegalArgument("Crafting dispatch status must not be null",
                () -> window.recordResult(provider, pattern, target, null));
        assertIllegalArgument("Crafting dispatch status must not be null", () -> window.resultCount(null));
    }

    @Test
    void rejectsNonpositiveGridAttemptLimits() {
        assertIllegalArgument(
                "Grid crafting dispatch limit must be positive",
                () -> new CraftingDispatchWindowImpl(0));
        assertIllegalArgument(
                "Grid crafting dispatch limit must be positive",
                () -> new CraftingDispatchWindowImpl(-1));
    }

    private static void assertIllegalArgument(String expectedMessage, Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
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
