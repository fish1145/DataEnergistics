package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import net.minecraft.world.entity.player.Player;

/**
 * Menu capability used by generic payload handlers to locate and validate a host UI coordinator.
 */
public interface HostUiCoordinatorHolder {

    /**
     * Returns the endpoint created with this exact menu instance.
     *
     * @return current menu's coordinator
     */
    HostUiCoordinator getHostUiCoordinator();

    /**
     * Revalidates the player, menu holder, and authoritative business host before a server mutation.
     *
     * @param player player whose current container delivered the request
     * @return whether this menu may apply a host UI membership change
     */
    boolean isHostUiAvailable(Player player);
}
