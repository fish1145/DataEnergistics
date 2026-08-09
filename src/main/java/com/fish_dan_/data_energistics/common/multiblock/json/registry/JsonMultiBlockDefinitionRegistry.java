package com.fish_dan_.data_energistics.common.multiblock.json.registry;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;

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
     * Returns one atomically published view of the active definitions and their revision.
     *
     * <p>
     * Consumers that cache derived state must retain this snapshot instead of reading definitions and revision in
     * separate calls.
     * </p>
     */
    JsonMultiBlockDefinitionRegistrySnapshot snapshot();

    /**
     * Looks up the active definition for the supplied machine and structure key.
     */
    default Optional<JsonMultiBlockDefinition> get(JsonMultiBlockStructureKey key) {
        return Optional.ofNullable(snapshot().definitions().get(key));
    }

    /**
     * Returns all currently active definitions after built-in and datapack layers are merged.
     */
    default Collection<JsonMultiBlockDefinition> values() {
        return snapshot().definitions().values();
    }

    /**
     * Returns the number of currently active definitions.
     */
    default int size() {
        return snapshot().definitions().size();
    }

    /**
     * Returns the monotonic revision of the active definition set so loaded controllers can invalidate cached matches.
     */
    default long revision() {
        return snapshot().revision();
    }
}
