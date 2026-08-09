package com.fish_dan_.data_energistics.common.multiblock.json.definition;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.multiblock.json.autobuild.JsonMultiBlockAutoBuildStaging;

import com.modularmc.mdl.api.multiblock.BlockPattern;

import java.util.Map;
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
                                               Map<String, Set<CompartmentType>> replaceableCompartmentTypes,
                                               JsonMultiBlockAutoBuildStaging autoBuildStaging)
        implements JsonMultiBlockDefinition {

    public ResolvedJsonMultiBlockDefinition {
        compartmentTypes = Map.copyOf(compartmentTypes);
        replaceableCompartmentTypes = copyReplaceableCompartmentTypes(replaceableCompartmentTypes);
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                            BlockPattern pattern,
                                            Optional<String> displayNameTranslationKey,
                                            Map<String, CompartmentType> compartmentTypes,
                                            Map<String, Set<CompartmentType>> replaceableCompartmentTypes) {
        this(
                key,
                pattern,
                displayNameTranslationKey,
                compartmentTypes,
                replaceableCompartmentTypes,
                JsonMultiBlockAutoBuildStaging.none());
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                            BlockPattern pattern,
                                            Optional<String> displayNameTranslationKey) {
        this(key, pattern, displayNameTranslationKey, Map.of(), Map.of(), JsonMultiBlockAutoBuildStaging.none());
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key, BlockPattern pattern) {
        this(key, pattern, Optional.empty(), Map.of(), Map.of(), JsonMultiBlockAutoBuildStaging.none());
    }

    private static Map<String, Set<CompartmentType>> copyReplaceableCompartmentTypes(
                                                                                     Map<String, Set<CompartmentType>> source) {
        return source.entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
