package com.fish_dan_.data_energistics.integration.emi.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.emi.DataEnergisticsEmiRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EMI-registration accumulator that commits one integration plugin's declarations atomically.
 */
final class EmiPluginRegistrationAccumulator {

    private final Map<ResourceLocation, EmiRecipeHandlerRegistration<?>> recipeHandlers = new LinkedHashMap<>();
    private boolean frozen;

    /**
     * Creates an isolated transaction for one discovered plugin.
     */
    PluginStaging createStaging(String owningModId, String pluginClassName) {
        requireOpen();
        return new PluginStaging(this, owningModId, pluginClassName);
    }

    /**
     * Validates and merges a successful plugin transaction.
     */
    void commit(PluginStaging staging) {
        requireOpen();
        staging.requireOwnedBy(this);
        staging.requireOpen();

        for (ResourceLocation registrationId : staging.recipeHandlers.keySet()) {
            if (this.recipeHandlers.containsKey(registrationId)) {
                throw new IllegalStateException(
                        "Duplicate EMI recipe-handler registration ID '" + registrationId + "' from " + staging.description());
            }
        }

        this.recipeHandlers.putAll(staging.recipeHandlers);
        staging.markCommitted();
    }

    /**
     * Freezes declarations in deterministic plugin and declaration order.
     */
    List<EmiRecipeHandlerRegistration<?>> freeze() {
        requireOpen();
        this.frozen = true;
        return List.copyOf(this.recipeHandlers.values());
    }

    /**
     * Rejects use after the EMI registration phase has published its declarations.
     */
    private void requireOpen() {
        if (this.frozen) {
            throw new IllegalStateException("Data Energistics EMI plugin registration is already frozen");
        }
    }

    /**
     * Plugin-local staging surface hidden from every other plugin until commit succeeds.
     */
    static final class PluginStaging implements DataEnergisticsEmiRegistry {

        private final EmiPluginRegistrationAccumulator owner;
        private final String owningModId;
        private final String pluginClassName;
        private final Map<ResourceLocation, EmiRecipeHandlerRegistration<?>> recipeHandlers = new LinkedHashMap<>();
        private State state = State.OPEN;

        /**
         * Captures stable ownership context for diagnostics.
         */
        private PluginStaging(EmiPluginRegistrationAccumulator owner, String owningModId, String pluginClassName) {
            this.owner = owner;
            this.owningModId = owningModId;
            this.pluginClassName = pluginClassName;
        }

        @Override
        public <T extends AbstractContainerMenu> void registerRecipeHandler(
                                                                            ResourceLocation registrationId,
                                                                            MenuType<T> menuType,
                                                                            EmiRecipeHandler<T> handler) {
            requireOpen();
            ResourceLocation stagedId = requireStagedValue(registrationId, "EMI recipe-handler registration ID");
            MenuType<T> stagedMenuType = requireStagedValue(menuType, "EMI recipe-handler menu type");
            EmiRecipeHandler<T> stagedHandler = requireStagedValue(handler, "EMI recipe handler");
            EmiRecipeHandlerRegistration<T> registration = new EmiRecipeHandlerRegistration<>(stagedId, stagedMenuType, stagedHandler);
            if (recipeHandlers.putIfAbsent(stagedId, registration) != null) {
                throw new IllegalStateException(
                        "Duplicate EMI recipe-handler registration ID '" + stagedId + "' in " + description());
            }
        }

        /**
         * Clears a failed plugin transaction before another plugin can observe it.
         */
        void discard() {
            if (this.state != State.OPEN) {
                return;
            }
            this.state = State.DISCARDED;
            this.recipeHandlers.clear();
        }

        /**
         * Ensures only the creating accumulator can commit this transaction.
         */
        private void requireOwnedBy(EmiPluginRegistrationAccumulator expectedOwner) {
            if (this.owner != expectedOwner) {
                throw new IllegalArgumentException("EMI plugin staging transaction belongs to a different registry");
            }
        }

        /**
         * Rejects retained registry handles after the callback has completed.
         */
        private void requireOpen() {
            if (this.state != State.OPEN) {
                throw new IllegalStateException(
                        "EMI plugin registration transaction for " + description() + " is already " + this.state.name().toLowerCase());
            }
        }

        /**
         * Records successful atomic commit.
         */
        private void markCommitted() {
            this.state = State.COMMITTED;
        }

        /**
         * Provides deterministic plugin context for registration failures.
         */
        private String description() {
            return "plugin " + this.pluginClassName + " owned by mod " + this.owningModId;
        }

        /**
         * Validates one external plugin value before it reaches the immutable runtime declarations.
         */
        private <T> T requireStagedValue(@Nullable T value, String role) {
            if (value == null) {
                throw new IllegalArgumentException(role + " must not be null in " + description());
            }
            return value;
        }

        /**
         * Lifecycle of one plugin callback.
         */
        private enum State {
            OPEN,
            COMMITTED,
            DISCARDED
        }
    }
}
