package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import com.modularmc.mdl.api.multiblock.BlockPattern;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Eager immutable definition already resolved from JSON or built-in factories.
 */
public record ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                               BlockPattern pattern,
                                               Optional<String> displayNameTranslationKey,
                                               Map<String, CompartmentType> compartmentTypes,
                                               Map<String, Set<CompartmentType>> replaceableCompartmentTypes)
        implements JsonMultiBlockDefinition {

    public ResolvedJsonMultiBlockDefinition {
        key = Objects.requireNonNull(key, "key");
        pattern = Objects.requireNonNull(pattern, "pattern");
        displayNameTranslationKey = Objects.requireNonNull(displayNameTranslationKey, "displayNameTranslationKey");
        compartmentTypes = Map.copyOf(Objects.requireNonNull(compartmentTypes, "compartmentTypes"));
        replaceableCompartmentTypes = copyReplaceableCompartmentTypes(replaceableCompartmentTypes);
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                            BlockPattern pattern,
                                            Optional<String> displayNameTranslationKey) {
        this(key, pattern, displayNameTranslationKey, Map.of(), Map.of());
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key, BlockPattern pattern) {
        this(key, pattern, Optional.empty(), Map.of(), Map.of());
    }

    private static Map<String, Set<CompartmentType>> copyReplaceableCompartmentTypes(
                                                                                     Map<String, Set<CompartmentType>> source) {
        Objects.requireNonNull(source, "replaceableCompartmentTypes");
        return source.entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
