package com.fish_dan_.data_energistics.common.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeableObject;
import org.jspecify.annotations.Nullable;

/**
 * Menu-thread view of one block or mounted beam endpoint.
 * Live connections belong to its state, never a global cache. World and node may be absent during loading/removal.
 */
public interface BeamEndpoint extends IUpgradeableObject {

    /** Returns the current world, or null before attachment. */
    @Nullable
    Level beamLevel();

    /** Returns the attached endpoint's immutable host position. */
    BlockPos beamPosition();

    /** Returns its emitter direction; parts use their mounting face. */
    Direction beamFacing();

    /** Returns the node wrapper, whose underlying node can be absent during lifecycle transitions. */
    IManagedGridNode beamNode();

    /** Returns the endpoint-owned persistent and live state. */
    BeamEndpointState beamState();

    /** Returns an RGB cable color, or -1 for an uncolored endpoint. */
    int beamColor();

    /** Schedules visual synchronization and, when requested, persistent saving on the world thread. */
    void beamChanged(boolean persist);
}
