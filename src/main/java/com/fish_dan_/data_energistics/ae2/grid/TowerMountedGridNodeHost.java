package com.fish_dan_.data_energistics.ae2.grid;

import net.minecraft.core.Direction;

import appeng.api.networking.IGridNode;
import org.jetbrains.annotations.Nullable;

/**
 * Provides tower-only access to a mounted device node after its block has been authorized through AE's node-host
 * capability.
 *
 * <p>
 * The regular AE host API intentionally exposes only nodes that may form physical connections. The tower uses this
 * typed Mixin bridge solely as a virtual-binding fallback when a mounted device has no externally facing node.
 * </p>
 */
public interface TowerMountedGridNodeHost {

    /**
     * Returns the physical node owned by the device mounted on one face.
     *
     * @param side mounting face
     * @return mounted device node, or {@code null} when the face has no node-owning device
     */
    @Nullable
    IGridNode dataEnergistics$mountedGridNode(Direction side);
}
