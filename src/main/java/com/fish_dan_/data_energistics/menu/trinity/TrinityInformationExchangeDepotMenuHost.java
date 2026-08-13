package com.fish_dan_.data_energistics.menu.trinity;

import com.fish_dan_.data_energistics.blockentity.TrinityInformationExchangeDepotBlockEntity.StorageMode;

import net.minecraft.world.entity.player.Player;

/**
 * Defines the narrow server-authoritative mode boundary exposed by one Trinity information exchange depot.
 */
public interface TrinityInformationExchangeDepotMenuHost {

    /**
     * Verifies that the hatch block entity still occupies its original block and remains within interaction distance.
     *
     * @param player player whose open menu is being validated
     * @return whether the physical menu route is still current
     */
    boolean isInformationExchangeDepotMenuValid(Player player);

    StorageMode informationExchangeMode();

    boolean setInformationExchangeMode(Player player, StorageMode mode);
}
