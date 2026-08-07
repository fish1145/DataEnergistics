package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;
import com.fish_dan_.data_energistics.util.UniversalTerminalAdapter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common-setup registry accumulator with one isolated transaction per plugin.
 *
 * <p>A plugin writes only to its own staging object. Commit validates every cross-plugin uniqueness constraint before
 * mutating the accumulator, so a rejected plugin cannot leak a partial terminal, provider, or virtual-output
 * registration.</p>
 */
final class DataEnergisticsRegistryImpl {

    private final Map<String, UniversalTerminalAdapter> universalTerminals = new LinkedHashMap<>();
    private final Map<ResourceLocation, PatternProviderRegistration> patternProviders = new LinkedHashMap<>();
    private final List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters = new ArrayList<>();
    private volatile boolean frozen;
    private volatile DataEnergisticsRegistrySnapshot snapshot;

    /** Creates an isolated registration transaction for exactly one discovered plugin. */
    PluginStaging createStaging(String owningModId, String pluginClassName) {
        this.requireOpen();
        return new PluginStaging(this, owningModId, pluginClassName);
    }

    /** Atomically validates and merges one completed plugin transaction. */
    void commit(PluginStaging staging) {
        this.requireOpen();
        staging.requireOwnedBy(this);
        staging.requireOpen();

        for (String terminalName : staging.universalTerminals.keySet()) {
            if (this.universalTerminals.containsKey(terminalName)) {
                throw new IllegalStateException("Duplicate universal terminal name '" + terminalName + "' from "
                        + staging.description());
            }
        }
        for (ResourceLocation registrationId : staging.patternProviders.keySet()) {
            if (this.patternProviders.containsKey(registrationId)) {
                throw new IllegalStateException("Duplicate pattern provider registration ID '" + registrationId
                        + "' from " + staging.description());
            }
        }
        for (VirtualCraftingOutputAdapter adapter : staging.virtualCraftingOutputAdapters) {
            if (this.virtualCraftingOutputAdapters.stream().anyMatch(existing -> existing == adapter)) {
                throw new IllegalStateException("Duplicate virtual crafting output adapter from "
                        + staging.description());
            }
        }

        this.universalTerminals.putAll(staging.universalTerminals);
        this.patternProviders.putAll(staging.patternProviders);
        this.virtualCraftingOutputAdapters.addAll(staging.virtualCraftingOutputAdapters);
        staging.markCommitted();
    }

    /** Publishes a single immutable runtime snapshot and permanently closes registration. */
    DataEnergisticsRegistrySnapshot freeze() {
        this.requireOpen();
        this.snapshot = new DataEnergisticsRegistrySnapshot(
                List.copyOf(this.universalTerminals.values()),
                List.copyOf(this.patternProviders.values()),
                this.virtualCraftingOutputAdapters);
        this.frozen = true;
        return this.snapshot;
    }

    /** Resolves the frozen view used by query methods retained by plugin code. */
    private DataEnergisticsRegistrySnapshot frozenSnapshot() {
        if (!this.frozen) {
            throw new IllegalStateException("Data Energistics registry queries are unavailable before freeze");
        }
        return this.snapshot;
    }

    /** Rejects registrations and additional freeze attempts after publication. */
    private void requireOpen() {
        if (this.frozen) {
            throw new IllegalStateException("Data Energistics plugin registration is already frozen");
        }
    }

    /**
     * Plugin-scoped typed registry whose collections are invisible to the global accumulator until atomic commit.
     */
    static final class PluginStaging implements DataEnergisticsRegistry {

        private final DataEnergisticsRegistryImpl owner;
        private final String owningModId;
        private final String pluginClassName;
        private final Map<String, UniversalTerminalAdapter> universalTerminals = new LinkedHashMap<>();
        private final Map<ResourceLocation, PatternProviderRegistration> patternProviders = new LinkedHashMap<>();
        private final List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters = new ArrayList<>();
        private final UniversalTerminalRegistry universalTerminalRegistry = new StagedUniversalTerminalRegistry();
        private final PatternProviderRegistry patternProviderRegistry = new StagedPatternProviderRegistry();
        private final VirtualCraftingRegistry virtualCraftingRegistry = new StagedVirtualCraftingRegistry();
        private State state = State.OPEN;

        /** Captures plugin ownership so every registration failure has actionable context. */
        private PluginStaging(DataEnergisticsRegistryImpl owner, String owningModId, String pluginClassName) {
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
        public VirtualCraftingRegistry virtualCrafting() {
            return this.virtualCraftingRegistry;
        }

        /** Closes and clears a failed transaction without touching already committed plugins. */
        void discard() {
            if (this.state != State.OPEN) {
                return;
            }
            this.state = State.DISCARDED;
            this.universalTerminals.clear();
            this.patternProviders.clear();
            this.virtualCraftingOutputAdapters.clear();
        }

        /** Verifies that a loader did not accidentally commit this staging transaction through another accumulator. */
        private void requireOwnedBy(DataEnergisticsRegistryImpl expectedOwner) {
            if (this.owner != expectedOwner) {
                throw new IllegalArgumentException("Plugin staging transaction belongs to a different registry");
            }
        }

        /** Rejects retained staging handles after the plugin callback has returned or failed. */
        private void requireOpen() {
            if (this.state != State.OPEN) {
                throw new IllegalStateException("Plugin registration transaction for " + this.description()
                        + " is already " + this.state.name().toLowerCase());
            }
        }

        /** Marks a successful transaction closed after every collection has been merged. */
        private void markCommitted() {
            this.state = State.COMMITTED;
        }

        /** Formats stable ownership context for duplicate and lifecycle failures. */
        private String description() {
            return "plugin " + this.pluginClassName + " owned by mod " + this.owningModId;
        }

        /** Stages terminal adapters and serves runtime queries only after the root registry freezes. */
        private final class StagedUniversalTerminalRegistry implements UniversalTerminalRegistry {

            @Override
            public void register(UniversalTerminalAdapter adapter) {
                requireOpen();
                String terminalName = adapter.name();
                if (terminalName.isBlank()) {
                    throw new IllegalArgumentException("Universal terminal name must not be blank for " + description());
                }
                if (universalTerminals.putIfAbsent(terminalName, adapter) != null) {
                    throw new IllegalStateException("Duplicate universal terminal name '" + terminalName + "' in "
                            + description());
                }
            }

            @Override
            public boolean isSupportedTerminal(ItemStack stack) {
                return owner.frozenSnapshot().universalTerminals().isSupportedTerminal(stack);
            }
        }

        /** Stages complete provider registrations keyed by their stable public registration ID. */
        private final class StagedPatternProviderRegistry implements PatternProviderRegistry {

            @Override
            public void register(PatternProviderRegistration registration) {
                requireOpen();
                var metadata = registration.metadata();
                ResourceLocation registrationId = metadata.registrationId();
                if (registrationId == null || metadata.providerIdentity() == null || registration.factory() == null) {
                    throw new IllegalArgumentException("Pattern provider registration requires an ID, identity and factory");
                }
                if (patternProviders.putIfAbsent(registrationId, registration) != null) {
                    throw new IllegalStateException("Duplicate pattern provider registration ID '" + registrationId
                            + "' in " + description());
                }
            }
        }

        /** Stages stateless virtual-output adapters in declaration order. */
        private final class StagedVirtualCraftingRegistry implements VirtualCraftingRegistry {

            @Override
            public void registerOutputAdapter(VirtualCraftingOutputAdapter adapter) {
                requireOpen();
                if (adapter == null) {
                    throw new IllegalArgumentException("Virtual crafting output adapter must not be null");
                }
                if (virtualCraftingOutputAdapters.stream().anyMatch(existing -> existing == adapter)) {
                    throw new IllegalStateException("Duplicate virtual crafting output adapter in " + description());
                }
                virtualCraftingOutputAdapters.add(adapter);
            }
        }

        /** Lifecycle of one isolated plugin registration callback. */
        private enum State {
            OPEN,
            COMMITTED,
            DISCARDED
        }
    }
}
