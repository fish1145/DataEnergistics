package com.fish_dan_.data_energistics.api.registry.adaptive;

import org.jetbrains.annotations.NotNull;

/**
 * Registration-stage surface for adaptive pattern-provider definitions.
 *
 * <p>
 * Registrations are frozen before worlds are opened. Runtime matching consumes the immutable snapshot and does
 * not retain this mutation surface.
 * </p>
 */
public interface AdaptivePatternProviderRegistry {

    /**
     * Stages one uniquely identified definition in the current plugin transaction.
     *
     * @param registration complete provider definition
     */
    void register(@NotNull AdaptivePatternProviderRegistration registration);
}
