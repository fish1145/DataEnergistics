package com.fish_dan_.data_energistics.client.jei.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiRegistry;
import com.fish_dan_.data_energistics.api.entrypoint.jei.JeiRecipeTransferHandlerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import mezz.jei.api.recipe.RecipeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JEI-registration accumulator that commits one integration plugin's declarations atomically.
 */
final class JeiPluginRegistrationAccumulator {

    private final Map<ResourceLocation, JeiRecipeTransferRegistration<?, ?>> recipeTransferHandlers = new LinkedHashMap<>();
    private final Map<RecipeTransferTarget, ResourceLocation> recipeTransferTargets = new LinkedHashMap<>();
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

        for (ResourceLocation registrationId : staging.recipeTransferHandlers.keySet()) {
            if (this.recipeTransferHandlers.containsKey(registrationId)) {
                throw new IllegalStateException(
                        "Duplicate JEI recipe-transfer registration ID '" + registrationId + "' from " + staging.description());
            }
        }
        for (RecipeTransferTarget target : staging.recipeTransferTargets.keySet()) {
            ResourceLocation existingId = this.recipeTransferTargets.get(target);
            if (existingId != null) {
                throw new IllegalStateException(
                        "Duplicate JEI recipe-transfer target '" + target + "' from " + staging.description() + "; already registered as '" + existingId + "'");
            }
        }

        this.recipeTransferHandlers.putAll(staging.recipeTransferHandlers);
        this.recipeTransferTargets.putAll(staging.recipeTransferTargets);
        staging.markCommitted();
    }

    /**
     * Freezes declarations in deterministic plugin and declaration order.
     */
    List<JeiRecipeTransferRegistration<?, ?>> freeze() {
        requireOpen();
        this.frozen = true;
        return List.copyOf(this.recipeTransferHandlers.values());
    }

    /**
     * Rejects use after the JEI registration phase has published its declarations.
     */
    private void requireOpen() {
        if (this.frozen) {
            throw new IllegalStateException("Data Energistics JEI plugin registration is already frozen");
        }
    }

    /**
     * Plugin-local staging surface hidden from every other plugin until commit succeeds.
     */
    static final class PluginStaging implements DataEnergisticsJeiRegistry {

        private final JeiPluginRegistrationAccumulator owner;
        private final String owningModId;
        private final String pluginClassName;
        private final Map<ResourceLocation, JeiRecipeTransferRegistration<?, ?>> recipeTransferHandlers = new LinkedHashMap<>();
        private final Map<RecipeTransferTarget, ResourceLocation> recipeTransferTargets = new LinkedHashMap<>();
        private State state = State.OPEN;

        /**
         * Captures stable ownership context for diagnostics.
         */
        private PluginStaging(JeiPluginRegistrationAccumulator owner, String owningModId, String pluginClassName) {
            this.owner = owner;
            this.owningModId = owningModId;
            this.pluginClassName = pluginClassName;
        }

        @Override
        public <T extends AbstractContainerMenu, R> void registerRecipeTransferHandler(
                                                                                       @NotNull ResourceLocation registrationId,
                                                                                       @NotNull Class<T> menuClass,
                                                                                       @NotNull MenuType<T> menuType,
                                                                                       @NotNull RecipeType<R> recipeType,
                                                                                       @NotNull JeiRecipeTransferHandlerFactory<T, R> factory) {
            requireOpen();
            ResourceLocation stagedId = requireStagedValue(registrationId, "JEI recipe-transfer registration ID");
            Class<T> stagedMenuClass = requireStagedValue(menuClass, "JEI recipe-transfer menu class");
            MenuType<T> stagedMenuType = requireStagedValue(menuType, "JEI recipe-transfer menu type");
            RecipeType<R> stagedRecipeType = requireStagedValue(recipeType, "JEI recipe-transfer recipe type");
            JeiRecipeTransferHandlerFactory<T, R> stagedFactory = requireStagedValue(factory, "JEI recipe-transfer handler factory");
            RecipeTransferTarget target = new RecipeTransferTarget(stagedMenuType, stagedRecipeType);
            if (recipeTransferTargets.containsKey(target)) {
                throw new IllegalStateException(
                        "Duplicate JEI recipe-transfer target '" + target + "' in " + description());
            }
            JeiRecipeTransferRegistration<T, R> registration = new JeiRecipeTransferRegistration<>(
                    stagedId, stagedMenuClass, stagedMenuType, stagedRecipeType, stagedFactory);
            if (recipeTransferHandlers.putIfAbsent(stagedId, registration) != null) {
                throw new IllegalStateException(
                        "Duplicate JEI recipe-transfer registration ID '" + stagedId + "' in " + description());
            }
            recipeTransferTargets.put(target, stagedId);
        }

        /**
         * Clears a failed plugin transaction before another plugin can observe it.
         */
        void discard() {
            if (this.state != State.OPEN) {
                return;
            }
            this.state = State.DISCARDED;
            this.recipeTransferHandlers.clear();
            this.recipeTransferTargets.clear();
        }

        /**
         * Ensures only the creating accumulator can commit this transaction.
         */
        private void requireOwnedBy(JeiPluginRegistrationAccumulator expectedOwner) {
            if (this.owner != expectedOwner) {
                throw new IllegalArgumentException("JEI plugin staging transaction belongs to a different registry");
            }
        }

        /**
         * Rejects retained registry handles after the callback has completed.
         */
        private void requireOpen() {
            if (this.state != State.OPEN) {
                throw new IllegalStateException(
                        "JEI plugin registration transaction for " + description() + " is already " + this.state.name().toLowerCase());
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

    /**
     * Exact JEI dispatch key: JEI distinguishes transfer handlers by both the opened menu type and recipe type.
     */
    private record RecipeTransferTarget(MenuType<?> menuType, RecipeType<?> recipeType) {}
}
