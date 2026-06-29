package com.fish_dan_.data_energistics.common.multiblock.json;

import com.modularmc.mdl.api.multiblock.BlockPattern;

import java.util.Objects;
import java.util.Optional;

/**
 * Eager immutable implementation for definitions already parsed from JSON.
 */
public record JsonMultiBlockDefinitionImpl(JsonMultiBlockStructureKey key,
                                           BlockPattern pattern,
                                           Optional<String> displayNameTranslationKey)
        implements JsonMultiBlockDefinition {

    public JsonMultiBlockDefinitionImpl {
        key = Objects.requireNonNull(key, "key");
        pattern = Objects.requireNonNull(pattern, "pattern");
        displayNameTranslationKey = Objects.requireNonNull(displayNameTranslationKey, "displayNameTranslationKey");
    }

    public JsonMultiBlockDefinitionImpl(JsonMultiBlockStructureKey key, BlockPattern pattern) {
        this(key, pattern, Optional.empty());
    }
}
