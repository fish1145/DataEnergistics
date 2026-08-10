package com.fish_dan_.data_energistics.api.registry.search;

import org.jetbrains.annotations.NotNull;

/**
 * Registration-stage surface for machine-specific Trinity pattern search candidates.
 */
public interface TrinityPatternSearchRegistry {

    /**
     * Registers one contributor under its stable public ID.
     *
     * @param registration immutable contribution declaration
     */
    void register(@NotNull TrinityPatternSearchTermRegistration registration);
}
