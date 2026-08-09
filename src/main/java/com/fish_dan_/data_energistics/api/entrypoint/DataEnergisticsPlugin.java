package com.fish_dan_.data_energistics.api.entrypoint;

/**
 * Common-setup extension point for integrations supplied by a mod.
 *
 * <p>
 * Implementations must be public, have a public no-argument constructor and be annotated with
 * {@link DataEnergisticsEntrypoint}. Registration is staged per plugin and is frozen before runtime dispatch
 * begins.
 * </p>
 */
@FunctionalInterface
public interface DataEnergisticsPlugin {

    /**
     * Declares all extensions owned by this plugin.
     *
     * @param registry plugin-scoped registration surface
     */
    void register(DataEnergisticsRegistry registry);
}
