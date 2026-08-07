package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;

import java.util.Optional;

/**
 * Server-thread registry that binds frozen provider declarations to exact AE2 publication lifecycles.
 */
interface PatternProviderRuntimeRegistry {

    /** Binds one newly mounted AE2 provider publication. */
    void bind(CraftingProviderId publicationId, ICraftingProvider provider);

    /** Releases one exact AE2 provider publication. */
    void unbind(CraftingProviderId publicationId);

    /** Resolves menu and upload behavior for one terminal-visible provider. */
    Optional<ResolvedProviderBinding> resolve(PatternContainer container);

    /** Clears every live binding while retaining the frozen declaration snapshot. */
    void clear();
}
