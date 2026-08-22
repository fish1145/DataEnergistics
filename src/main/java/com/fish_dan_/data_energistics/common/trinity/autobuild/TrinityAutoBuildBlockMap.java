package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreKind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps generic auto-build predicate categories to explicit, one-based tiered Trinity core registrations and the
 * universal lowest-rank fallback accepted by every category.
 */
public final class TrinityAutoBuildBlockMap {

    /** Predicate category for the ten storage cores and universal empty unit used by the main structure. */
    public static final String STORAGE_CORE = "storage_core";
    /** Predicate category for the ten merged storage cores and universal empty unit used by the CPU child structure. */
    public static final String PARALLEL_CPU_CORE = "parallel_cpu_core";
    /** Predicate category for the three pattern processing cores and universal empty unit used by crafting. */
    public static final String PATTERN_PROCESSING_CORE = "pattern_processing_core";

    /**
     * Universal structure candidate appended after historical selections so every existing one-based value stays
     * stable.
     */
    private static final ResourceLocation EMPTY_TRINITY_UNIT_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "empty_trinity_unit");
    /** Lowest positive replacement rank, leaving every ordinary selectable core above the universal unit. */
    private static final int EMPTY_TRINITY_UNIT_RANK = 1;

    /** Stable category presentation order shared by the auto-build request UI and payload diagnostics. */
    private static final List<String> CATEGORY_ORDER = List.of(
            STORAGE_CORE,
            PARALLEL_CPU_CORE,
            PATTERN_PROCESSING_CORE);

    /** Immutable category-to-tier definitions used by validation and runtime block resolution. */
    private static final Map<String, List<TierDefinition>> CATEGORIES = Map.of(
            STORAGE_CORE,
            List.of(
                    tier("me_digital_storage_core_1k", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_4k", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_16k", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_64k", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_256k", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_1m", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_4m", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_16m", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_64m", TrinityCoreKind.STORAGE_TYPES),
                    tier("me_digital_storage_core_256m", TrinityCoreKind.STORAGE_TYPES),
                    emptyTier(TrinityCoreKind.STORAGE_TYPES)),
            PARALLEL_CPU_CORE,
            List.of(
                    tier("me_digital_merged_storage_core_1k", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_4k", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_16k", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_64k", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_256k", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_1m", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_4m", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_16m", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_64m", TrinityCoreKind.PARALLEL_CPU),
                    tier("me_digital_merged_storage_core_256m", TrinityCoreKind.PARALLEL_CPU),
                    emptyTier(TrinityCoreKind.PARALLEL_CPU)),
            PATTERN_PROCESSING_CORE,
            List.of(
                    tier("me_digital_pattern_processing_core", TrinityCoreKind.PATTERN_PROCESSING),
                    tier("extended_me_digital_pattern_processing_core", TrinityCoreKind.PATTERN_PROCESSING),
                    tier("overlimit_me_digital_pattern_processing_core", TrinityCoreKind.PATTERN_PROCESSING),
                    emptyTier(TrinityCoreKind.PATTERN_PROCESSING)));

    private TrinityAutoBuildBlockMap() {}

    /**
     * Returns immutable category metadata without resolving Minecraft block registrations.
     */
    public static Map<String, List<ResourceLocation>> categories() {
        Map<String, List<ResourceLocation>> categories = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            List<TierDefinition> tiers = CATEGORIES.get(category);
            List<ResourceLocation> blockIds = new ArrayList<>(tiers.size());
            for (TierDefinition tier : tiers) {
                blockIds.add(tier.blockId());
            }
            categories.put(category, List.copyOf(blockIds));
        }
        return Collections.unmodifiableMap(categories);
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
        return resolveTierBlock(tierDefinition(category, tierIndex));
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

    /**
     * Returns the single predicate category that belongs to one Trinity structure selector.
     *
     * @param structureIndex request selector defined by {@link TrinityAutoBuildRequest}
     * @return category whose tiers may be selected for the requested structure
     */
    public static String categoryForStructure(int structureIndex) {
        return switch (structureIndex) {
            case TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX -> STORAGE_CORE;
            case TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX -> PARALLEL_CPU_CORE;
            case TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX -> PATTERN_PROCESSING_CORE;
            default -> throw new IllegalArgumentException("Unknown Trinity auto-build structure index: " + structureIndex);
        };
    }

    /**
     * Validates a build action and expands its sole tier selection into the complete candidate-to-selected map used by
     * {@code MultiBlockAutoBuild}.
     *
     * <p>
     * Each affected MDLib predicate accepts every tier in one category plus the universal empty unit. The returned map
     * therefore maps every one of those candidates to the selected block, which lets the atomic builder reject
     * incomplete or mixed tiers without changing the historical one-based selection values.
     * </p>
     *
     * @param structureIndex structure selector being built
     * @param repeatCount    requested repeat count
     * @param tierSelections one category and one one-based tier selected by the request
     * @return immutable block-candidate to selected-block mapping
     */
    public static Map<Block, Block> selectedTierBlocks(int structureIndex,
                                                       int repeatCount,
                                                       Map<String, Integer> tierSelections) {
        validateRepeatCount(structureIndex, repeatCount);
        String requiredCategory = categoryForStructure(structureIndex);
        if (tierSelections.size() != 1 || !tierSelections.containsKey(requiredCategory)) {
            throw new IllegalArgumentException("Trinity auto-build structure " + structureIndex +
                    " requires exactly one '" + requiredCategory + "' tier selection");
        }

        Block selectedBlock = resolveBlock(requiredCategory, tierSelections.get(requiredCategory));
        LinkedHashMap<Block, Block> selections = new LinkedHashMap<>();
        for (TierDefinition tier : CATEGORIES.get(requiredCategory)) {
            selections.put(resolveTierBlock(tier), selectedBlock);
        }
        return Map.copyOf(selections);
    }

    /**
     * Resolves the ordered candidate ranks for one structure's exclusive core category.
     *
     * <p>
     * The atomic builder uses these ranks to permit a selected higher tier to replace a legal lower tier while
     * refusing downgrades or categories that do not explicitly declare an order.
     * </p>
     *
     * @param structureIndex structure selector being built
     * @return immutable candidate block to positive tier-rank mapping
     */
    public static Map<Block, Integer> tierRanksForStructure(int structureIndex) {
        String category = categoryForStructure(structureIndex);
        LinkedHashMap<Block, Integer> ranks = new LinkedHashMap<>();
        List<TierDefinition> tiers = CATEGORIES.get(category);
        for (int index = 0; index < tiers.size(); index++) {
            TierDefinition tier = tiers.get(index);
            int rank = EMPTY_TRINITY_UNIT_ID.equals(tier.blockId()) ? EMPTY_TRINITY_UNIT_RANK : index + 2;
            ranks.put(resolveTierBlock(tier), rank);
        }
        return Map.copyOf(ranks);
    }

    private static TierDefinition tier(String blockPath, TrinityCoreKind coreKind) {
        return new TierDefinition(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, blockPath), coreKind);
    }

    private static TierDefinition emptyTier(TrinityCoreKind coreKind) {
        return new TierDefinition(EMPTY_TRINITY_UNIT_ID, coreKind);
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

    private static void validateRepeatCount(int structureIndex, int repeatCount) {
        if (structureIndex == TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX) {
            if (repeatCount != 1) {
                throw new IllegalArgumentException("Trinity main structure repeat count must be 1: " + repeatCount);
            }
            return;
        }
        if (structureIndex == TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX ||
                structureIndex == TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX) {
            if (repeatCount < TrinityAutoBuildOptions.MIN_REPEAT_COUNT ||
                    repeatCount > TrinityAutoBuildOptions.MAX_REPEAT_COUNT) {
                throw new IllegalArgumentException("Trinity child structure repeat count must be between " +
                        TrinityAutoBuildOptions.MIN_REPEAT_COUNT + " and " + TrinityAutoBuildOptions.MAX_REPEAT_COUNT +
                        ": " + repeatCount);
            }
            return;
        }
        throw new IllegalArgumentException("Unknown Trinity auto-build structure index: " + structureIndex);
    }

    private static Block resolveTierBlock(TierDefinition tier) {
        Block block = BuiltInRegistries.BLOCK.get(tier.blockId());
        if (!tier.blockId().equals(BuiltInRegistries.BLOCK.getKey(block))) {
            throw new IllegalStateException("Trinity auto-build tier is not registered: " + tier.blockId());
        }
        if (!(block instanceof TrinityCoreComponent component) || !component.supportsKind(tier.coreKind())) {
            throw new IllegalStateException("Trinity auto-build tier " + tier.blockId() + " does not provide " +
                    tier.coreKind());
        }
        return block;
    }

    /**
     * One registered tier within a generic auto-build predicate category.
     */
    private record TierDefinition(ResourceLocation blockId, TrinityCoreKind coreKind) {}
}
