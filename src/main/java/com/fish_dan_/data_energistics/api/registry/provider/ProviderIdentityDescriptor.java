package com.fish_dan_.data_energistics.api.registry.provider;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Immutable declaration-time identity schema for an external provider integration.
 *
 * <p>The descriptor contains only semantic, stable data and therefore can be registered during common setup. A live
 * {@code ProviderIdentity} is resolved separately when a provider instance is bound to a factory.</p>
 *
 * @param type                 stable provider type identifier
 * @param schemaVersion        version of the canonical field schema
 * @param orderedCanonicalFields ordered, deterministic field values
 */
public record ProviderIdentityDescriptor(ResourceLocation type,
                                         int schemaVersion,
                                         List<String> orderedCanonicalFields) {

    /** Validates and freezes an external identity schema. */
    public ProviderIdentityDescriptor {
        if (type == null) {
            throw new IllegalArgumentException("Provider identity type must not be null");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Provider identity schema version must be positive");
        }
        orderedCanonicalFields = List.copyOf(orderedCanonicalFields);
    }
}
