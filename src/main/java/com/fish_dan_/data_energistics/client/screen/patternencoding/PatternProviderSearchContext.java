package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderViewerWorkstations;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Client-only XEI search metadata resolved lazily for the encoded pattern shown by an open upload panel. */
final class PatternProviderSearchContext {

    private static final PatternProviderSearchContext EMPTY = new PatternProviderSearchContext(
            null,
            ObjectLists.emptyList(),
            new ObjectOpenHashSet<>(),
            new Object2ObjectLinkedOpenHashMap<>(),
            -1L,
            -1L);

    private final @Nullable EncodedPatternRecipeReference recipeReference;
    private final ObjectList<String> recipeTypeNames;
    private final ObjectSet<ResourceLocation> workstationIds;
    private final Object2ObjectMap<ResourceLocation, String> workstationNames;
    private final long recipeNameRevision;
    private final long workstationRevision;

    private PatternProviderSearchContext(
                                         @Nullable EncodedPatternRecipeReference recipeReference,
                                         ObjectList<String> recipeTypeNames,
                                         ObjectSet<ResourceLocation> workstationIds,
                                         Object2ObjectMap<ResourceLocation, String> workstationNames,
                                         long recipeNameRevision,
                                         long workstationRevision) {
        this.recipeReference = recipeReference;
        this.recipeTypeNames = recipeTypeNames;
        this.workstationIds = workstationIds;
        this.workstationNames = workstationNames;
        this.recipeNameRevision = recipeNameRevision;
        this.workstationRevision = workstationRevision;
    }

    static PatternProviderSearchContext resolve(@Nullable EncodedPatternRecipeReference reference) {
        if (reference == null) {
            return EMPTY;
        }
        ResourceLocation recipeTypeId = reference.recipeTypeId();
        ObjectList<String> typeNames = PatternProviderRecipeTypeNames.resolve(recipeTypeId);
        ObjectList<ResourceLocation> resolvedWorkstations = PatternProviderViewerWorkstations.resolve(recipeTypeId);
        ObjectSet<ResourceLocation> workstationIds = new ObjectOpenHashSet<>(resolvedWorkstations);
        Object2ObjectMap<ResourceLocation, String> workstationNames = new Object2ObjectLinkedOpenHashMap<>();
        for (ResourceLocation workstationId : resolvedWorkstations) {
            workstationNames.put(
                    workstationId,
                    BuiltInRegistries.ITEM.get(workstationId).getDescription().getString());
        }
        return new PatternProviderSearchContext(
                reference,
                typeNames,
                workstationIds,
                workstationNames,
                PatternProviderRecipeTypeNames.revision(),
                PatternProviderViewerWorkstations.revision());
    }

    boolean current(@Nullable EncodedPatternRecipeReference reference) {
        if (this.recipeReference == null && reference == null) {
            return true;
        }
        return Objects.equals(this.recipeReference, reference) &&
                this.recipeNameRevision == PatternProviderRecipeTypeNames.revision() &&
                this.workstationRevision == PatternProviderViewerWorkstations.revision();
    }

    void addTerms(
                  PatternEncodingPreviewMenu.SyncedPatternProvider provider,
                  ObjectList<String> terms) {
        EncodedPatternRecipeReference reference = this.recipeReference;
        if (reference == null) {
            return;
        }
        ResourceLocation recipeTypeId = reference.recipeTypeId();
        boolean recipeTypeMatch = provider.supportedRecipeTypeIds().contains(recipeTypeId);
        boolean iconWorkstationMatch = this.workstationIds.contains(provider.iconItemId());
        boolean leafWorkstationMatch = false;
        for (PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf : provider.leaves()) {
            if (this.workstationIds.contains(leaf.iconItemId())) {
                leafWorkstationMatch = true;
                terms.add(leaf.iconItemId().toString());
                terms.add(this.workstationNames.get(leaf.iconItemId()));
            }
        }
        ResourceLocation preferredWorkstationId = provider.preferredWorkstationId();
        boolean preferredWorkstationMatch = preferredWorkstationId != null &&
                this.workstationIds.contains(preferredWorkstationId);
        if (!recipeTypeMatch && !iconWorkstationMatch && !leafWorkstationMatch && !preferredWorkstationMatch) {
            return;
        }
        terms.add(reference.id().toString());
        terms.add(recipeTypeId.toString());
        terms.addAll(this.recipeTypeNames);
        if (preferredWorkstationMatch) {
            terms.add(preferredWorkstationId.toString());
            terms.add(this.workstationNames.get(preferredWorkstationId));
        }
    }
}
