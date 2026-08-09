package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Public location-independent identity supplied by an external pattern-provider integration.
 *
 * <p>
 * The type and schema version identify the provider family. Canonical fields identify one live provider within
 * that family and must remain ordered and deterministic across server restarts.
 * </p>
 *
 * @param type            stable external provider family identifier
 * @param schemaVersion   positive version of the canonical field schema
 * @param canonicalFields ordered deterministic identity fields for one live provider
 */
public record ExternalPatternProviderIdentity(
                                              ResourceLocation type,
                                              int schemaVersion,
                                              List<String> canonicalFields) {

    /**
     * Validates the schema version and freezes the canonical field list.
     */
    public ExternalPatternProviderIdentity {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("External provider identity schema version must be positive");
        }
        canonicalFields = List.copyOf(canonicalFields);
    }
}
