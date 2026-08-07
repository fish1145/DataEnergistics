package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistration;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;

import java.util.List;
import java.util.Optional;

/**
 * Installed access point for the frozen provider registry and its server-lifetime live bindings.
 */
public final class PatternProviderRuntimeBindings {

    private static volatile PatternProviderRuntimeRegistry registry;

    private PatternProviderRuntimeBindings() {}

    /** Installs the immutable declaration snapshot exactly once during common setup. */
    public static synchronized void install(List<PatternProviderRegistration> registrations) {
        if (registry != null) {
            throw new IllegalStateException("Pattern provider runtime bindings are already installed");
        }
        registry = new PatternProviderRuntimeRegistryImpl(registrations);
    }

    /** Binds one publication while isolating plugin factory failures from AE2's mount lifecycle. */
    public static void bind(CraftingProviderId publicationId, ICraftingProvider provider) {
        try {
            requireInstalled().bind(publicationId, provider);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to bind Data Energistics provider plugin for publication {} and provider {}",
                    publicationId,
                    provider,
                    exception);
        }
    }

    /** Releases one exact publication while allowing AE2 to finish its unmount lifecycle. */
    public static void unbind(CraftingProviderId publicationId) {
        try {
            requireInstalled().unbind(publicationId);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to unbind Data Energistics provider plugin publication {}",
                    publicationId,
                    exception);
        }
    }

    /** Resolves the unique plugin declaration selected for one terminal-visible provider. */
    public static Optional<ResolvedProviderBinding> resolve(PatternContainer container) {
        return requireInstalled().resolve(container);
    }

    /** Clears every live provider binding when the server stops. */
    public static void clearLiveBindings() {
        requireInstalled().clear();
    }

    private static PatternProviderRuntimeRegistry requireInstalled() {
        PatternProviderRuntimeRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("Pattern provider runtime bindings are not installed");
        }
        return current;
    }
}
