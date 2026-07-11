package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Maps generic auto-build predicate categories to explicit, one-based tiered Trinity core registrations.
 */
public final class TrinityAutoBuildBlockMap {

    /** Predicate category for the ten merged storage cores used by the CPU child structure. */
    public static final String PARALLEL_CPU_CORE = "parallel_cpu_core";
    /** Predicate category for the three pattern processing cores used by the crafting child structure. */
    public static final String PATTERN_PROCESSING_CORE = "pattern_processing_core";

    /** Immutable category-to-tier definitions used by validation and runtime block resolution. */
    private static final Map<String, List<TierDefinition>> CATEGORIES = Map.of(
            PARALLEL_CPU_CORE,
            List.of(
                    tier("me_digital_merged_storage_core_1m", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_1M.get()),
                    tier("me_digital_merged_storage_core_4m", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_4M.get()),
                    tier("me_digital_merged_storage_core_16m", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_16M.get()),
                    tier("me_digital_merged_storage_core_64m", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_64M.get()),
                    tier("me_digital_merged_storage_core_256m", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256M.get()),
                    tier("me_digital_merged_storage_core_1g", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_1G.get()),
                    tier("me_digital_merged_storage_core_4g", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_4G.get()),
                    tier("me_digital_merged_storage_core_16g", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_16G.get()),
                    tier("me_digital_merged_storage_core_64g", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_64G.get()),
                    tier("me_digital_merged_storage_core_256g", TrinityCoreKind.PARALLEL_CPU,
                            () -> ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256G.get())),
            PATTERN_PROCESSING_CORE,
            List.of(
                    tier("me_digital_pattern_processing_core", TrinityCoreKind.PATTERN_PROCESSING,
                            () -> ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get()),
                    tier("extended_me_digital_pattern_processing_core", TrinityCoreKind.PATTERN_PROCESSING,
                            () -> ModBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE.get()),
                    tier("overlimit_me_digital_pattern_processing_core", TrinityCoreKind.PATTERN_PROCESSING,
                            () -> ModBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE.get())));

    private TrinityAutoBuildBlockMap() {}

    /**
     * Returns immutable category metadata without resolving Minecraft block registrations.
     */
    public static Map<String, List<ResourceLocation>> categories() {
        Map<String, List<ResourceLocation>> categories = new LinkedHashMap<>();
        for (Map.Entry<String, List<TierDefinition>> entry : CATEGORIES.entrySet()) {
            List<ResourceLocation> blockIds = new ArrayList<>(entry.getValue().size());
            for (TierDefinition tier : entry.getValue()) {
                blockIds.add(tier.blockId());
            }
            categories.put(entry.getKey(), List.copyOf(blockIds));
        }
        return Map.copyOf(categories);
    }

    /**
     * Returns the registered block id selected by a one-based tier index without bootstrapping its block class.
     *
     * @param category  predicate category declared by an auto-build descriptor
     * @param tierIndex one-based selected tier within that category
     * @return exact selected block id
     */
    public static ResourceLocation blockId(String category, int tierIndex) {
        return tierDefinition(category, tierIndex).blockId();
    }

    /**
     * Returns the Trinity capability contributed by every tier in the requested predicate category.
     *
     * @param category predicate category declared by an auto-build descriptor
     * @return capability kind expected from resolved blocks in that category
     */
    public static TrinityCoreKind coreKind(String category) {
        List<TierDefinition> tiers = CATEGORIES.get(category);
        if (tiers == null) {
            throw new IllegalArgumentException("Unknown Trinity auto-build tier category: " + category);
        }
        return tiers.getFirst().coreKind();
    }

    /**
     * Resolves and validates the exact registered block selected by a category and one-based tier index.
     *
     * @param category  predicate category declared by an auto-build descriptor
     * @param tierIndex one-based selected tier within that category
     * @return registered core block whose capability matches the category declaration
     */
    public static Block resolveBlock(String category, int tierIndex) {
        TierDefinition tier = tierDefinition(category, tierIndex);
        Block block = tier.blockSupplier().get();
        if (!(block instanceof TrinityCoreComponent component) || component.kind() != tier.coreKind()) {
            throw new IllegalStateException("Trinity auto-build tier " + tier.blockId() + " does not provide " +
                    tier.coreKind());
        }
        return block;
    }

    /**
     * Validates every category and one-based tier index supplied by an auto-build request.
     *
     * @param tierSelections requested predicate category to tier index map
     */
    public static void validateTierSelections(Map<String, Integer> tierSelections) {
        for (Map.Entry<String, Integer> entry : tierSelections.entrySet()) {
            tierDefinition(entry.getKey(), entry.getValue());
        }
    }

    private static TierDefinition tier(String blockPath,
                                       TrinityCoreKind coreKind,
                                       Supplier<? extends Block> blockSupplier) {
        return new TierDefinition(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, blockPath), coreKind,
                blockSupplier);
    }

    private static TierDefinition tierDefinition(String category, int tierIndex) {
        List<TierDefinition> tiers = CATEGORIES.get(category);
        if (tiers == null) {
            throw new IllegalArgumentException("Unknown Trinity auto-build tier category: " + category);
        }
        if (tierIndex < 1 || tierIndex > tiers.size()) {
            throw new IllegalArgumentException("Trinity auto-build tier index for " + category + " must be between 1 and " +
                    tiers.size() + ": " + tierIndex);
        }
        return tiers.get(tierIndex - 1);
    }

    /**
     * One registered tier within a generic auto-build predicate category.
     */
    private record TierDefinition(ResourceLocation blockId,
                                  TrinityCoreKind coreKind,
                                  Supplier<? extends Block> blockSupplier) {}
}
