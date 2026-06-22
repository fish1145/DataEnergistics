package com.fish_dan_.data_energistics.common.multiblock.vertical;

import com.fish_dan_.data_energistics.Data_Energistics;

import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry for code-defined vertical multiblock structures.
 *
 * <p>
 * This registry is intentionally simple for v1. Definitions are registered during bootstrap by code, and duplicate
 * ids fail immediately so later scans cannot silently use the wrong structure.
 *
 * @param <S> block state representation used by the caller
 */
public final class VerticalMultiBlockRegistry<S> {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private final Map<String, VerticalMultiBlockDefinition<S>> definitions = new LinkedHashMap<>();

    public void register(VerticalMultiBlockDefinition<S> definition) {
        if (this.definitions.containsKey(definition.id())) {
            String message = "Duplicate vertical multiblock definition: " + definition.id();
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        this.definitions.put(definition.id(), definition);
        LOGGER.info("Registered vertical multiblock definition {}", definition.id());
    }

    public Optional<VerticalMultiBlockDefinition<S>> get(String id) {
        return Optional.ofNullable(this.definitions.get(id));
    }

    public Collection<VerticalMultiBlockDefinition<S>> values() {
        return this.definitions.values();
    }

    public int size() {
        return this.definitions.size();
    }
}
