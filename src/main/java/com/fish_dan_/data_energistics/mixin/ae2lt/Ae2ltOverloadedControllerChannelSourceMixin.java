package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.ae2.ChannelHubControllerSource;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adapts AE2LT 1.1.4's configured per-controller supply into Channel Hub capacity accounting.
 */
@Mixin(value = OverloadedControllerBlockEntity.class, remap = false)
public abstract class Ae2ltOverloadedControllerChannelSourceMixin implements ChannelHubControllerSource {

    /**
     * Delegates to AE2LT so configuration reloads and overflow clamping remain authoritative.
     */
    @Unique
    @Override
    public int getChannelHubSupply(int cableCapacityFactor) {
        return OverloadedChannelOwnerHelper.supplyPerController(cableCapacityFactor);
    }
}
