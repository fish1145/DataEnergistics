package com.fish_dan_.data_energistics.common.crafting.trinity.planning.request;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import net.minecraft.world.entity.player.Player;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Request-scoped player selection carried through AE2's existing action-source context boundary.
 *
 * @param quantityMode quantity interpretation selected on the current Craft Amount page
 */
public record TrinityCraftingRequestContext(CraftingQuantityMode quantityMode) {

    /**
     * Rejects incomplete request metadata before it enters asynchronous planning.
     */
    public TrinityCraftingRequestContext {
        if (quantityMode == null) {
            throw new IllegalArgumentException("A Trinity crafting request context requires a quantity mode");
        }
    }

    /**
     * Decorates an existing action source without changing its player, machine, or third-party contexts.
     *
     * @param source       original AE2 source
     * @param quantityMode player-selected quantity interpretation
     * @return action source carrying this request context
     */
    public static IActionSource attach(IActionSource source, CraftingQuantityMode quantityMode) {
        return new ContextActionSource(source, new TrinityCraftingRequestContext(quantityMode));
    }

    /**
     * Resolves the explicit player selection or the COMMON default for machine and external requests.
     *
     * @param source      current simulation action source, which AE2 permits to be absent
     * @param defaultMode COMMON default
     * @return request quantity mode
     */
    public static CraftingQuantityMode resolve(@Nullable IActionSource source, CraftingQuantityMode defaultMode) {
        if (source == null) {
            return defaultMode;
        }
        return source.context(TrinityCraftingRequestContext.class)
                .map(TrinityCraftingRequestContext::quantityMode)
                .orElse(defaultMode);
    }

    /**
     * Narrow decorator that participates in AE2's explicit context API instead of maintaining a static player map.
     */
    private record ContextActionSource(
                                       IActionSource delegate,
                                       TrinityCraftingRequestContext requestContext)
            implements IActionSource {

        private ContextActionSource {
            if (delegate == null || requestContext == null) {
                throw new IllegalArgumentException("A Trinity action-source context requires a delegate and metadata");
            }
        }

        @Override
        public Optional<Player> player() {
            return this.delegate.player();
        }

        @Override
        public Optional<IActionHost> machine() {
            return this.delegate.machine();
        }

        @Override
        public <T> Optional<T> context(Class<T> key) {
            if (key == TrinityCraftingRequestContext.class) {
                return Optional.of(key.cast(this.requestContext));
            }
            return this.delegate.context(key);
        }
    }
}
