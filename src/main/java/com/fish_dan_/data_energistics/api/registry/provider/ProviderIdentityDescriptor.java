package com.fish_dan_.data_energistics.api.registry.provider;

import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;

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

    private static final ResourceLocation BLOCK = type("block");
    private static final ResourceLocation PART = type("part");
    private static final ResourceLocation TRINITY = type("trinity");
    private static final ResourceLocation MATRIX = type("matrix");
    private static final ResourceLocation VIRTUAL = type("virtual");

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

    /** Describes every physical block provider of one registered block-entity type. */
    public static ProviderIdentityDescriptor block(ResourceLocation blockEntityTypeId) {
        return descriptor(BLOCK, blockEntityTypeId.toString());
    }

    /** Describes every multipart provider reconstructed from one registered part item. */
    public static ProviderIdentityDescriptor part(ResourceLocation partItemId) {
        return descriptor(PART, partItemId.toString());
    }

    /** Describes Data Energistics Trinity provider partitions independently of their runtime routing keys. */
    public static ProviderIdentityDescriptor trinity() {
        return new ProviderIdentityDescriptor(TRINITY, ProviderIdentity.SCHEMA_VERSION, List.of());
    }

    /** Describes ordinary or ExtendedAE-Plus assembler matrix providers. */
    public static ProviderIdentityDescriptor matrix(boolean plus) {
        return descriptor(MATRIX, Boolean.toString(plus));
    }

    /** Describes an otherwise virtual provider with a registered terminal-group icon. */
    public static ProviderIdentityDescriptor virtual(ResourceLocation terminalGroupIconId,
                                                      String terminalGroupNameEncoding) {
        return new ProviderIdentityDescriptor(
                VIRTUAL,
                ProviderIdentity.SCHEMA_VERSION,
                List.of(terminalGroupIconId.toString(), terminalGroupNameEncoding));
    }

    /** Describes an otherwise virtual provider without a terminal-group icon. */
    public static ProviderIdentityDescriptor virtual(String terminalGroupNameEncoding) {
        return new ProviderIdentityDescriptor(
                VIRTUAL,
                ProviderIdentity.SCHEMA_VERSION,
                List.of("", terminalGroupNameEncoding));
    }

    /** Projects a live location-bearing identity onto its declaration-time semantic descriptor. */
    public static ProviderIdentityDescriptor from(ProviderIdentity identity) {
        return switch (identity) {
            case ProviderIdentity.Block block -> block(block.blockEntityTypeId());
            case ProviderIdentity.Part part -> part(part.partItemId());
            case ProviderIdentity.Trinity ignored -> trinity();
            case ProviderIdentity.Matrix matrix -> matrix(matrix.plus());
            case ProviderIdentity.Virtual virtual -> virtual.terminalGroupIconId()
                    .map(icon -> virtual(icon, virtual.terminalGroupNameEncoding()))
                    .orElseGet(() -> virtual(virtual.terminalGroupNameEncoding()));
        };
    }

    private static ProviderIdentityDescriptor descriptor(ResourceLocation type, String field) {
        return new ProviderIdentityDescriptor(type, ProviderIdentity.SCHEMA_VERSION, List.of(field));
    }

    private static ResourceLocation type(String path) {
        return ResourceLocation.fromNamespaceAndPath("data_energistics", path);
    }
}
