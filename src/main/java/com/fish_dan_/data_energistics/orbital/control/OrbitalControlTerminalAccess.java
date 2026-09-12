package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.curios.CuriosOrbitalControlTerminalAccess;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/** Resolves the one terminal-presence invariant shared by menus and the HUD. */
public final class OrbitalControlTerminalAccess {

    private OrbitalControlTerminalAccess() {}

    /** Returns the first hand currently holding the terminal, preferring the main hand. */
    public static Optional<InteractionHand> heldHand(Player player) {
        if (player.getMainHandItem().is(DEItems.ORBITAL_CONTROL_TERMINAL.get())) {
            return Optional.of(InteractionHand.MAIN_HAND);
        }
        if (player.getOffhandItem().is(DEItems.ORBITAL_CONTROL_TERMINAL.get())) {
            return Optional.of(InteractionHand.OFF_HAND);
        }
        return Optional.empty();
    }

    /** Returns whether either hand or the active dedicated Curios slot contains the terminal. */
    public static boolean hasTerminal(Player player) {
        if (heldHand(player).isPresent()) {
            return true;
        }
        return ModFlags.isCuriosLoaded() && CuriosOrbitalControlTerminalAccess.find(player).isPresent();
    }
}
