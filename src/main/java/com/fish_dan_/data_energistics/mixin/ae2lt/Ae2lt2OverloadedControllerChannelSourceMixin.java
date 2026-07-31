package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.ae2.ChannelHubControllerSource;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adapts Thunderbolt's AE2LT 2.0 per-controller supply into Channel Hub capacity accounting.
 */
@Mixin(value = OverloadedControllerBlockEntity.class, remap = false)
public abstract class Ae2lt2OverloadedControllerChannelSourceMixin implements ChannelHubControllerSource {

    /**
     * Uses Thunderbolt's public helper so configured capacity stays identical to its max-flow source model.
     */
    @Unique
    @Override
    public int getChannelHubSupply(int cableCapacityFactor) {
        return ThunderboltOverloadedChannelOwnerHelperInvoker.invokeSupplyPerController(cableCapacityFactor);
    }
}
