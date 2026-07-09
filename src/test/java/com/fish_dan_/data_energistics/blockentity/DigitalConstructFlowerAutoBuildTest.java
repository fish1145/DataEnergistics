package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPlacementPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStatePropertiesPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStatePropertiesPredicate.StatePattern;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStatePropertiesPredicate.StatePropertyValue;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.network.DigitalConstructFlowerAutoBuildTarget;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.Predicates;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import com.modularmc.mdl.api.multiblock.structurepredicate.BlockPredicate;
import com.modularmc.mdl.api.multiblock.util.RelativeDirection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DigitalConstructFlowerAutoBuildTest {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(2, 1, 1);
    private static final String STRUCTURE_NAME = "test";

    private DigitalConstructFlowerAutoBuildTest() {}

    @TestHolder("digital_construct_flower_auto_build_only_places_requested_pattern")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void onlyPlacesRequestedPattern(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = creativePlayer(helper);

        DigitalConstructFlowerAutoBuild.Stats mainStats = build(
                level,
                player,
                world(level),
                twoCellPattern(Blocks.GOLD_BLOCK.defaultBlockState()),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(mainStats.placed(), 1, "Requested pattern should place its one target");
        helper.assertValueEqual(level.getBlockState(helper.absolutePos(TARGET)).getBlock(), Blocks.GOLD_BLOCK, "Main pattern should place gold");

        helper.setBlock(TARGET, Blocks.AIR.defaultBlockState());
        DigitalConstructFlowerAutoBuild.Stats cpuStats = build(
                level,
                player,
                world(level),
                twoCellPattern(Blocks.DIAMOND_BLOCK.defaultBlockState()),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(cpuStats.placed(), 1, "Second requested pattern should place its one target");
        helper.assertValueEqual(level.getBlockState(helper.absolutePos(TARGET)).getBlock(), Blocks.DIAMOND_BLOCK, "CPU pattern should place diamond");
        helper.assertValueEqual(
                level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 1))).getBlock(),
                Blocks.AIR,
                "Auto-build should not walk any unrequested pattern");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_target_maps_to_requested_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void targetMapsToRequestedStructure(GameTestHelper helper) {
        ResourceLocation trinityDigitalCore = ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_ID);

        helper.assertValueEqual(
                DigitalConstructFlowerBlockEntity.autoBuildDefinitionKey(DigitalConstructFlowerAutoBuildTarget.MAIN),
                JsonMultiBlockStructureKey.main(trinityDigitalCore),
                "MAIN auto-build target should select only the main structure definition");
        helper.assertValueEqual(
                DigitalConstructFlowerBlockEntity.autoBuildStructureName(DigitalConstructFlowerAutoBuildTarget.MAIN),
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME,
                "MAIN auto-build target should build only the main structure name");
        helper.assertValueEqual(
                DigitalConstructFlowerBlockEntity.autoBuildDefinitionKey(DigitalConstructFlowerAutoBuildTarget.CPU),
                new JsonMultiBlockStructureKey(trinityDigitalCore, ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME),
                "CPU auto-build target should select only the CPU child definition");
        helper.assertValueEqual(
                DigitalConstructFlowerBlockEntity.autoBuildStructureName(DigitalConstructFlowerAutoBuildTarget.CPU),
                ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME,
                "CPU auto-build target should build only the CPU child structure name");
        helper.assertValueEqual(
                DigitalConstructFlowerBlockEntity.autoBuildDefinitionKey(DigitalConstructFlowerAutoBuildTarget.CRAFTING),
                new JsonMultiBlockStructureKey(trinityDigitalCore, ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME),
                "CRAFTING auto-build target should select only the crafting child definition");
        helper.assertValueEqual(
                DigitalConstructFlowerBlockEntity.autoBuildStructureName(DigitalConstructFlowerAutoBuildTarget.CRAFTING),
                ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME,
                "CRAFTING auto-build target should build only the crafting child structure name");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_applies_block_state_after_placement")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void appliesBlockStateAfterPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState desiredState = Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);

        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                creativePlayer(helper),
                world(level),
                twoCellPattern(desiredState),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(stats.placed(), 1, "Block-state pattern should be placed");
        helper.assertValueEqual(
                level.getBlockState(helper.absolutePos(TARGET)),
                desiredState,
                "Auto-build should apply the declared block state after placement");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_counts_missing_block_candidate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void countsMissingBlockCandidate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                player,
                world(level),
                twoCellPattern(Blocks.EMERALD_BLOCK.defaultBlockState()),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(stats.missing(), 1, "Survival player without inventory should miss the candidate");
        helper.assertValueEqual(level.getBlockState(helper.absolutePos(TARGET)).getBlock(), Blocks.AIR, "Missing candidate should leave the target empty");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_counts_blocked_target")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void countsBlockedTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET, Blocks.OBSIDIAN.defaultBlockState());

        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                creativePlayer(helper),
                world(level),
                twoCellPattern(Blocks.GLASS.defaultBlockState()),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(stats.blocked(), 1, "Non-replaceable target should be counted as blocked");
        helper.assertValueEqual(level.getBlockState(helper.absolutePos(TARGET)).getBlock(), Blocks.OBSIDIAN, "Blocked target should not be replaced");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_counts_unsupported_placement_item_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void countsUnsupportedPlacementItemFailure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                creativePlayer(helper),
                world(level),
                twoCellPattern(unsupportedStickPredicate(Blocks.GLASS.defaultBlockState())),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(stats.placeFailed(), 1, "Unsupported non-block placement item should fail placement");
        helper.assertValueEqual(level.getBlockState(helper.absolutePos(TARGET)).getBlock(), Blocks.AIR, "Failed placement should leave the target empty");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_places_ae2_cable_bus_part_candidate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void placesAe2CableBusPartCandidate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block cableBusBlock = block("ae2:cable_bus");
        Item fluixCoveredCable = item("ae2:fluix_covered_cable");
        BlockPattern pattern = twoCellPattern(new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new BlockPredicate(List.of(cableBusBlock)),
                List.of(fluixCoveredCable.getDefaultInstance()))));

        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                creativePlayer(helper),
                world(level),
                pattern,
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(stats.placed(), 1, "AE2 cable part item should place a cable bus slot");
        helper.assertValueEqual(
                level.getBlockState(helper.absolutePos(TARGET)).getBlock(),
                cableBusBlock,
                "Fluix covered cable should produce an AE2 cable bus block");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_counts_unloaded_world_view_target")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void countsUnloadedWorldViewTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                creativePlayer(helper),
                unloadedView(level, helper.absolutePos(TARGET)),
                twoCellPattern(Blocks.IRON_BLOCK.defaultBlockState()),
                helper.absolutePos(ORIGIN));

        helper.assertValueEqual(stats.unloaded(), 1, "Unloaded structure view target should be counted as unloaded");
        helper.assertValueEqual(level.getBlockState(helper.absolutePos(TARGET)).getBlock(), Blocks.AIR, "Unloaded target should not be placed");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_auto_build_uses_exported_child_anchor_height")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesExportedChildAnchorHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos lowerTarget = helper.absolutePos(new BlockPos(2, 1, 2));

        DigitalConstructFlowerAutoBuild.Stats stats = build(
                level,
                creativePlayer(helper),
                world(level),
                anchoredHeightPattern(Blocks.LAPIS_BLOCK.defaultBlockState()),
                origin);

        helper.assertValueEqual(stats.placed(), 1, "Auto-build should place the child block relative to the exported anchor height");
        helper.assertValueEqual(
                level.getBlockState(lowerTarget).getBlock(),
                Blocks.LAPIS_BLOCK,
                "Child structure targets below the exported host anchor should be placed below the origin");
        helper.succeed();
    }

    private static DigitalConstructFlowerAutoBuild.Stats build(ServerLevel level,
                                                               Player player,
                                                               StructureWorldView world,
                                                               BlockPattern pattern,
                                                               BlockPos origin) {
        return DigitalConstructFlowerAutoBuild.buildPattern(
                level,
                player,
                world,
                pattern,
                origin,
                STRUCTURE_NAME,
                Direction.NORTH,
                false);
    }

    private static Player creativePlayer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.CREATIVE);
    }

    private static BlockPattern twoCellPattern(BlockState targetState) {
        return twoCellPattern(blockStatePredicate(targetState));
    }

    private static BlockPattern twoCellPattern(TraceabilityPredicate targetPredicate) {
        return FactoryBlockPattern.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
                .aisle("~X")
                .where('~', Predicates.any())
                .where('X', targetPredicate)
                .build();
    }

    private static BlockPattern anchoredHeightPattern(BlockState targetState) {
        return FactoryBlockPattern.start(RelativeDirection.RIGHT, RelativeDirection.FRONT, RelativeDirection.UP)
                .aisle(
                        " ",
                        " ")
                .aisle(
                        " ",
                        "X")
                .aisle(
                        " ",
                        "~")
                .where('~', Predicates.any())
                .where('X', blockStatePredicate(targetState))
                .build();
    }

    private static TraceabilityPredicate blockStatePredicate(BlockState state) {
        return new TraceabilityPredicate(new JsonMultiBlockStatePropertiesPredicate(List.of(statePattern(state))));
    }

    private static TraceabilityPredicate unsupportedStickPredicate(BlockState acceptedState) {
        return new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new JsonMultiBlockStatePropertiesPredicate(List.of(statePattern(acceptedState))),
                List.of(new ItemStack(Items.STICK))));
    }

    private static StatePattern statePattern(BlockState state) {
        List<StatePropertyValue<?>> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            properties.add(statePropertyValue(state, property));
        }
        return new StatePattern(
                state.getBlock(),
                properties);
    }

    private static <T extends Comparable<T>> StatePropertyValue<T> statePropertyValue(BlockState state, Property<T> property) {
        return new StatePropertyValue<>(property, state.getValue(property));
    }

    private static Block block(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (!location.equals(BuiltInRegistries.BLOCK.getKey(block))) {
            throw new IllegalStateException("Missing test block: " + id);
        }
        return block;
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        Item item = BuiltInRegistries.ITEM.get(location);
        if (!location.equals(BuiltInRegistries.ITEM.getKey(item))) {
            throw new IllegalStateException("Missing test item: " + id);
        }
        return item;
    }

    private static StructureWorldView world(Level level) {
        return new LevelView(level);
    }

    private static StructureWorldView unloadedView(Level level, BlockPos unloadedTarget) {
        return new UnloadedTargetView(level, unloadedTarget);
    }

    private record LevelView(Level level) implements StructureWorldView {

        @Override
        public boolean isLoaded(BlockPos pos) {
            return this.level.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }
    }

    private record UnloadedTargetView(Level level, BlockPos unloadedTarget) implements StructureWorldView {

        @Override
        public boolean isLoaded(BlockPos pos) {
            return !pos.equals(this.unloadedTarget) && this.level.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }
    }
}
