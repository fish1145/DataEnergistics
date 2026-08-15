package com.fish_dan_.data_energistics.api.registry.search;

/**
 * Registration-stage surface for machine-specific Trinity pattern search candidates.
 */
public interface TrinityPatternSearchRegistry {

    /**
     * Registers one contributor under its stable public ID.
     *
     * @param registration immutable contribution declaration
     */
    void register(TrinityPatternSearchTermRegistration registration);
}
