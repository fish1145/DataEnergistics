package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdLookup;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchTermRegistration;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistration;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternRecipeIdResolvers;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Immutable runtime values published after every discovered plugin has finished registration.
 *
 * <p>
 * The snapshot deliberately does not implement the registration-stage registrar. Runtime consumers receive copied
 * values and read-only resolver interfaces, so late mutation cannot be expressed instead of being represented by
 * frozen implementations whose only behavior is to throw.
 * </p>
 */
public final class DataEnergisticsRegistrySnapshot {

    private final List<UniversalTerminalRegistration> universalTerminalRegistrations;
    private final List<PatternProviderRegistration> patternProviderRegistrations;
    private final List<AdaptivePatternProviderRegistration> adaptivePatternProviderRegistrations;
    private final TrinityPatternRecipeIdResolvers trinityPatternRecipes;
    private final List<TrinityPatternSearchTermRegistration> trinityPatternSearchTermRegistrations;
    private final List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters;

    /**
     * Freezes all registration values without retaining a mutable staging collection.
     */
    DataEnergisticsRegistrySnapshot(List<UniversalTerminalRegistration> universalTerminalRegistrations,
                                    List<PatternProviderRegistration> patternProviderRegistrations,
                                    List<AdaptivePatternProviderRegistration> adaptivePatternProviderRegistrations,
                                    Map<ResourceLocation, TrinityPatternRecipeIdResolver> trinityPatternRecipeIdResolvers,
                                    Map<ResourceLocation, TrinityPatternSearchTermRegistration> trinityPatternSearchTerms,
                                    List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters) {
        this.universalTerminalRegistrations = List.copyOf(universalTerminalRegistrations);
        this.patternProviderRegistrations = List.copyOf(patternProviderRegistrations);
        this.adaptivePatternProviderRegistrations = List.copyOf(adaptivePatternProviderRegistrations);
        this.trinityPatternRecipes = new TrinityPatternRecipeIdResolvers(trinityPatternRecipeIdResolvers);
        this.trinityPatternSearchTermRegistrations = List.copyOf(trinityPatternSearchTerms.values());
        this.virtualCraftingOutputAdapters = List.copyOf(virtualCraftingOutputAdapters);
    }

    /**
     * @return terminals in deterministic plugin and declaration order
     */
    public List<UniversalTerminalRegistration> universalTerminalRegistrations() {
        return this.universalTerminalRegistrations;
    }

    /**
     * @return provider lifecycle declarations in deterministic plugin and declaration order
     */
    public List<PatternProviderRegistration> patternProviderRegistrations() {
        return this.patternProviderRegistrations;
    }

    /**
     * @return adaptive provider declarations in deterministic plugin and declaration order
     */
    public List<AdaptivePatternProviderRegistration> adaptivePatternProviderRegistrations() {
        return this.adaptivePatternProviderRegistrations;
    }

    /**
     * @return immutable Trinity recipe resolution runtime
     */
    public TrinityPatternRecipeIdLookup trinityPatternRecipes() {
        return this.trinityPatternRecipes;
    }

    /**
     * @return number of frozen Trinity resolver declarations
     */
    public int trinityPatternRecipeResolverCount() {
        return this.trinityPatternRecipes.size();
    }

    /**
     * @return machine-specific search contributors in deterministic plugin and declaration order
     */
    public List<TrinityPatternSearchTermRegistration> trinityPatternSearchTermRegistrations() {
        return this.trinityPatternSearchTermRegistrations;
    }

    /**
     * @return virtual-output adapters in deterministic plugin and declaration order
     */
    public List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters() {
        return this.virtualCraftingOutputAdapters;
    }
}
