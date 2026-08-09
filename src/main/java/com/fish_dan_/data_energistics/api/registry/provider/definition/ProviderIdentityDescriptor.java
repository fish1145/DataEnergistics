package com.fish_dan_.data_energistics.api.registry.provider.definition;

import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Immutable declaration-time identity of one provider family.
 *
 * <p>
 * Descriptors retain only the semantic fields shared by every live instance in a registered family. Location,
 * routing and other instance-specific fields remain exclusively in {@link PatternProviderIdentity}.
 * </p>
 */
public sealed interface ProviderIdentityDescriptor
                                                   permits ProviderIdentityDescriptor.Block, ProviderIdentityDescriptor.Part,
                                                   ProviderIdentityDescriptor.Trinity, ProviderIdentityDescriptor.External {

    /**
     * Describes block providers implemented by one registered block-entity type.
     *
     * @param blockEntityTypeId registered block-entity type
     */
    record Block(ResourceLocation blockEntityTypeId) implements ProviderIdentityDescriptor {}

    /**
     * Describes multipart providers reconstructed from one registered part item.
     *
     * @param partItemId registered item that reconstructs the part
     */
    record Part(ResourceLocation partItemId) implements ProviderIdentityDescriptor {}

    /**
     * Describes Data Energistics Trinity provider partitions independently of their live routing keys.
     */
    enum Trinity implements ProviderIdentityDescriptor {
        /**
         * The single Trinity provider family.
         */
        INSTANCE
    }

    /**
     * Describes an externally defined provider family without retaining instance-specific canonical fields.
     *
     * @param type          stable provider family identifier
     * @param schemaVersion version of the external canonical identity schema
     */
    record External(ResourceLocation type, int schemaVersion) implements ProviderIdentityDescriptor {

        /**
         * Validates the external family schema.
         */
        public External {
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("External provider identity schema version must be positive");
            }
        }
    }

    /**
     * Projects a live identity onto its declaration-time provider family.
     *
     * <p>
     * A display-derived virtual identity cannot identify a provider family and therefore yields an empty result.
     * </p>
     *
     * @param identity live provider identity
     * @return semantic provider descriptor, or empty for a virtual fallback identity
     */
    static Optional<ProviderIdentityDescriptor> from(
                                                     PatternProviderIdentity identity) {
        return identity.descriptor();
    }
}
