package com.fish_dan_.data_energistics.api.entrypoint.jei;

/**
 * JEI-phase extension point for integrations supplied by another mod.
 *
 * <p>
 * Implementations must be public, expose a public no-argument constructor, and be annotated with
 * {@link DataEnergisticsJeiEntrypoint}. Their registrations are isolated until the complete callback succeeds.
 * </p>
 */
@FunctionalInterface
public interface DataEnergisticsJeiPlugin {

    /**
     * Declares JEI integrations owned by this plugin.
     *
     * <p>
     * JEI may repeat this phase after recipes reload or the client reconnects. A fresh plugin instance and
     * registry are supplied for each cycle; the registry is valid only for this callback.
     * </p>
     *
     * @param registry plugin-scoped JEI registration surface
     */
    void register(DataEnergisticsJeiRegistry registry);
}
