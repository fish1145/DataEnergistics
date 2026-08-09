package com.fish_dan_.data_energistics.mixin.core.patternprovider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationSink;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.IdentityCraftingProviderPublicationIndex;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.helpers.NetworkCraftingProviders;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * Exposes an identity-preserving Trinity publication index alongside AE2's crafting-provider registry.
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(NetworkCraftingProviders.class)
public abstract class NetworkCraftingProvidersPublicationMixin
                                                               implements CraftingProviderPublicationIndex, CraftingProviderPublicationSink {

    /**
     * Index whose live references remain confined to the owning server thread.
     */
    @Unique
    private final IdentityCraftingProviderPublicationIndex dataEnergistics$publicationIndex = new IdentityCraftingProviderPublicationIndex();

    @Override
    public long publicationScope() {
        return this.dataEnergistics$publicationIndex.publicationScope();
    }

    @Override
    public long publicationRevision() {
        return this.dataEnergistics$publicationIndex.publicationRevision();
    }

    @Override
    public List<CraftingProviderId> providerIdsFor(IPatternDetails patternIdentity) {
        return this.dataEnergistics$publicationIndex.providerIdsFor(patternIdentity);
    }

    @Override
    @Nullable
    public ICraftingProvider resolveLiveProvider(CraftingProviderId providerId) {
        return this.dataEnergistics$publicationIndex.resolveLiveProvider(providerId);
    }

    @Override
    public CraftingProviderId dataEnergistics$publishProvider(
                                                              ICraftingProvider provider,
                                                              List<IPatternDetails> patterns) {
        return this.dataEnergistics$publicationIndex.publish(provider, patterns);
    }

    @Override
    public void dataEnergistics$unpublishProvider(CraftingProviderId providerId) {
        this.dataEnergistics$publicationIndex.unpublish(providerId);
    }
}
