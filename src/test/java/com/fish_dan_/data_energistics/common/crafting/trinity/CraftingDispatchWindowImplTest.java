package com.fish_dan_.data_energistics.common.crafting.trinity;

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

        for (int attempt = 0; attempt < CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(window.canAttempt(provider, pattern));
            assertTrue(window.tryAcquire(provider, pattern));
        }

        assertFalse(window.canAttempt(provider, pattern));
        assertFalse(window.tryAcquire(provider, pattern));
    }

    @Test
    void accountsByProviderIdentityInsteadOfEquality() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider first = new EqualCraftingProvider();
        ICraftingProvider second = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        assertNotSame(first, second);

        for (int attempt = 0; attempt < CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(window.tryAcquire(first, pattern));
        }

        assertFalse(window.canAttempt(first, pattern));
        assertTrue(window.canAttempt(second, pattern));
        assertTrue(window.tryAcquire(second, pattern));
    }

    @Test
    void unavailableProviderStaysBlockedWithoutAQuotaAcquisition() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();

        window.markUnavailable(provider, pattern);
        window.markUnavailable(provider, pattern);

        assertFalse(window.canAttempt(provider, pattern));
        assertFalse(window.tryAcquire(provider, pattern));
    }

    @Test
    void unavailablePatternDoesNotBlockAnotherPatternOnTheSameProvider() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails unavailablePattern = new IdentityPatternDetails();
        IPatternDetails availablePattern = new IdentityPatternDetails();

        window.markUnavailable(provider, unavailablePattern);

        assertFalse(window.canAttempt(provider, unavailablePattern));
        assertTrue(window.canAttempt(provider, availablePattern));
        assertTrue(window.tryAcquire(provider, availablePattern));
    }

    @Test
    void limitsTotalPhysicalAttemptsAcrossDifferentProviders() {
        CraftingDispatchWindow window = new CraftingDispatchWindowImpl(3);
        IPatternDetails pattern = new IdentityPatternDetails();

        for (int attempt = 0; attempt < 3; attempt++) {
            ICraftingProvider provider = new EqualCraftingProvider();
            assertTrue(window.canAttempt(provider, pattern));
            assertTrue(window.tryAcquire(provider, pattern));
        }

        ICraftingProvider extraProvider = new EqualCraftingProvider();
        assertEquals(3, window.attemptCount());
        assertFalse(window.canAttempt(extraProvider, pattern));
        assertFalse(window.tryAcquire(extraProvider, pattern));
    }

    @Test
    void independentWindowsResetAllTransientState() {
        ICraftingProvider provider = new EqualCraftingProvider();
        IPatternDetails pattern = new IdentityPatternDetails();
        CraftingDispatchWindow first = CraftingDispatchWindow.create();
        CraftingDispatchWindow second = CraftingDispatchWindow.create();
        first.markUnavailable(provider, pattern);

        assertFalse(first.canAttempt(provider, pattern));
        assertTrue(second.canAttempt(provider, pattern));
        assertTrue(second.tryAcquire(provider, pattern));
    }

    @Test
    void rejectsNullProvidersBeforeMutatingState() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        IPatternDetails pattern = new IdentityPatternDetails();

        assertIllegalArgument("Crafting dispatch provider must not be null", () -> window.canAttempt(null, pattern));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.canAttempt(new EqualCraftingProvider(), null));
        assertIllegalArgument("Crafting dispatch provider must not be null", () -> window.tryAcquire(null, pattern));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.tryAcquire(new EqualCraftingProvider(), null));
        assertIllegalArgument("Crafting dispatch provider must not be null", () -> window.markUnavailable(null, pattern));
        assertIllegalArgument("Crafting dispatch pattern must not be null",
                () -> window.markUnavailable(new EqualCraftingProvider(), null));
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
}
