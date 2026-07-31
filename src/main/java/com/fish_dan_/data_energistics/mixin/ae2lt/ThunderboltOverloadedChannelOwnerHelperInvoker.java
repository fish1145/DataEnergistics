package com.fish_dan_.data_energistics.mixin.ae2lt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls Thunderbolt's public configured-supply helper without adding its unpublished API to the compile dependency set.
 */
@Pseudo
@Mixin(targets = "com.moakiee.thunderbolt.ae2.channel.OverloadedChannelOwnerHelper", remap = false)
public interface ThunderboltOverloadedChannelOwnerHelperInvoker {

    /**
     * Invokes Thunderbolt's static supply calculation after Mixin binds this accessor.
     *
     * @param cableCapacityFactor AE2 channel-mode multiplier
     * @return configured and clamped controller supply
     */
    @Invoker("supplyPerController")
    static int invokeSupplyPerController(int cableCapacityFactor) {
        throw new AssertionError("Thunderbolt supply invoker was not transformed");
    }
}
