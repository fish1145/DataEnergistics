package com.fish_dan_.data_energistics.blockentity.orbital;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE-connected uplink endpoint that can bind only to a weapon already owned by its placing player.
 */
public final class OrbitalUplinkBeaconBlockEntity extends OrbitalEndpointBlockEntity {

    public OrbitalUplinkBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(
                DEBlockEntities.ORBITAL_UPLINK_BEACON_BLOCK_ENTITY.get(),
                pos,
                state,
                DEBlocks.ORBITAL_UPLINK_BEACON.get());
    }
}
