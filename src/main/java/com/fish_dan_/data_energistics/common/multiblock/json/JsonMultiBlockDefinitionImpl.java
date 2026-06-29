package com.fish_dan_.data_energistics.common.multiblock.json;

import com.modularmc.mdl.api.multiblock.BlockPattern;

/**
 * Eager immutable implementation for definitions already parsed from JSON.
 */
public record JsonMultiBlockDefinitionImpl(JsonMultiBlockStructureKey key,
                                           BlockPattern pattern)
        implements JsonMultiBlockDefinition {

}
