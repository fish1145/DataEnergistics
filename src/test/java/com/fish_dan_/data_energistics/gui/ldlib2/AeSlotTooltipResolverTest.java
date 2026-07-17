package com.fish_dan_.data_energistics.gui.ldlib2;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public final class AeSlotTooltipResolverTest {

    @Test
    void emptyingTooltipWinsWithoutResolvingLowerPriorityContent() {
        List<Component> emptying = List.of(Component.literal("emptying"));
        AtomicBoolean customRequested = new AtomicBoolean();

        AeSlotTooltipResolver.Selection resolved = AeSlotTooltipResolver.select(
                emptying,
                () -> {
                    customRequested.set(true);
                    return List.of(Component.literal("custom"));
                },
                true);

        assertEquals(AeSlotTooltipResolver.Kind.EMPTYING, resolved.kind());
        assertEquals(emptying, resolved.texts());
        assertFalse(customRequested.get());
    }

    @Test
    void customTooltipReplacesTheOrdinaryItemTooltip() {
        List<Component> custom = List.of(Component.literal("custom"));
        AeSlotTooltipResolver.Selection resolved = AeSlotTooltipResolver.select(
                null,
                () -> custom,
                true);

        assertEquals(AeSlotTooltipResolver.Kind.CUSTOM, resolved.kind());
        assertEquals(custom, resolved.texts());
    }

    @Test
    void emptyCustomTooltipSuppressesOrdinaryFallback() {
        AeSlotTooltipResolver.Selection resolved = AeSlotTooltipResolver.select(
                null,
                List::of,
                true);

        assertEquals(AeSlotTooltipResolver.Kind.CUSTOM, resolved.kind());
        assertEquals(List.of(), resolved.texts());
    }

    @Test
    void ordinaryTooltipIsUsedWhenAe2ProvidesNoOverride() {
        AeSlotTooltipResolver.Selection resolved = AeSlotTooltipResolver.select(null, () -> null, true);

        assertEquals(AeSlotTooltipResolver.Kind.ORDINARY, resolved.kind());
    }

    @Test
    void carriedStackSuppressesOrdinaryFallbackWhenNoSpecialTooltipExists() {
        AeSlotTooltipResolver.Selection resolved = AeSlotTooltipResolver.select(
                null,
                () -> null,
                false);

        assertEquals(AeSlotTooltipResolver.Kind.NONE, resolved.kind());
    }
}
