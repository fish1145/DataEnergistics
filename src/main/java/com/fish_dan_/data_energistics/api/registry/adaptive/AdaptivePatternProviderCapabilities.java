package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable behavior identifiers understood by Data Energistics' adaptive pattern providers.
 *
 * <p>
 * Definitions declare only the behaviors they actually implement. This avoids a closed provider-kind enum and
 * allows one provider to compose multiple independent behaviors.
 * </p>
 */
public final class AdaptivePatternProviderCapabilities {

    /**
     * Enables handling implemented for AE2 Crystal Science meteorite providers.
     */
    public static final ResourceLocation METEORITE = capability("meteorite");
    /**
     * Enables AdvancedAE-specific pattern handling.
     */
    public static final ResourceLocation ADVANCED_PATTERN = capability("advanced_pattern");
    /**
     * Enables the filtered-import option supported by compatible providers.
     */
    public static final ResourceLocation FILTERED_IMPORT = capability("filtered_import");
    /**
     * Enables Applied Create mechanical-crafting dispatch.
     */
    public static final ResourceLocation MECHANICAL_CRAFTING = capability("mechanical_crafting");
    /**
     * Enables resonating-pattern handling.
     */
    public static final ResourceLocation RESONATING = capability("resonating");

    private AdaptivePatternProviderCapabilities() {}

    /**
     * Creates one Data Energistics-owned capability identifier.
     */
    private static ResourceLocation capability(String path) {
        return ResourceLocation.fromNamespaceAndPath("data_energistics", "adaptive_pattern_provider/" + path);
    }
}
