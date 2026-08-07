package com.fish_dan_.data_energistics.api.registry.provider.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Immutable semantic metadata used to match a declared provider integration.
 *
 * <p>Category and workstation IDs are canonicalized as sorted, duplicate-free snapshots. Matching code must compare
 * the complete values; no display names, class names or namespace heuristics are implied by this type.</p>
 *
 * @param registrationId    stable identity of the plugin registration
 * @param providerIdentity  stable declaration-time provider identity schema
 * @param recipeCategoryIds complete recipe-category ID set understood by the provider
 * @param workstationItemIds complete workstation item-ID set understood by the provider
 */
public record PatternProviderMetadata(ResourceLocation registrationId,
                                       ProviderIdentityDescriptor providerIdentity,
                                      List<ResourceLocation> recipeCategoryIds,
                                      List<ResourceLocation> workstationItemIds) {

    /**
     * Validates and freezes provider metadata at the public registration boundary.
     */
    public PatternProviderMetadata {
        recipeCategoryIds = canonicalIds(recipeCategoryIds);
        workstationItemIds = canonicalIds(workstationItemIds);
    }

    /**
     * Alias for integrations that use the shorter category terminology.
     *
     * @return canonical recipe-category IDs
     */
    public List<ResourceLocation> categoryIds() {
        return this.recipeCategoryIds;
    }

    /**
     * Alias for integrations that use the shorter workstation terminology.
     *
     * @return canonical workstation item IDs
     */
    public List<ResourceLocation> workstationIds() {
        return this.workstationItemIds;
    }

    private static List<ResourceLocation> canonicalIds(List<ResourceLocation> ids) {
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        for (ResourceLocation id : ids) {
            unique.add(id);
        }
        ArrayList<ResourceLocation> canonical = new ArrayList<>(unique);
        canonical.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(canonical);
    }
}
