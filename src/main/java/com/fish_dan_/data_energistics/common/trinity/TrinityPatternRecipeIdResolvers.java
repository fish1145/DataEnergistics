package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdLookup;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolution;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;

import net.minecraft.resources.ResourceLocation;

import appeng.api.ids.AEComponents;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable deterministic runtime for the Trinity recipe resolvers frozen during common setup.
 */
public final class TrinityPatternRecipeIdResolvers implements TrinityPatternRecipeIdLookup {

    /**
     * Stable resolver ID for AE2 crafting patterns.
     */
    public static final ResourceLocation AE2_CRAFTING = resolverId("ae2/crafting");
    /**
     * Stable resolver ID for AE2 stonecutting patterns.
     */
    public static final ResourceLocation AE2_STONECUTTING = resolverId("ae2/stonecutting");
    /**
     * Stable resolver ID for AE2 smithing-table patterns.
     */
    public static final ResourceLocation AE2_SMITHING = resolverId("ae2/smithing");

    private final List<RegisteredResolver> resolvers;

    /**
     * Captures the IDs validated by plugin staging and rejects an adapter that changed its identity before freeze.
     *
     * @param registeredResolvers resolver map in deterministic plugin and declaration order
     */
    public TrinityPatternRecipeIdResolvers(
            @NotNull Map<@NotNull ResourceLocation, @NotNull TrinityPatternRecipeIdResolver> registeredResolvers) {
        ArrayList<RegisteredResolver> captured = new ArrayList<>(registeredResolvers.size());
        for (Map.Entry<ResourceLocation, TrinityPatternRecipeIdResolver> entry : registeredResolvers.entrySet()) {
            ResourceLocation resolverId = entry.getKey();
            TrinityPatternRecipeIdResolver resolver = entry.getValue();
            captured.add(new RegisteredResolver(resolverId, resolver));
        }
        this.resolvers = List.copyOf(captured);
    }

    /**
     * Creates an isolated immutable runtime directly from resolvers for logic tests and local composition.
     *
     * @param resolvers resolvers to validate in declaration order
     */
    public TrinityPatternRecipeIdResolvers(
            @NotNull List<@NotNull TrinityPatternRecipeIdResolver> resolvers) {
        this(indexResolvers(resolvers));
    }

    /**
     * Creates an isolated runtime containing the three built-in AE2 resolvers.
     *
     * @return immutable built-in resolver runtime
     */
    public static @NotNull TrinityPatternRecipeIdResolvers createWithBuiltIns() {
        return new TrinityPatternRecipeIdResolvers(builtIns());
    }

    /**
     * @return number of frozen resolver registrations
     */
    public int size() {
        return this.resolvers.size();
    }

    @Override
    public @NotNull Optional<@NotNull TrinityPatternRecipeIdResolution> resolve(
            @NotNull IMolecularAssemblerSupportedPattern pattern) {
        List<RegisteredResolver> matches = new ArrayList<>();
        for (RegisteredResolver registered : this.resolvers) {
            boolean supported;
            try {
                supported = registered.resolver().supports(pattern);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern recipe resolver {} failed while checking a pattern; isolating that resolver",
                        registered.id(),
                        exception);
                continue;
            }
            if (supported) {
                matches.add(registered);
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Ambiguous Trinity pattern recipe resolvers " + matches.stream().map(RegisteredResolver::id).toList());
        }
        RegisteredResolver match = matches.getFirst();
        ResourceLocation recipeId;
        try {
            recipeId = match.resolver().recipeId(pattern);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity pattern recipe resolver {} failed to resolve a recipe ID; rejecting the pattern",
                    match.id(),
                    exception);
            return Optional.empty();
        }
        if (recipeId == null) {
            Data_Energistics.LOGGER.error(
                    "Trinity pattern recipe resolver {} returned a null recipe ID; rejecting the pattern",
                    match.id());
            return Optional.empty();
        }
        return Optional.of(new TrinityPatternRecipeIdResolution(match.id(), recipeId));
    }

    /**
     * Returns the built-ins so the Data Energistics plugin registers them through the same staging lifecycle.
     */
    static @NotNull List<@NotNull TrinityPatternRecipeIdResolver> builtIns() {
        return List.of(BuiltInResolver.CRAFTING, BuiltInResolver.STONECUTTING, BuiltInResolver.SMITHING);
    }

    /**
     * Validates resolver IDs before constructing an isolated runtime.
     */
    private static @NotNull Map<@NotNull ResourceLocation, @NotNull TrinityPatternRecipeIdResolver> indexResolvers(
            @NotNull List<@NotNull TrinityPatternRecipeIdResolver> resolvers) {
        LinkedHashMap<ResourceLocation, TrinityPatternRecipeIdResolver> indexed = new LinkedHashMap<>();
        for (TrinityPatternRecipeIdResolver resolver : resolvers) {
            ResourceLocation resolverId = resolver.id();
            if (indexed.putIfAbsent(resolverId, resolver) != null) {
                throw new IllegalArgumentException("Duplicate Trinity pattern recipe resolver ID: " + resolverId);
            }
        }
        return indexed;
    }

    /**
     * Captured stable ID paired with the stateless resolver callback.
     */
    private record RegisteredResolver(@NotNull ResourceLocation id,
                                      @NotNull TrinityPatternRecipeIdResolver resolver) {
    }

    /**
     * Built-in AE2 component resolvers registered by the built-in Data Energistics plugin.
     */
    private enum BuiltInResolver implements TrinityPatternRecipeIdResolver {

        /**
         * Reads AE2's encoded crafting component.
         */
        CRAFTING(AE2_CRAFTING),
        /**
         * Reads AE2's encoded stonecutting component.
         */
        STONECUTTING(AE2_STONECUTTING),
        /**
         * Reads AE2's encoded smithing-table component.
         */
        SMITHING(AE2_SMITHING);

        private final ResourceLocation id;

        BuiltInResolver(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public @NotNull ResourceLocation id() {
            return this.id;
        }

        @Override
        public boolean supports(@NotNull IMolecularAssemblerSupportedPattern pattern) {
            return switch (this) {
                case CRAFTING -> pattern.getDefinition().toStack().has(AEComponents.ENCODED_CRAFTING_PATTERN);
                case STONECUTTING -> pattern.getDefinition().toStack()
                        .has(AEComponents.ENCODED_STONECUTTING_PATTERN);
                case SMITHING -> pattern.getDefinition().toStack()
                        .has(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
            };
        }

        @Override
        public @NotNull ResourceLocation recipeId(@NotNull IMolecularAssemblerSupportedPattern pattern) {
            return switch (this) {
                case CRAFTING -> pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN).recipeId();
                case STONECUTTING -> pattern.getDefinition().get(AEComponents.ENCODED_STONECUTTING_PATTERN).recipeId();
                case SMITHING -> pattern.getDefinition().get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN).recipeId();
            };
        }
    }

    /**
     * Creates one Data Energistics-owned built-in resolver ID.
     */
    private static @NotNull ResourceLocation resolverId(@NotNull String path) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, path);
    }
}
