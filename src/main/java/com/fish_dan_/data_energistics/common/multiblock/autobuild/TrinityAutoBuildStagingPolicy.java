package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.StagingPolicy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.definitions.AEBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Explicit staging allowlist for the three audited Trinity structures.
 *
 * <p>
 * This is deliberately a closed list rather than a namespace-wide or compatibility-based rule. Adding a new
 * structure candidate requires an explicit review before it can enter the silent world mutation path.
 * </p>
 */
public final class TrinityAutoBuildStagingPolicy implements StagingPolicy {

    /** Shared immutable policy used by the Trinity Data Core production entry point. */
    public static final TrinityAutoBuildStagingPolicy INSTANCE = new TrinityAutoBuildStagingPolicy();

    /** Exact block ids declared by the audited main, CPU, and crafting structure JSON definitions. */
    private static final Set<String> APPROVED_BLOCK_IDS = Set.of(
            "data_energistics:me_digital_storage_core_1m",
            "data_energistics:me_digital_storage_core_4m",
            "data_energistics:me_digital_storage_core_16m",
            "data_energistics:me_digital_storage_core_64m",
            "data_energistics:me_digital_storage_core_256m",
            "data_energistics:me_digital_storage_core_1g",
            "data_energistics:me_digital_storage_core_4g",
            "data_energistics:me_digital_storage_core_16g",
            "data_energistics:me_digital_storage_core_64g",
            "data_energistics:me_digital_storage_core_256g",
            "data_energistics:me_digital_merged_storage_core_1m",
            "data_energistics:me_digital_merged_storage_core_4m",
            "data_energistics:me_digital_merged_storage_core_16m",
            "data_energistics:me_digital_merged_storage_core_64m",
            "data_energistics:me_digital_merged_storage_core_256m",
            "data_energistics:me_digital_merged_storage_core_1g",
            "data_energistics:me_digital_merged_storage_core_4g",
            "data_energistics:me_digital_merged_storage_core_16g",
            "data_energistics:me_digital_merged_storage_core_64g",
            "data_energistics:me_digital_merged_storage_core_256g",
            "data_energistics:me_digital_pattern_processing_core",
            "data_energistics:extended_me_digital_pattern_processing_core",
            "data_energistics:overlimit_me_digital_pattern_processing_core",
            "data_energistics:me_access_hatch",
            "data_energistics:data_framework",
            "data_energistics:data_meteorite_0",
            "data_energistics:data_meteorite_1",
            "data_energistics:data_meteorite_2",
            "ae2:quartz_vibrant_glass",
            "ae2:smooth_sky_stone_block",
            "ae2:not_so_mysterious_cube",
            "ae2:fluix_block",
            "ae2:smooth_sky_stone_wall",
            "ae2:controller",
            "ae2:spatial_pylon",
            "ae2:energy_acceptor",
            "ae2:chiseled_quartz_wall",
            "ae2:smooth_sky_stone_slab",
            "ae2:cut_quartz_wall",
            "ae2:fluix_wall",
            "ae2:quartz_fixture",
            "ae2:cut_quartz_block",
            "ae2:quartz_glass",
            "minecraft:sea_lantern",
            "minecraft:smooth_quartz",
            "minecraft:smooth_quartz_slab",
            "minecraft:smooth_quartz_stairs",
            "minecraft:end_rod",
            "minecraft:glass",
            "minecraft:quartz_slab");

    /**
     * Neighbor-shape-independent blocks whose real-world pre-commit writes were audited.
     *
     * <p>
     * Entity blocks, neighbor-shape-dependent blocks, the AE2 Mysterious Cube, and every unreviewed candidate remain
     * overlay-only until publication. The end rod is included because it is the audited support for the quartz fixture
     * in the Trinity structure.
     * </p>
     */
    private static final Set<String> APPROVED_PHYSICAL_STAGING_BLOCK_IDS = Set.of(
            "data_energistics:data_framework",
            "data_energistics:data_meteorite_0",
            "data_energistics:data_meteorite_1",
            "data_energistics:data_meteorite_2",
            "ae2:quartz_vibrant_glass",
            "ae2:smooth_sky_stone_block",
            "ae2:fluix_block",
            "ae2:cut_quartz_block",
            "ae2:quartz_glass",
            "minecraft:sea_lantern",
            "minecraft:smooth_quartz",
            "minecraft:end_rod",
            "minecraft:glass");

    /** Exact covered-cable items declared by the main Trinity structure's placement-items predicate. */
    private static final Set<String> APPROVED_PART_ITEM_IDS = Set.of(
            "ae2:fluix_covered_cable",
            "ae2:white_covered_cable",
            "ae2:light_gray_covered_cable",
            "ae2:gray_covered_cable",
            "ae2:black_covered_cable",
            "ae2:lime_covered_cable",
            "ae2:yellow_covered_cable",
            "ae2:orange_covered_cable",
            "ae2:brown_covered_cable",
            "ae2:red_covered_cable",
            "ae2:pink_covered_cable",
            "ae2:magenta_covered_cable",
            "ae2:purple_covered_cable",
            "ae2:blue_covered_cable",
            "ae2:light_blue_covered_cable",
            "ae2:cyan_covered_cable",
            "ae2:green_covered_cable");

    private TrinityAutoBuildStagingPolicy() {}

    @Override
    public boolean canStageBlock(BlockPos position, ItemStack stack, BlockState desiredState) {
        return APPROVED_BLOCK_IDS.contains(BuiltInRegistries.BLOCK.getKey(desiredState.getBlock()).toString());
    }

    @Override
    public boolean canPhysicallyStageBlock(BlockPos position, ItemStack stack, BlockState desiredState) {
        return APPROVED_PHYSICAL_STAGING_BLOCK_IDS.contains(
                BuiltInRegistries.BLOCK.getKey(desiredState.getBlock()).toString());
    }

    @Nullable
    @Override
    public BlockState partHostState(BlockPos position, ItemStack partStack, Direction side) {
        if (!APPROVED_PART_ITEM_IDS.contains(BuiltInRegistries.ITEM.getKey(partStack.getItem()).toString())) {
            return null;
        }
        return AEBlocks.CABLE_BUS.block().defaultBlockState();
    }
}
