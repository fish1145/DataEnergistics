package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import appeng.api.ids.AEComponents;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic registry for Trinity pattern recipe-ID resolvers.
 *
 * <p>
 * The global registry contains AE2 crafting, stonecutting, and smithing support. Integrations may register an
 * additional resolver during common setup. Duplicate registration IDs and ambiguous matches fail immediately.
 * </p>
 */
public final class TrinityPatternRecipeIdResolvers {

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

    private static final TrinityPatternRecipeIdResolvers GLOBAL = createWithBuiltIns();

    private final Map<ResourceLocation, TrinityPatternRecipeIdResolver> resolvers = new LinkedHashMap<>();

    /**
     * Creates an empty registry, primarily for isolated integration and logic tests.
     */
    public TrinityPatternRecipeIdResolvers() {}

    /**
     * @return process-wide registry used by world-backed pattern cores
     */
    public static TrinityPatternRecipeIdResolvers global() {
        return GLOBAL;
    }

    /**
     * Registers one process-wide extension resolver.
     *
     * @param resolver uniquely identified resolver
     */
    public static void registerGlobal(TrinityPatternRecipeIdResolver resolver) {
        GLOBAL.register(resolver);
    }

    /**
     * Creates an isolated registry containing the three built-in AE2 resolvers.
     *
     * @return mutable registry initialized with built-in support
     */
    public static TrinityPatternRecipeIdResolvers createWithBuiltIns() {
        TrinityPatternRecipeIdResolvers registry = new TrinityPatternRecipeIdResolvers();
        registry.register(BuiltInResolver.CRAFTING);
        registry.register(BuiltInResolver.STONECUTTING);
        registry.register(BuiltInResolver.SMITHING);
        return registry;
    }

    /**
     * Registers one resolver by its stable ID.
     *
     * @param resolver resolver to add
     * @throws IllegalArgumentException when the ID is already registered
     */
    public synchronized void register(TrinityPatternRecipeIdResolver resolver) {
        ResourceLocation resolverId = resolver.id();
        if (resolverId == null) {
            throw new IllegalArgumentException("Trinity pattern recipe resolver ID must not be null");
        }
        TrinityPatternRecipeIdResolver previous = this.resolvers.putIfAbsent(resolverId, resolver);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate Trinity pattern recipe resolver ID: " + resolverId);
        }
    }

    /**
     * Resolves exactly one stable recipe identity for a decoded pattern.
     *
     * @param pattern decoded pattern to resolve
     * @return the sole matching identity, or empty when the pattern is opaque to every registered resolver
     * @throws IllegalStateException when multiple resolvers match
     */
    public synchronized Optional<Resolution> resolve(IMolecularAssemblerSupportedPattern pattern) {
        List<Map.Entry<ResourceLocation, TrinityPatternRecipeIdResolver>> matches = new ArrayList<>();
        for (Map.Entry<ResourceLocation, TrinityPatternRecipeIdResolver> entry : this.resolvers.entrySet()) {
            if (entry.getValue().supports(pattern)) {
                matches.add(entry);
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Ambiguous Trinity pattern recipe resolvers " +
                    matches.stream().map(Map.Entry::getKey).toList());
        }
        Map.Entry<ResourceLocation, TrinityPatternRecipeIdResolver> match = matches.getFirst();
        ResourceLocation recipeId = match.getValue().recipeId(pattern);
        if (recipeId == null) {
            throw new IllegalStateException(
                    "Trinity pattern recipe resolver " + match.getKey() + " returned a null recipe ID");
        }
        return Optional.of(new Resolution(match.getKey(), recipeId));
    }

    /**
     * Stable result retained with a pattern definition so reloads cannot silently reinterpret queued work.
     *
     * @param resolverId resolver registration that established the identity
     * @param recipeId   recipe selected by the encoded pattern
     */
    public record Resolution(ResourceLocation resolverId, ResourceLocation recipeId) {

        public Resolution {
            if (resolverId == null || recipeId == null) {
                throw new IllegalArgumentException("Trinity pattern recipe resolution IDs must not be null");
            }
        }
    }

    /**
     * Built-in AE2 component resolvers kept private so integrations use the public registry contract.
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
        public ResourceLocation id() {
            return this.id;
        }

        @Override
        public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
            return switch (this) {
                case CRAFTING -> pattern.getDefinition().toStack().has(AEComponents.ENCODED_CRAFTING_PATTERN);
                case STONECUTTING -> pattern.getDefinition().toStack()
                        .has(AEComponents.ENCODED_STONECUTTING_PATTERN);
                case SMITHING -> pattern.getDefinition().toStack()
                        .has(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
            };
        }

        @Override
        public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
            return switch (this) {
                case CRAFTING -> pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN).recipeId();
                case STONECUTTING -> pattern.getDefinition().get(AEComponents.ENCODED_STONECUTTING_PATTERN).recipeId();
                case SMITHING -> pattern.getDefinition().get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN).recipeId();
            };
        }
    }

    private static ResourceLocation resolverId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, path);
    }
}
