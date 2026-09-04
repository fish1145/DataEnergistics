package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.dynamic.DynamicCraftingOutputRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchRegistry;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchTermRegistration;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistration;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common-setup registry accumulator with one isolated transaction per plugin.
 *
 * <p>
 * A plugin writes only to its own staging object. Commit validates every cross-plugin uniqueness constraint before
 * mutating the accumulator, so a rejected plugin cannot leak a partial terminal, provider, or crafting-output
 * registration.
 * </p>
 */
final class PluginRegistrationAccumulator {

    private final Map<String, UniversalTerminalRegistration> universalTerminals = new LinkedHashMap<>();
    private final Map<ResourceLocation, PatternProviderRegistration> patternProviders = new LinkedHashMap<>();
    private final Map<ProviderIdentityDescriptor, ResourceLocation> patternProviderIdentities = new LinkedHashMap<>();
    private final Map<ResourceLocation, CraftingMachineCapacityRegistration> craftingMachineCapacities = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> craftingMachineCapacityTypes = new LinkedHashMap<>();
    private final Map<ResourceLocation, AdaptivePatternProviderRegistration> adaptivePatternProviders = new LinkedHashMap<>();
    private final Map<ResourceLocation, TrinityPatternRecipeIdResolver> trinityPatternRecipeIdResolvers = new LinkedHashMap<>();
    private final Map<ResourceLocation, TrinityPatternSearchTermRegistration> trinityPatternSearchTerms = new LinkedHashMap<>();
    private final List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters = new ArrayList<>();
    private final Map<ResourceLocation, DynamicCraftingOutputAdapter> dynamicCraftingOutputAdapters = new LinkedHashMap<>();
    private volatile boolean frozen;

    /**
     * Creates an isolated registration transaction for exactly one discovered plugin.
     */
    PluginStaging createStaging(String owningModId, String pluginClassName) {
        this.requireOpen();
        return new PluginStaging(this, owningModId, pluginClassName);
    }

    /**
     * Atomically validates and merges one completed plugin transaction.
     */
    void commit(PluginStaging staging) {
        this.requireOpen();
        staging.requireOwnedBy(this);
        staging.requireOpen();

        for (String terminalName : staging.universalTerminals.keySet()) {
            if (this.universalTerminals.containsKey(terminalName)) {
                throw new IllegalStateException("Duplicate universal terminal name '" + terminalName + "' from " + staging.description());
            }
        }
        for (ResourceLocation registrationId : staging.patternProviders.keySet()) {
            if (this.patternProviders.containsKey(registrationId)) {
                throw new IllegalStateException("Duplicate pattern provider registration ID '" + registrationId + "' from " + staging.description());
            }
        }
        for (PatternProviderRegistration registration : staging.patternProviders.values()) {
            ProviderIdentityDescriptor identity = registration.metadata().providerIdentity();
            if (this.patternProviderIdentities.containsKey(identity)) {
                ResourceLocation existingId = this.patternProviderIdentities.get(identity);
                throw new IllegalStateException("Duplicate pattern provider identity '" + identity + "' from " + staging.description() + "; already registered as '" + existingId + "'");
            }
        }
        for (ResourceLocation registrationId : staging.craftingMachineCapacities.keySet()) {
            if (this.craftingMachineCapacities.containsKey(registrationId)) {
                throw new IllegalStateException("Duplicate crafting machine capacity registration ID '" + registrationId + "' from " + staging.description());
            }
        }
        for (CraftingMachineCapacityRegistration registration : staging.craftingMachineCapacities.values()) {
            if (this.craftingMachineCapacityTypes.containsKey(registration.blockEntityTypeId())) {
                ResourceLocation existingId = this.craftingMachineCapacityTypes.get(registration.blockEntityTypeId());
                throw new IllegalStateException("Duplicate crafting machine capacity block-entity type '" + registration.blockEntityTypeId() + "' from " + staging.description() + "; already registered as '" + existingId + "'");
            }
        }
        for (ResourceLocation registrationId : staging.adaptivePatternProviders.keySet()) {
            if (this.adaptivePatternProviders.containsKey(registrationId)) {
                throw new IllegalStateException("Duplicate adaptive pattern provider registration ID '" + registrationId + "' from " + staging.description());
            }
        }
        for (ResourceLocation resolverId : staging.trinityPatternRecipeIdResolvers.keySet()) {
            if (this.trinityPatternRecipeIdResolvers.containsKey(resolverId)) {
                throw new IllegalStateException("Duplicate Trinity pattern recipe resolver ID '" + resolverId + "' from " + staging.description());
            }
        }
        for (ResourceLocation registrationId : staging.trinityPatternSearchTerms.keySet()) {
            if (this.trinityPatternSearchTerms.containsKey(registrationId)) {
                throw new IllegalStateException("Duplicate Trinity pattern search registration ID '" + registrationId + "' from " + staging.description());
            }
        }
        for (VirtualCraftingOutputAdapter adapter : staging.virtualCraftingOutputAdapters) {
            if (this.virtualCraftingOutputAdapters.stream().anyMatch(existing -> existing == adapter)) {
                throw new IllegalStateException("Duplicate virtual crafting output adapter from " + staging.description());
            }
        }
        for (ResourceLocation adapterId : staging.dynamicCraftingOutputAdapters.keySet()) {
            if (this.dynamicCraftingOutputAdapters.containsKey(adapterId)) {
                throw new IllegalStateException("Duplicate dynamic crafting output adapter ID '" + adapterId + "' from " + staging.description());
            }
        }

        this.universalTerminals.putAll(staging.universalTerminals);
        this.patternProviders.putAll(staging.patternProviders);
        staging.patternProviders.values().forEach(registration -> this.patternProviderIdentities.put(
                registration.metadata().providerIdentity(), registration.metadata().registrationId()));
        this.craftingMachineCapacities.putAll(staging.craftingMachineCapacities);
        staging.craftingMachineCapacities.values().forEach(registration -> this.craftingMachineCapacityTypes.put(
                registration.blockEntityTypeId(), registration.registrationId()));
        this.adaptivePatternProviders.putAll(staging.adaptivePatternProviders);
        this.trinityPatternRecipeIdResolvers.putAll(staging.trinityPatternRecipeIdResolvers);
        this.trinityPatternSearchTerms.putAll(staging.trinityPatternSearchTerms);
        this.virtualCraftingOutputAdapters.addAll(staging.virtualCraftingOutputAdapters);
        this.dynamicCraftingOutputAdapters.putAll(staging.dynamicCraftingOutputAdapters);
        staging.markCommitted();
    }

    /**
     * Publishes a single immutable runtime snapshot and permanently closes registration.
     */
    DataEnergisticsRegistrySnapshot freeze() {
        this.requireOpen();
        DataEnergisticsRegistrySnapshot snapshot = new DataEnergisticsRegistrySnapshot(
                List.copyOf(this.universalTerminals.values()),
                List.copyOf(this.patternProviders.values()),
                List.copyOf(this.craftingMachineCapacities.values()),
                List.copyOf(this.adaptivePatternProviders.values()),
                this.trinityPatternRecipeIdResolvers,
                this.trinityPatternSearchTerms,
                this.virtualCraftingOutputAdapters,
                this.dynamicCraftingOutputAdapters);
        this.frozen = true;
        return snapshot;
    }

    /**
     * Rejects registrations and additional freeze attempts after publication.
     */
    private void requireOpen() {
        if (this.frozen) {
            throw new IllegalStateException("Data Energistics plugin registration is already frozen");
        }
    }

    /**
     * Plugin-scoped typed registry whose collections are invisible to the global accumulator until atomic commit.
     */
    static final class PluginStaging implements DataEnergisticsRegistry {

        private final PluginRegistrationAccumulator owner;
        private final String owningModId;
        private final String pluginClassName;
        private final Map<String, UniversalTerminalRegistration> universalTerminals = new LinkedHashMap<>();
        private final Map<ResourceLocation, PatternProviderRegistration> patternProviders = new LinkedHashMap<>();
        private final Map<ResourceLocation, CraftingMachineCapacityRegistration> craftingMachineCapacities = new LinkedHashMap<>();
        private final Map<ResourceLocation, AdaptivePatternProviderRegistration> adaptivePatternProviders = new LinkedHashMap<>();
        private final Map<ResourceLocation, TrinityPatternRecipeIdResolver> trinityPatternRecipeIdResolvers = new LinkedHashMap<>();
        private final Map<ResourceLocation, TrinityPatternSearchTermRegistration> trinityPatternSearchTerms = new LinkedHashMap<>();
        private final List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters = new ArrayList<>();
        private final Map<ResourceLocation, DynamicCraftingOutputAdapter> dynamicCraftingOutputAdapters = new LinkedHashMap<>();
        private final UniversalTerminalRegistry universalTerminalRegistry = new StagedUniversalTerminalRegistry();
        private final PatternProviderRegistry patternProviderRegistry = new StagedPatternProviderRegistry();
        private final CraftingMachineCapacityRegistry craftingMachineCapacityRegistry = new StagedCraftingMachineCapacityRegistry();
        private final AdaptivePatternProviderRegistry adaptivePatternProviderRegistry = new StagedAdaptivePatternProviderRegistry();
        private final TrinityPatternRecipeIdRegistry trinityPatternRecipeIdRegistry = new StagedTrinityPatternRecipeIdRegistry();
        private final TrinityPatternSearchRegistry trinityPatternSearchRegistry = new StagedTrinityPatternSearchRegistry();
        private final VirtualCraftingRegistry virtualCraftingRegistry = new StagedVirtualCraftingRegistry();
        private final DynamicCraftingOutputRegistry dynamicCraftingOutputRegistry = new StagedDynamicCraftingOutputRegistry();
        private State state = State.OPEN;

        /**
         * Captures plugin ownership so every registration failure has actionable context.
         */
        private PluginStaging(PluginRegistrationAccumulator owner, String owningModId, String pluginClassName) {
            this.owner = owner;
            this.owningModId = owningModId;
            this.pluginClassName = pluginClassName;
        }

        @Override
        public UniversalTerminalRegistry universalTerminals() {
            return this.universalTerminalRegistry;
        }

        @Override
        public PatternProviderRegistry patternProviders() {
            return this.patternProviderRegistry;
        }

        @Override
        public CraftingMachineCapacityRegistry craftingMachineCapacities() {
            return this.craftingMachineCapacityRegistry;
        }

        @Override
        public AdaptivePatternProviderRegistry adaptivePatternProviders() {
            return this.adaptivePatternProviderRegistry;
        }

        @Override
        public TrinityPatternRecipeIdRegistry trinityPatternRecipes() {
            return this.trinityPatternRecipeIdRegistry;
        }

        @Override
        public TrinityPatternSearchRegistry trinityPatternSearch() {
            return this.trinityPatternSearchRegistry;
        }

        @Override
        public VirtualCraftingRegistry virtualCrafting() {
            return this.virtualCraftingRegistry;
        }

        @Override
        public DynamicCraftingOutputRegistry dynamicCraftingOutputs() {
            return this.dynamicCraftingOutputRegistry;
        }

        /**
         * Closes and clears a failed transaction without touching already committed plugins.
         */
        void discard() {
            if (this.state != State.OPEN) {
                return;
            }
            this.state = State.DISCARDED;
            this.universalTerminals.clear();
            this.patternProviders.clear();
            this.craftingMachineCapacities.clear();
            this.adaptivePatternProviders.clear();
            this.trinityPatternRecipeIdResolvers.clear();
            this.trinityPatternSearchTerms.clear();
            this.virtualCraftingOutputAdapters.clear();
            this.dynamicCraftingOutputAdapters.clear();
        }

        /**
         * Verifies that a loader did not accidentally commit this staging transaction through another accumulator.
         */
        private void requireOwnedBy(PluginRegistrationAccumulator expectedOwner) {
            if (this.owner != expectedOwner) {
                throw new IllegalArgumentException("Plugin staging transaction belongs to a different registry");
            }
        }

        /**
         * Rejects retained staging handles after the plugin callback has returned or failed.
         */
        private void requireOpen() {
            if (this.state != State.OPEN) {
                throw new IllegalStateException("Plugin registration transaction for " + this.description() + " is already " + this.state.name().toLowerCase());
            }
        }

        /**
         * Marks a successful transaction closed after every collection has been merged.
         */
        private void markCommitted() {
            this.state = State.COMMITTED;
        }

        /**
         * Formats stable ownership context for duplicate and lifecycle failures.
         */
        private String description() {
            return "plugin " + this.pluginClassName + " owned by mod " + this.owningModId;
        }

        /**
         * Rejects an invalid plugin value before it can poison a committed global snapshot.
         */
        private <T> T requireStagedValue(@Nullable T value, String role) {
            if (value == null) {
                throw new IllegalArgumentException(role + " must not be null in " + this.description());
            }
            return value;
        }

        /**
         * Stages complete terminal registrations without exposing a runtime query surface.
         */
        private final class StagedUniversalTerminalRegistry implements UniversalTerminalRegistry {

            @Override
            public void register(UniversalTerminalRegistration registration) {
                requireOpen();
                registration = requireStagedValue(registration, "Universal terminal registration");
                String terminalName = registration.name();
                if (universalTerminals.putIfAbsent(terminalName, registration) != null) {
                    throw new IllegalStateException("Duplicate universal terminal name '" + terminalName + "' in " + description());
                }
            }
        }

        /**
         * Stages complete provider registrations keyed by their stable public registration ID.
         */
        private final class StagedPatternProviderRegistry implements PatternProviderRegistry {

            @Override
            public void register(PatternProviderRegistration registration) {
                requireOpen();
                registration = requireStagedValue(registration, "Pattern provider registration");
                var metadata = requireStagedValue(registration.metadata(), "Pattern provider metadata");
                ResourceLocation registrationId = requireStagedValue(
                        metadata.registrationId(), "Pattern provider registration ID");
                ProviderIdentityDescriptor providerIdentity = requireStagedValue(
                        metadata.providerIdentity(), "Pattern provider identity");
                for (PatternProviderRegistration existing : patternProviders.values()) {
                    if (existing.metadata().providerIdentity().equals(providerIdentity)) {
                        throw new IllegalStateException(
                                "Duplicate pattern provider identity '" + providerIdentity + "' in " + description());
                    }
                }
                if (patternProviders.putIfAbsent(registrationId, registration) != null) {
                    throw new IllegalStateException("Duplicate pattern provider registration ID '" + registrationId + "' in " + description());
                }
            }
        }

        /** Stages machine-capacity declarations by registration ID and exact block-entity type. */
        private final class StagedCraftingMachineCapacityRegistry implements CraftingMachineCapacityRegistry {

            @Override
            public void register(CraftingMachineCapacityRegistration registration) {
                requireOpen();
                CraftingMachineCapacityRegistration staged = requireStagedValue(
                        registration, "Crafting machine capacity registration");
                ResourceLocation registrationId = requireStagedValue(
                        staged.registrationId(), "Crafting machine capacity registration ID");
                ResourceLocation blockEntityTypeId = requireStagedValue(
                        staged.blockEntityTypeId(), "Crafting machine block-entity type ID");
                for (CraftingMachineCapacityRegistration existing : craftingMachineCapacities.values()) {
                    if (existing.blockEntityTypeId().equals(blockEntityTypeId)) {
                        throw new IllegalStateException(
                                "Duplicate crafting machine capacity block-entity type '" + blockEntityTypeId +
                                        "' in " + description());
                    }
                }
                if (craftingMachineCapacities.putIfAbsent(registrationId, staged) != null) {
                    throw new IllegalStateException(
                            "Duplicate crafting machine capacity registration ID '" + registrationId +
                                    "' in " + description());
                }
            }
        }

        /**
         * Stages adaptive provider definitions by their stable public registration ID.
         */
        private final class StagedAdaptivePatternProviderRegistry implements AdaptivePatternProviderRegistry {

            @Override
            public void register(AdaptivePatternProviderRegistration registration) {
                requireOpen();
                registration = requireStagedValue(registration, "Adaptive pattern provider registration");
                ResourceLocation registrationId = requireStagedValue(
                        registration.registrationId(), "Adaptive pattern provider registration ID");
                requireStagedValue(registration.definition(), "Adaptive pattern provider definition");
                if (adaptivePatternProviders.putIfAbsent(registrationId, registration) != null) {
                    throw new IllegalStateException("Duplicate adaptive pattern provider registration ID '" + registrationId + "' in " + description());
                }
            }
        }

        /**
         * Stages Trinity recipe resolvers by the ID captured before the runtime snapshot is built.
         */
        private final class StagedTrinityPatternRecipeIdRegistry implements TrinityPatternRecipeIdRegistry {

            @Override
            public void register(TrinityPatternRecipeIdResolver resolver) {
                requireOpen();
                resolver = requireStagedValue(resolver, "Trinity pattern recipe resolver");
                ResourceLocation resolverId = requireStagedValue(
                        resolver.id(), "Trinity pattern recipe resolver ID");
                if (trinityPatternRecipeIdResolvers.putIfAbsent(resolverId, resolver) != null) {
                    throw new IllegalStateException("Duplicate Trinity pattern recipe resolver ID '" + resolverId + "' in " + description());
                }
            }
        }

        /**
         * Stages client-visible Trinity pattern-search terms by stable public registration ID.
         */
        private final class StagedTrinityPatternSearchRegistry implements TrinityPatternSearchRegistry {

            @Override
            public void register(TrinityPatternSearchTermRegistration registration) {
                requireOpen();
                registration = requireStagedValue(registration, "Trinity pattern search registration");
                ResourceLocation registrationId = requireStagedValue(
                        registration.registrationId(), "Trinity pattern search registration ID");
                requireStagedValue(registration.contributor(), "Trinity pattern search contributor");
                if (trinityPatternSearchTerms.putIfAbsent(registrationId, registration) != null) {
                    throw new IllegalStateException("Duplicate Trinity pattern search registration ID '" + registrationId + "' in " + description());
                }
            }
        }

        /**
         * Stages stateless virtual-output adapters in declaration order.
         */
        private final class StagedVirtualCraftingRegistry implements VirtualCraftingRegistry {

            @Override
            public void registerOutputAdapter(VirtualCraftingOutputAdapter adapter) {
                requireOpen();
                VirtualCraftingOutputAdapter stagedAdapter = requireStagedValue(adapter, "Virtual crafting output adapter");
                if (virtualCraftingOutputAdapters.stream().anyMatch(existing -> existing == stagedAdapter)) {
                    throw new IllegalStateException("Duplicate virtual crafting output adapter in " + description());
                }
                virtualCraftingOutputAdapters.add(stagedAdapter);
            }
        }

        /**
         * Stages stateless dynamic-output adapters by stable public ID and declaration order.
         */
        private final class StagedDynamicCraftingOutputRegistry implements DynamicCraftingOutputRegistry {

            @Override
            public void register(DynamicCraftingOutputAdapter adapter) {
                requireOpen();
                adapter = requireStagedValue(adapter, "Dynamic crafting output adapter");
                ResourceLocation adapterId = requireStagedValue(
                        adapter.id(), "Dynamic crafting output adapter ID");
                if (dynamicCraftingOutputAdapters.putIfAbsent(adapterId, adapter) != null) {
                    throw new IllegalStateException(
                            "Duplicate dynamic crafting output adapter ID '" + adapterId + "' in " + description());
                }
            }
        }

        /**
         * Lifecycle of one isolated plugin registration callback.
         */
        private enum State {
            OPEN,
            COMMITTED,
            DISCARDED
        }
    }
}
