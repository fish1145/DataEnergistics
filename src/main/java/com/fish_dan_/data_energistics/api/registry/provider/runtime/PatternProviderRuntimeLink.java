package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import appeng.helpers.patternprovider.PatternContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Links an AE2 crafting-provider publication to the terminal-visible container that owns the same provider lifecycle.
 */
public interface PatternProviderRuntimeLink {

    /**
     * Returns the terminal-visible container represented by this publication.
     *
     * @return provider host used for stable identity resolution and terminal actions
     */
    @NotNull
    PatternContainer patternContainer();
}
