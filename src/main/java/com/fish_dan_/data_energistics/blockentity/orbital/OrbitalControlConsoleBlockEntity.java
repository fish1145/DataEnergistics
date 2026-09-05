package com.fish_dan_.data_energistics.blockentity.orbital;

import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE-connected console endpoint that persists only the stable weapon identity assigned during placement.
 */
public final class OrbitalControlConsoleBlockEntity extends OrbitalEndpointBlockEntity {

    public OrbitalControlConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(
                DEBlockEntities.ORBITAL_CONTROL_CONSOLE_BLOCK_ENTITY.get(),
                pos,
                state,
                DEBlocks.ORBITAL_CONTROL_CONSOLE.get());
    }

    @Override
    public OrbitalEndpointKind endpointKind() {
        return OrbitalEndpointKind.CONTROL_CONSOLE;
    }
}
