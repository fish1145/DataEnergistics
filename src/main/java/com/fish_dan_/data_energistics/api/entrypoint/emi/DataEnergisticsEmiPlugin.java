package com.fish_dan_.data_energistics.api.entrypoint.emi;

/**
 * EMI-phase extension point for integrations supplied by another mod.
 *
 * <p>
 * Implementations must be public, expose a public no-argument constructor, and be annotated with
 * {@link DataEnergisticsEmiEntrypoint}. Their registrations are isolated until the complete callback succeeds.
 * </p>
 */
@FunctionalInterface
public interface DataEnergisticsEmiPlugin {

    /**
     * Declares EMI integrations owned by this plugin.
     *
     * @param registry plugin-scoped EMI registration surface
     */
    void register(DataEnergisticsEmiRegistry registry);
}
