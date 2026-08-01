package com.fish_dan_.data_energistics.blockentity.tower.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable per-device channel and FE display snapshot.
 *
 * @param bindingAnchor     owning binding anchor
 * @param deviceKey         stable device identity
 * @param displayItemId     item used to represent the concrete node in the tower UI
 * @param displayName       node or item display name captured while the target Grid is loaded
 * @param requestedChannels zero or one requested channels
 * @param grantedChannels   zero or one granted channels
 * @param state             current device lifecycle state
 * @param failure           concise diagnostic reason, empty when successful
 * @param storedFe          frozen FE stored amount
 * @param capacityFe        frozen FE capacity
 * @param canExtractFe      whether the frozen endpoint can supply FE
 * @param canReceiveFe      whether the frozen endpoint can receive FE
 */
public record TowerVirtualDeviceSnapshot(BlockPos bindingAnchor,
                                         TowerDeviceKey deviceKey,
                                         ResourceLocation displayItemId,
                                         String displayName,
                                         int requestedChannels,
                                         int grantedChannels,
                                         TowerVirtualDeviceState state,
                                         String failure,
                                         long storedFe,
                                         long capacityFe,
                                         boolean canExtractFe,
                                         boolean canReceiveFe) {

    /** Validates and normalizes one device snapshot. */
    public TowerVirtualDeviceSnapshot {
        bindingAnchor = bindingAnchor.immutable();
        if (requestedChannels < 0 || requestedChannels > 1 || grantedChannels < 0 || grantedChannels > requestedChannels) {
            throw new IllegalArgumentException("Tower virtual device channel counts must be zero or one");
        }
        if (storedFe < 0 || capacityFe < storedFe) {
            throw new IllegalArgumentException("Tower virtual device FE snapshot is invalid");
        }
    }
}
