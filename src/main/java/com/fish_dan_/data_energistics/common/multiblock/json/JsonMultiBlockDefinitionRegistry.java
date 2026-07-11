package com.fish_dan_.data_energistics.common.multiblock.json;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for Data Energistics JSON multiblock definitions.
 *
 * <p>
 * Built-in definitions provide code-backed fallback anchors. Datapack definitions are applied on reload and may replace
 * built-in definitions with the same key while registering additional named structures.
 */
public interface JsonMultiBlockDefinitionRegistry {

    /**
     * Registers a code-backed fallback definition. Duplicate built-in keys fail fast.
     */
    void registerBuiltin(JsonMultiBlockDefinition definition);

    /**
     * Replaces the current datapack layer with the supplied definitions and keeps built-ins as fallback entries.
     */
    void applyJsonDefinitions(Collection<JsonMultiBlockDefinition> definitions);

    /**
     * Looks up the active definition for the supplied machine and structure key.
     */
    Optional<JsonMultiBlockDefinition> get(JsonMultiBlockStructureKey key);

    /**
     * Returns all currently active definitions after built-in and datapack layers are merged.
     */
    Collection<JsonMultiBlockDefinition> values();

    /**
     * Returns the number of currently active definitions.
     */
    int size();

    /**
     * Returns the monotonic revision of the active definition set so loaded controllers can invalidate cached matches.
     */
    long revision();
}
