package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Layered in-memory registry for built-in and datapack JSON multiblock definitions.
 */
public final class LayeredJsonMultiBlockDefinitionRegistry implements JsonMultiBlockDefinitionRegistry {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> builtins = Map.of();
    private Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> jsonDefinitions = Map.of();
    private volatile JsonMultiBlockDefinitionRegistrySnapshot activeSnapshot = new JsonMultiBlockDefinitionRegistrySnapshot(0L, Map.of());

    @Override
    public synchronized void registerBuiltin(JsonMultiBlockDefinition definition) {
        if (definition == null || definition.key() == null) {
            throw new IllegalArgumentException("Built-in JSON multiblock definition and key cannot be null");
        }
        if (this.builtins.containsKey(definition.key())) {
            String message = "Duplicate built-in JSON multiblock definition: " + definition.key();
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }

        long nextRevision = nextRevision("register built-in " + definition.key());
        LinkedHashMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> nextBuiltins = new LinkedHashMap<>(this.builtins);
        nextBuiltins.put(definition.key(), definition);
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> immutableBuiltins = immutableCopy(nextBuiltins);
        JsonMultiBlockDefinitionRegistrySnapshot nextSnapshot = new JsonMultiBlockDefinitionRegistrySnapshot(
                nextRevision,
                merge(immutableBuiltins, this.jsonDefinitions));

        this.builtins = immutableBuiltins;
        this.activeSnapshot = nextSnapshot;
        LOGGER.info("Registered built-in JSON multiblock definition {}", definition.key());
    }

    @Override
    public synchronized void applyJsonDefinitions(Collection<JsonMultiBlockDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("JSON multiblock reload definitions cannot be null");
        }
        LinkedHashMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> nextJsonDefinitions = new LinkedHashMap<>();
        for (JsonMultiBlockDefinition definition : definitions) {
            if (definition == null || definition.key() == null) {
                throw new IllegalArgumentException("JSON multiblock reload cannot contain a null definition or key");
            }
            if (nextJsonDefinitions.putIfAbsent(definition.key(), definition) != null) {
                String message = "Duplicate JSON multiblock definition in reload apply: " + definition.key();
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }
        }

        long nextRevision = nextRevision("apply JSON definitions");
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> immutableJsonDefinitions = immutableCopy(nextJsonDefinitions);
        JsonMultiBlockDefinitionRegistrySnapshot nextSnapshot = new JsonMultiBlockDefinitionRegistrySnapshot(
                nextRevision,
                merge(this.builtins, immutableJsonDefinitions));

        this.jsonDefinitions = immutableJsonDefinitions;
        this.activeSnapshot = nextSnapshot;
        LOGGER.info(
                "Applied {} JSON multiblock definitions ({} active, {} built-in fallback)",
                this.jsonDefinitions.size(),
                this.activeSnapshot.definitions().size(),
                this.builtins.size());
    }

    @Override
    public JsonMultiBlockDefinitionRegistrySnapshot snapshot() {
        return this.activeSnapshot;
    }

    private long nextRevision(String operation) {
        try {
            return Math.incrementExact(this.activeSnapshot.revision());
        } catch (ArithmeticException exception) {
            LOGGER.error("JSON multiblock definition revision overflow while attempting to {}", operation, exception);
            throw exception;
        }
    }

    private static Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> merge(
                                                                                   Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> builtins,
                                                                                   Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> jsonDefinitions) {
        LinkedHashMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> merged = new LinkedHashMap<>(builtins);
        merged.putAll(jsonDefinitions);
        return immutableCopy(merged);
    }

    private static Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> immutableCopy(
                                                                                           Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }
}
