package com.fish_dan_.data_energistics.api.registry.terminal;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Menu-opening context supplied to a universal-terminal adapter without exposing the concrete terminal part.
 *
 * <p>
 * The context owns the implementation-specific host bridge. Integrations request only the menu host interface they
 * understand and therefore remain independent from Data Energistics' part implementation.
 * </p>
 */
public interface UniversalTerminalContext {

    /**
     * Returns the player opening the selected terminal.
     *
     * @return active menu player
     */
    @NotNull
    Player player();

    /**
     * Resolves the standard menu host projection for the requested interface.
     *
     * @param hostInterface interface required by the target menu
     * @param <T>           requested host type
     * @return compatible host projection, or {@code null} when the terminal cannot provide it
     */
    <T> @Nullable T resolveDefaultMenuHost(@NotNull Class<T> hostInterface);
}
