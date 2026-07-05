package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import com.modularmc.mdl.api.multiblock.BlockPattern;

import java.util.Map;
import java.util.Optional;

/**
 * One resolved JSON multiblock definition available to Data Energistics runtime code.
 *
 * <p>
 * The key exists so callers can address GTM-style named structures without depending on resource file paths. The
 * pattern
 * is the MDLib {@link BlockPattern} built from JSON or code-backed built-in data.
 */
public interface JsonMultiBlockDefinition {

    /**
     * Returns the machine id and named structure this definition belongs to.
     */
    JsonMultiBlockStructureKey key();

    /**
     * Returns the resolved MDLib pattern used by structure-matching callers.
     */
    BlockPattern pattern();

    /**
     * Returns the translation key for the player-facing structure display name, when JSON metadata defines one.
     */
    Optional<String> displayNameTranslationKey();

    /**
     * Returns pattern symbols that are declared as compartment positions.
     */
    Map<String, CompartmentType> compartmentTypes();
}
