package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Layered in-memory registry for built-in and datapack JSON multiblock definitions.
 */
public final class LayeredJsonMultiBlockDefinitionRegistry implements JsonMultiBlockDefinitionRegistry {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> builtins = new LinkedHashMap<>();
    private final Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> active = new LinkedHashMap<>();
    private boolean jsonApplied;
    private volatile long revision;

    @Override
    public synchronized void registerBuiltin(JsonMultiBlockDefinition definition) {
        JsonMultiBlockDefinition existing = this.builtins.putIfAbsent(definition.key(), definition);
        if (existing != null) {
            String message = "Duplicate built-in JSON multiblock definition: " + definition.key();
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        if (!this.jsonApplied) {
            this.active.put(definition.key(), definition);
        } else {
            this.active.putIfAbsent(definition.key(), definition);
        }
        this.revision = Math.incrementExact(this.revision);
        LOGGER.info("Registered built-in JSON multiblock definition {}", definition.key());
    }

    @Override
    public synchronized void applyJsonDefinitions(Collection<JsonMultiBlockDefinition> definitions) {
        Set<JsonMultiBlockStructureKey> jsonKeys = new LinkedHashSet<>();
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> next = new LinkedHashMap<>(this.builtins);
        for (JsonMultiBlockDefinition definition : definitions) {
            if (!jsonKeys.add(definition.key())) {
                String message = "Duplicate JSON multiblock definition in reload apply: " + definition.key();
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }
            next.put(definition.key(), definition);
        }
        this.active.clear();
        this.active.putAll(next);
        this.jsonApplied = true;
        this.revision = Math.incrementExact(this.revision);
        LOGGER.info(
                "Applied {} JSON multiblock definitions ({} active, {} built-in fallback)",
                jsonKeys.size(),
                this.active.size(),
                this.builtins.size());
    }

    @Override
    public synchronized Optional<JsonMultiBlockDefinition> get(JsonMultiBlockStructureKey key) {
        return Optional.ofNullable(this.active.get(key));
    }

    @Override
    public synchronized Collection<JsonMultiBlockDefinition> values() {
        return List.copyOf(this.active.values());
    }

    @Override
    public synchronized int size() {
        return this.active.size();
    }

    @Override
    public long revision() {
        return this.revision;
    }
}
