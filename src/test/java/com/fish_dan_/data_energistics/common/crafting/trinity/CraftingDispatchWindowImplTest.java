package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
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

        for (int attempt = 0; attempt < CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(window.canAttempt(provider));
            assertTrue(window.tryAcquire(provider));
        }

        assertFalse(window.canAttempt(provider));
        assertFalse(window.tryAcquire(provider));
    }

    @Test
    void accountsByProviderIdentityInsteadOfEquality() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider first = new EqualCraftingProvider();
        ICraftingProvider second = new EqualCraftingProvider();
        assertNotSame(first, second);

        for (int attempt = 0; attempt < CraftingDispatchWindow.MAX_ATTEMPTS_PER_PROVIDER; attempt++) {
            assertTrue(window.tryAcquire(first));
        }

        assertFalse(window.canAttempt(first));
        assertTrue(window.canAttempt(second));
        assertTrue(window.tryAcquire(second));
    }

    @Test
    void unavailableProviderStaysBlockedWithoutAQuotaAcquisition() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();
        ICraftingProvider provider = new EqualCraftingProvider();

        window.markUnavailable(provider);
        window.markUnavailable(provider);

        assertFalse(window.canAttempt(provider));
        assertFalse(window.tryAcquire(provider));
    }

    @Test
    void independentWindowsResetAllTransientState() {
        ICraftingProvider provider = new EqualCraftingProvider();
        CraftingDispatchWindow first = CraftingDispatchWindow.create();
        CraftingDispatchWindow second = CraftingDispatchWindow.create();
        first.markUnavailable(provider);

        assertFalse(first.canAttempt(provider));
        assertTrue(second.canAttempt(provider));
        assertTrue(second.tryAcquire(provider));
    }

    @Test
    void rejectsNullProvidersBeforeMutatingState() {
        CraftingDispatchWindow window = CraftingDispatchWindow.create();

        assertIllegalArgument(() -> window.canAttempt(null));
        assertIllegalArgument(() -> window.tryAcquire(null));
        assertIllegalArgument(() -> window.markUnavailable(null));
    }

    private static void assertIllegalArgument(Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals("Crafting dispatch provider must not be null", exception.getMessage());
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
}
