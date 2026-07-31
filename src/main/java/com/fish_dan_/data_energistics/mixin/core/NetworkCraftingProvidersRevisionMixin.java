package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingProviderRevision;

import appeng.me.service.helpers.NetworkCraftingProviders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Supplies the true mutation revision needed by time-sliced Trinity graph invalidation.
 */
@Mixin(NetworkCraftingProviders.class)
public abstract class NetworkCraftingProvidersRevisionMixin implements TrinityCraftingProviderRevision {

    /**
     * Monotonic mutation generation independent from AE2's tick-valued diagnostic timestamp.
     */
    @Unique
    private long dataEnergistics$craftingProviderRevision;

    @Inject(method = "setLastModifiedOnTick", at = @At("RETURN"))
    private void dataEnergistics$advanceCraftingProviderRevision(CallbackInfo ci) {
        this.dataEnergistics$craftingProviderRevision = Math.incrementExact(this.dataEnergistics$craftingProviderRevision);
    }

    @Override
    public long trinityCraftingProviderRevision() {
        return this.dataEnergistics$craftingProviderRevision;
    }
}
