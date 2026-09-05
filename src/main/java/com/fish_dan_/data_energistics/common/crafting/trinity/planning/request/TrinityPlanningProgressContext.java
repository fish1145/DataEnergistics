package com.fish_dan_.data_energistics.common.crafting.trinity.planning.request;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;

import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Request-local bridge carrying only a detached planning progress reporter through AE2's action-source context API.
 *
 * <p>
 * The reporter owns no menu or game object. It is closed by the confirmation-menu revision before cancellation, so
 * an asynchronous planner cannot resurrect a stale user interface.
 * </p>
 */
public record TrinityPlanningProgressContext(TrinityPlanningProgressReporter reporter) {

    /** Decorates an action source while preserving every existing AE2 and mod context. */
    public static IActionSource attach(IActionSource source, TrinityPlanningProgressReporter reporter) {
        return new ContextActionSource(source, new TrinityPlanningProgressContext(reporter));
    }

    /** Resolves the optional menu-owned reporter for this request. */
    public static Optional<TrinityPlanningProgressReporter> resolve(@Nullable IActionSource source) {
        return source == null ? Optional.empty() : source.context(TrinityPlanningProgressContext.class)
                .map(TrinityPlanningProgressContext::reporter);
    }

    private record ContextActionSource(IActionSource delegate, TrinityPlanningProgressContext progressContext)
            implements IActionSource {

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
            if (key == TrinityPlanningProgressContext.class) {
                return Optional.of(key.cast(this.progressContext));
            }
            return this.delegate.context(key);
        }
    }
}
