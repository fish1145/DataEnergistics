package com.fish_dan_.data_energistics.mixin.core.patternprovider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationSink;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.helpers.NetworkCraftingProviders;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mirrors AE2's exact private provider-state mount lifecycle into the Trinity publication index.
 */
@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$ProviderState", remap = false)
public abstract class NetworkCraftingProviderStatePublicationMixin {

    /**
     * Live provider retained by AE2 for this one registration.
     */
    @Shadow(remap = false)
    @Final
    private ICraftingProvider provider;

    /**
     * Exact pattern snapshot that AE2 mounted for this registration.
     */
    @Shadow(remap = false)
    @Final
    private List<IPatternDetails> patterns;

    /**
     * Publication identity valid only between this state object's mount and unmount calls.
     */
    @Unique
    private CraftingProviderId dataEnergistics$providerId;

    /**
     * Publishes only after AE2 has mounted the complete captured state.
     */
    @Inject(method = "mount", at = @At("RETURN"), remap = false, require = 1)
    private void dataEnergistics$publish(NetworkCraftingProviders methods, CallbackInfo ci) {
        if (this.dataEnergistics$providerId != null) {
            throw new IllegalStateException("AE2 crafting provider state was mounted twice");
        }
        CraftingProviderPublicationSink publicationSink = (CraftingProviderPublicationSink) methods;
        this.dataEnergistics$providerId = publicationSink.dataEnergistics$publishProvider(
                this.provider,
                this.patterns);
        PatternProviderRuntimeBindings.bind(this.dataEnergistics$providerId, this.provider);
    }

    /**
     * Invalidates the ID only after AE2 has unmounted the complete captured state.
     */
    @Inject(method = "unmount", at = @At("RETURN"), remap = false, require = 1)
    private void dataEnergistics$unpublish(NetworkCraftingProviders methods, CallbackInfo ci) {
        CraftingProviderId providerId = this.dataEnergistics$providerId;
        if (providerId == null) {
            throw new IllegalStateException("AE2 crafting provider state was unmounted before publication");
        }
        CraftingProviderPublicationSink publicationSink = (CraftingProviderPublicationSink) methods;
        PatternProviderRuntimeBindings.unbind(providerId);
        publicationSink.dataEnergistics$unpublishProvider(providerId);
        this.dataEnergistics$providerId = null;
    }
}
