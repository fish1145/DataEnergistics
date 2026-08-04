package com.fish_dan_.data_energistics.common.crafting.trinity.planning.request;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import net.minecraft.world.entity.player.Player;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class TrinityCraftingRequestContextTest {

    @Test
    void explicitModeOverridesCommonDefaultWithoutHidingDelegateContext() {
        RequestMarker marker = new RequestMarker();
        IActionSource delegate = new MarkerActionSource(marker);
        IActionSource contextual = TrinityCraftingRequestContext.attach(
                delegate,
                CraftingQuantityMode.FINAL_TOTAL);

        assertEquals(
                CraftingQuantityMode.FINAL_TOTAL,
                TrinityCraftingRequestContext.resolve(contextual, CraftingQuantityMode.NET_NEW));
        assertSame(marker, contextual.context(RequestMarker.class).orElseThrow());
        assertEquals(delegate.player(), contextual.player());
        assertEquals(delegate.machine(), contextual.machine());
    }

    @Test
    void commonDefaultAppliesWhenRequestHasNoExplicitMode() {
        assertEquals(
                CraftingQuantityMode.FINAL_TOTAL,
                TrinityCraftingRequestContext.resolve(IActionSource.empty(), CraftingQuantityMode.FINAL_TOTAL));
        assertEquals(
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingRequestContext.resolve(null, CraftingQuantityMode.NET_NEW));
    }

    private static final class RequestMarker {}

    private record MarkerActionSource(RequestMarker marker) implements IActionSource {

        @Override
        public Optional<Player> player() {
            return Optional.empty();
        }

        @Override
        public Optional<IActionHost> machine() {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> context(Class<T> key) {
            return key == RequestMarker.class ? Optional.of(key.cast(this.marker)) : Optional.empty();
        }
    }
}
