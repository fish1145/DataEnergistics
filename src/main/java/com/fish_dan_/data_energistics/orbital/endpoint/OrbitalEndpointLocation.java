package com.fish_dan_.data_energistics.orbital.endpoint;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Dimension-qualified immutable block location used as an endpoint identity.
 */
public record OrbitalEndpointLocation(ResourceLocation dimensionId, BlockPos pos) {

    public OrbitalEndpointLocation {
        pos = pos.immutable();
    }
}
