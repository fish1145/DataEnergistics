package com.fish_dan_.data_energistics.common.multiblock.json.registry;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable generation of all active JSON multiblock definitions.
 *
 * @param revision    registry-local monotonic generation
 * @param definitions active definitions in stable presentation order
 */
public record JsonMultiBlockDefinitionRegistrySnapshot(
                                                       long revision,
                                                       Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions) {

    /**
     * Copies the complete generation so readers can safely retain it across reloads.
     */
    public JsonMultiBlockDefinitionRegistrySnapshot {
        if (revision < 0L) {
            throw new IllegalArgumentException("JSON multiblock definition revision cannot be negative: " + revision);
        }
        if (definitions == null) {
            throw new IllegalArgumentException("JSON multiblock definition snapshot map cannot be null");
        }
        LinkedHashMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> copy = new LinkedHashMap<>();
        for (Map.Entry<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> entry : definitions.entrySet()) {
            JsonMultiBlockStructureKey key = entry.getKey();
            JsonMultiBlockDefinition definition = entry.getValue();
            if (key == null || definition == null) {
                throw new IllegalArgumentException("JSON multiblock definition snapshot cannot contain null entries");
            }
            if (!key.equals(definition.key())) {
                throw new IllegalArgumentException("JSON multiblock definition snapshot key mismatch: " + key +
                        " != " + definition.key());
            }
            copy.put(key, definition);
        }
        definitions = Collections.unmodifiableMap(copy);
    }
}
