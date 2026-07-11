package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.FailureType;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.PartSideResolver;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Result;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPlacementPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStatePropertiesPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStatePropertiesPredicate.StatePattern;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStatePropertiesPredicate.StatePropertyValue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.parts.PartHelper;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.Predicates;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import com.modularmc.mdl.api.multiblock.structurepredicate.BlockPredicate;
import com.modularmc.mdl.api.multiblock.util.RelativeDirection;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MultiBlockAutoBuildGameTest {

    private static final BlockPos ORIGIN = new BlockPos(4, 2, 4);
    private static final String STRUCTURE_NAME = "atomic_auto_build_test";
    private static final Direction FRONT = Direction.SOUTH;
    private static final MultiBlockAutoBuild AUTO_BUILD = new MultiBlockAutoBuildImpl();

    private MultiBlockAutoBuildGameTest() {}

    @TestHolder("multi_block_auto_build_uses_requested_repetition")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void usesRequestedRepetition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPattern pattern = repeatedPattern();

        Result result = execute(
                level,
                creativePlayer(helper),
                pattern,
                helper.absolutePos(ORIGIN),
                2,
                Map.of());

        helper.assertTrue(result.success(), "Requested repetition should build successfully");
        helper.assertValueEqual(result.placed(), 3, "Two repeated blocks and one fixed cap should be placed");
        helper.assertValueEqual(blockAt(level, pattern, helper.absolutePos(ORIGIN), 1), Blocks.IRON_BLOCK,
                "First requested repetition should be placed");
        helper.assertValueEqual(blockAt(level, pattern, helper.absolutePos(ORIGIN), 2), Blocks.IRON_BLOCK,
                "Second requested repetition should be placed");
        helper.assertValueEqual(blockAt(level, pattern, helper.absolutePos(ORIGIN), 3), Blocks.GOLD_BLOCK,
                "Fixed cap should follow the requested repeated layers");
        helper.assertValueEqual(blockAt(level, pattern, helper.absolutePos(ORIGIN), 4), Blocks.AIR,
                "Auto-build must not expand the variable unit to its maximum");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_rejects_invalid_repetition_before_mutation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void rejectsInvalidRepetitionBeforeMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPattern pattern = repeatedPattern();
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, creativePlayer(helper), pattern, origin, 4, Map.of());

        helper.assertFalse(result.success(), "Out-of-range repetition should be rejected");
        helper.assertValueEqual(result.failure().type(), FailureType.INVALID_REPETITION,
                "Failure should identify the invalid repetition");
        helper.assertValueEqual(blockAt(level, pattern, origin, 1), Blocks.AIR,
                "Invalid repetition must not change the world");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_resolves_selected_predicate_tier")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void resolvesSelectedPredicateTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPattern pattern = oneTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);
        Map<Block, Block> selectedTier = Map.of(
                Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK);

        Result first = execute(level, creativePlayer(helper), pattern, origin, 1, selectedTier);
        Result second = execute(level, creativePlayer(helper), pattern, origin, 1, selectedTier);

        helper.assertTrue(first.success(), "Selected predicate tier should build successfully");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.GOLD_BLOCK,
                "Tier selection should override candidate list order");
        helper.assertTrue(second.success(), "An already matching selected tier should remain reusable");
        helper.assertValueEqual(second.placed(), 0, "Reusable selected tier should not be replaced");
        helper.assertValueEqual(second.reused(), 1, "Reusable selected tier should be counted once");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_rejects_incomplete_tier_mapping_before_mutation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void rejectsIncompleteTierMappingBeforeMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.GOLD_BLOCK));
        BlockPattern pattern = oneTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, player, pattern, origin, 1, Map.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));

        helper.assertFalse(result.success(), "A partial tier mapping must be rejected before placement");
        helper.assertValueEqual(result.failure().type(), FailureType.INVALID_TIER_SELECTION,
                "Partial tier mapping should have a dedicated failure type");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.AIR,
                "Invalid tier selection must not change the world");
        helper.assertValueEqual(countItem(player, Blocks.GOLD_BLOCK.asItem()), 1,
                "Invalid tier selection must not reserve player materials");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_rejects_tier_target_outside_predicate_before_mutation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void rejectsTierTargetOutsidePredicateBeforeMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.DIRT));
        BlockPattern pattern = oneTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, player, pattern, origin, 1, Map.of(
                Blocks.IRON_BLOCK, Blocks.DIRT,
                Blocks.GOLD_BLOCK, Blocks.DIRT));

        helper.assertFalse(result.success(), "A tier target outside the predicate must be rejected before placement");
        helper.assertValueEqual(result.failure().type(), FailureType.INVALID_TIER_SELECTION,
                "Invalid tier target should have a dedicated failure type");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.AIR,
                "Invalid tier target must not change the world");
        helper.assertValueEqual(countItem(player, Blocks.DIRT.asItem()), 1,
                "Invalid tier target must not reserve player materials");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_rejects_inconsistent_tier_mapping_before_mutation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void rejectsInconsistentTierMappingBeforeMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.GOLD_BLOCK));
        BlockPattern pattern = oneTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, player, pattern, origin, 1, Map.of(
                Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK));

        helper.assertFalse(result.success(), "Tier candidates must resolve to one selected block");
        helper.assertValueEqual(result.failure().type(), FailureType.INVALID_TIER_SELECTION,
                "Inconsistent tier mapping should have a dedicated failure type");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.AIR,
                "Inconsistent tier mapping must not change the world");
        helper.assertValueEqual(countItem(player, Blocks.GOLD_BLOCK.asItem()), 1,
                "Inconsistent tier mapping must not reserve player materials");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_missing_material_is_atomic")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void missingMaterialIsAtomic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.IRON_BLOCK));
        BlockPattern pattern = twoTargetPattern(
                Predicates.blocks(Blocks.IRON_BLOCK),
                Predicates.blocks(Blocks.IRON_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, player, pattern, origin, 1, Map.of());

        helper.assertFalse(result.success(), "Insufficient aggregate material should reject the entire plan");
        helper.assertValueEqual(result.failure().type(), FailureType.MISSING_MATERIAL,
                "Failure should identify missing aggregate material");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.AIR,
                "First target must remain unchanged after preflight failure");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 2), Blocks.AIR,
                "Second target must remain unchanged after preflight failure");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 1,
                "Preflight failure must not consume the available material");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_retries_deferred_support_dependency")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void retriesDeferredSupportDependency(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block fixture = block("ae2:quartz_fixture");
        BlockState fixtureState = fixture.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        TraceabilityPredicate fixturePredicate = new TraceabilityPredicate(
                new JsonMultiBlockPlacementPredicate(
                        new JsonMultiBlockStatePropertiesPredicate(List.of(new StatePattern(
                                fixture,
                                List.of(new StatePropertyValue<>(BlockStateProperties.FACING, Direction.EAST))))),
                        List.of(fixture.asItem().getDefaultInstance())));
        BlockPattern pattern = twoTargetPattern(fixturePredicate, Predicates.blocks(Blocks.IRON_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos fixturePosition = position(pattern, origin, 0, 1);
        BlockPos supportPosition = position(pattern, origin, 0, 2);

        helper.assertValueEqual(supportPosition, fixturePosition.relative(Direction.WEST),
                "Test structure must visit the fixture before its western support");
        helper.assertFalse(fixtureState.canSurvive(level, fixturePosition),
                "Fixture must initially be unplaceable without its planned support");

        Result result = execute(level, creativePlayer(helper), pattern, origin, 1, Map.of());

        helper.assertTrue(result.success(), "Deferred fixture should place after its later planned support");
        helper.assertValueEqual(level.getBlockState(supportPosition).getBlock(), Blocks.IRON_BLOCK,
                "Later support placement should be retained");
        helper.assertValueEqual(level.getBlockState(fixturePosition).getBlock(), fixture,
                "Deferred fixture should be retried and placed");
        helper.assertValueEqual(level.getBlockState(fixturePosition).getValue(BlockStateProperties.FACING),
                Direction.EAST,
                "Retried fixture should keep its predicate-selected target state");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_runtime_failure_rolls_back")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void runtimeFailureRollsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.IRON_BLOCK));
        player.getInventory().setItem(1, new ItemStack(Blocks.DIRT));
        TraceabilityPredicate invalidPlacementPredicate = new TraceabilityPredicate(
                new JsonMultiBlockPlacementPredicate(
                        new BlockPredicate(List.of(Blocks.GLASS)),
                        List.of(Blocks.DIRT.asItem().getDefaultInstance())));
        BlockPattern pattern = twoTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK), invalidPlacementPredicate);
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, player, pattern, origin, 1, Map.of());

        helper.assertFalse(result.success(), "A placement item outside the predicate should fail verification");
        helper.assertValueEqual(result.failure().type(), FailureType.PLACE_FAILED,
                "Runtime predicate failure should be reported as placement failure");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.AIR,
                "Earlier successful placements must be rolled back");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 2), Blocks.AIR,
                "Failing placement must be rolled back");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 1,
                "Rollback must restore material consumed by earlier placements");
        helper.assertValueEqual(countItem(player, Blocks.DIRT.asItem()), 1,
                "Rollback must restore material consumed by the failing placement");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_wrong_tier_is_blocked")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void wrongTierIsBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.GOLD_BLOCK));
        BlockPattern pattern = oneTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos target = position(pattern, origin, 0, 1);
        level.setBlock(target, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        Result result = execute(level, player, pattern, origin, 1, Map.of(
                Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK));

        helper.assertFalse(result.success(), "An accepted but unselected tier should be treated as a conflict");
        helper.assertValueEqual(result.failure().type(), FailureType.BLOCKED,
                "Wrong selected tier should be reported as blocked");
        helper.assertValueEqual(level.getBlockState(target).getBlock(), Blocks.IRON_BLOCK,
                "Blocked tier conflict must not replace the existing block");
        helper.assertValueEqual(countItem(player, Blocks.GOLD_BLOCK.asItem()), 1,
                "Blocked tier conflict must not consume the selected core");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_upgrades_existing_tier_and_returns_loot")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void upgradesExistingTierAndReturnsLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.GOLD_BLOCK));
        BlockPattern pattern = oneTargetPattern(Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos target = position(pattern, origin, 0, 1);
        level.setBlock(target, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        Result result = execute(
                level,
                player,
                pattern,
                origin,
                1,
                Map.of(
                        Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                        Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK),
                Map.of(Blocks.IRON_BLOCK, 1, Blocks.GOLD_BLOCK, 2),
                (position, partStack) -> null);

        helper.assertTrue(result.success(), "A selected higher tier should replace an accepted lower tier");
        helper.assertValueEqual(level.getBlockState(target).getBlock(), Blocks.GOLD_BLOCK,
                "Tier upgrade should leave the requested higher tier in place");
        helper.assertValueEqual(countItem(player, Blocks.GOLD_BLOCK.asItem()), 0,
                "Tier upgrade should consume the requested higher-tier material");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 1,
                "Tier upgrade should return old-block loot to the player inventory first");
        helper.assertFalse(hasDroppedItem(level, target, Blocks.IRON_BLOCK.asItem()),
                "Tier upgrade should not drop loot while the player inventory accepts it");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_drops_tier_loot_after_player_inventory_overflow")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void dropsTierLootAfterPlayerInventoryOverflow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = creativePlayer(helper);
        fillInventory(player, new ItemStack(Blocks.DIRT, 64));
        BlockPattern pattern = twoTargetPattern(
                Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK),
                Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK));
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos firstTarget = position(pattern, origin, 0, 1);
        BlockPos secondTarget = position(pattern, origin, 0, 2);
        level.setBlock(firstTarget, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(secondTarget, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        Result result = execute(
                level,
                player,
                pattern,
                origin,
                1,
                Map.of(
                        Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                        Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK),
                Map.of(Blocks.IRON_BLOCK, 1, Blocks.GOLD_BLOCK, 2),
                (position, partStack) -> null);

        helper.assertTrue(result.success(), "Creative tier upgrades should commit with a full inventory");
        helper.assertValueEqual(result.placed(), 2, "Both lower-tier positions should be replaced");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 0,
                "Full player inventory must not absorb replacement loot");
        List<ItemStack> droppedIron = droppedItems(level, firstTarget, Blocks.IRON_BLOCK.asItem());
        helper.assertValueEqual(droppedIron.size(), 1,
                "Matching replacement loot should be merged into one world entity after inventory overflow");
        helper.assertValueEqual(droppedIron.getFirst().getCount(), 2,
                "Merged world replacement loot should preserve the aggregate item count");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_rolls_back_tier_replacement_without_delivering_loot")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void rollsBackTierReplacementWithoutDeliveringLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Blocks.GOLD_BLOCK));
        player.getInventory().setItem(1, new ItemStack(Blocks.DIRT));
        TraceabilityPredicate failingPredicate = new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new BlockPredicate(List.of(Blocks.GLASS)),
                List.of(Blocks.DIRT.asItem().getDefaultInstance())));
        BlockPattern pattern = twoTargetPattern(
                Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK),
                failingPredicate);
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos replacementTarget = position(pattern, origin, 0, 1);
        BlockPos failingTarget = position(pattern, origin, 0, 2);
        level.setBlock(replacementTarget, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        Result result = execute(
                level,
                player,
                pattern,
                origin,
                1,
                Map.of(
                        Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                        Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK),
                Map.of(Blocks.IRON_BLOCK, 1, Blocks.GOLD_BLOCK, 2),
                (position, partStack) -> null);

        helper.assertFalse(result.success(), "A later commit failure must roll back an earlier tier replacement");
        helper.assertValueEqual(result.failure().type(), FailureType.PLACE_FAILED,
                "The invalid later predicate should fail during placement verification");
        helper.assertValueEqual(level.getBlockState(replacementTarget).getBlock(), Blocks.IRON_BLOCK,
                "Failed transaction must restore the original lower tier");
        helper.assertValueEqual(level.getBlockState(failingTarget).getBlock(), Blocks.AIR,
                "Failed transaction must restore the later target position");
        helper.assertValueEqual(countItem(player, Blocks.GOLD_BLOCK.asItem()), 1,
                "Failed transaction must return selected high-tier material");
        helper.assertValueEqual(countItem(player, Blocks.DIRT.asItem()), 1,
                "Failed transaction must return later placement material");
        helper.assertValueEqual(countItem(player, Blocks.IRON_BLOCK.asItem()), 0,
                "Failed transaction must not deliver old-tier loot to the player inventory");
        helper.assertFalse(hasDroppedItem(level, replacementTarget, Blocks.IRON_BLOCK.asItem()),
                "Failed transaction must not emit old-tier loot into the world");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_places_ae2_part")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void placesAe2Part(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block cableBus = block("ae2:cable_bus");
        Item cable = item("ae2:fluix_covered_cable");
        TraceabilityPredicate predicate = new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new BlockPredicate(List.of(cableBus)),
                List.of(cable.getDefaultInstance())));
        BlockPattern pattern = oneTargetPattern(predicate);
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, creativePlayer(helper), pattern, origin, 1, Map.of(),
                (position, partStack) -> Direction.UP);
        BlockPos target = position(pattern, origin, 0, 1);

        helper.assertTrue(result.success(), "AE2 part candidates should use the part placement API");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), cableBus,
                "AE2 part placement should create its cable bus block");
        helper.assertTrue(PartHelper.getPart(level, target, null) != null,
                "Covered cable should occupy the cable bus center slot");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_repairs_existing_ae2_cable_bus_part")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void repairsExistingAe2CableBusPart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block cableBus = block("ae2:cable_bus");
        Item cable = item("ae2:fluix_covered_cable");
        TraceabilityPredicate predicate = new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new BlockPredicate(List.of(cableBus)),
                List.of(cable.getDefaultInstance())));
        BlockPattern pattern = oneTargetPattern(predicate);
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos target = position(pattern, origin, 0, 1);
        level.setBlock(target, cableBus.defaultBlockState(), Block.UPDATE_ALL);

        Result result = execute(level, creativePlayer(helper), pattern, origin, 1, Map.of(),
                (position, partStack) -> Direction.UP);

        helper.assertTrue(result.success(), "An empty existing Cable Bus must receive its required AE2 part");
        helper.assertValueEqual(result.placed(), 1,
                "Repairing the missing part should count as one committed placement");
        helper.assertValueEqual(level.getBlockState(target).getBlock(), cableBus,
                "Repairing a Cable Bus must retain the existing host block");
        helper.assertTrue(PartHelper.getPart(level, target, null) != null,
                "Repairing a Cable Bus must populate the covered-cable center slot");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_places_ae2_sided_part")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void placesAe2SidedPart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block cableBus = block("ae2:cable_bus");
        Item importBus = item("ae2:import_bus");
        TraceabilityPredicate predicate = new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new BlockPredicate(List.of(cableBus)),
                List.of(importBus.getDefaultInstance())));
        BlockPattern pattern = oneTargetPattern(predicate);
        BlockPos origin = helper.absolutePos(ORIGIN);
        BlockPos target = position(pattern, origin, 0, 1);

        Result result = execute(level, creativePlayer(helper), pattern, origin, 1, Map.of(),
                (position, partStack) -> Direction.UP);

        helper.assertTrue(result.success(), "AE2 sided parts should use the resolved host side");
        helper.assertValueEqual(level.getBlockState(target).getBlock(), cableBus,
                "AE2 sided part placement should create a cable bus host");
        helper.assertTrue(PartHelper.getPart(level, target, Direction.UP) != null,
                "Import bus should occupy the explicit UP host side");
        helper.succeed();
    }

    @TestHolder("multi_block_auto_build_rejects_ae2_part_without_side_before_mutation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void rejectsAe2PartWithoutSideBeforeMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Block cableBus = block("ae2:cable_bus");
        Item importBus = item("ae2:import_bus");
        player.getInventory().setItem(0, importBus.getDefaultInstance());
        TraceabilityPredicate predicate = new TraceabilityPredicate(new JsonMultiBlockPlacementPredicate(
                new BlockPredicate(List.of(cableBus)),
                List.of(importBus.getDefaultInstance())));
        BlockPattern pattern = oneTargetPattern(predicate);
        BlockPos origin = helper.absolutePos(ORIGIN);

        Result result = execute(level, player, pattern, origin, 1, Map.of());

        helper.assertFalse(result.success(), "AE2 parts without an explicit side must be rejected");
        helper.assertValueEqual(result.failure().type(), FailureType.UNSUPPORTED_CANDIDATE,
                "Missing AE2 host side should fail before the transaction commits");
        helper.assertValueEqual(blockAt(level, pattern, origin, 0, 1), Blocks.AIR,
                "Missing AE2 host side must not create a cable bus");
        helper.assertValueEqual(countItem(player, importBus), 1,
                "Missing AE2 host side must not reserve the part item");
        helper.succeed();
    }

    private static Result execute(ServerLevel level,
                                  Player player,
                                  BlockPattern pattern,
                                  BlockPos origin,
                                  int repeatCount,
                                  Map<Block, Block> selectedTierBlocks) {
        return execute(level, player, pattern, origin, repeatCount, selectedTierBlocks,
                (position, partStack) -> null);
    }

    private static Result execute(ServerLevel level,
                                  Player player,
                                  BlockPattern pattern,
                                  BlockPos origin,
                                  int repeatCount,
                                  Map<Block, Block> selectedTierBlocks,
                                  PartSideResolver partSideResolver) {
        return execute(level, player, pattern, origin, repeatCount, selectedTierBlocks, Map.of(), partSideResolver);
    }

    private static Result execute(ServerLevel level,
                                  Player player,
                                  BlockPattern pattern,
                                  BlockPos origin,
                                  int repeatCount,
                                  Map<Block, Block> selectedTierBlocks,
                                  Map<Block, Integer> tierRanks,
                                  PartSideResolver partSideResolver) {
        return AUTO_BUILD.execute(MultiBlockAutoBuild.Context.builder()
                .level(level)
                .player(player)
                .world(new LevelView(level))
                .pattern(pattern)
                .origin(origin)
                .structureName(STRUCTURE_NAME)
                .front(FRONT)
                .flipped(false)
                .repeatCount(repeatCount)
                .selectedTierBlocks(selectedTierBlocks)
                .tierRanks(tierRanks)
                .partSideResolver(partSideResolver)
                .build());
    }

    private static Player creativePlayer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.CREATIVE);
    }

    private static BlockPattern repeatedPattern() {
        return FactoryBlockPattern.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
                .aisle("~")
                .beginRepeatable()
                .aisle("X")
                .endRepeatable(1, 3)
                .aisle("Y")
                .where('~', Predicates.any())
                .where('X', Predicates.blocks(Blocks.IRON_BLOCK))
                .where('Y', Predicates.blocks(Blocks.GOLD_BLOCK))
                .build();
    }

    private static BlockPattern oneTargetPattern(TraceabilityPredicate predicate) {
        return FactoryBlockPattern.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
                .aisle("~X")
                .where('~', Predicates.any())
                .where('X', predicate)
                .build();
    }

    private static BlockPattern twoTargetPattern(TraceabilityPredicate first, TraceabilityPredicate second) {
        return FactoryBlockPattern.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
                .aisle("~AB")
                .where('~', Predicates.any())
                .where('A', first)
                .where('B', second)
                .build();
    }

    private static Block blockAt(Level level, BlockPattern pattern, BlockPos origin, int expandedZ) {
        return blockAt(level, pattern, origin, expandedZ, 0);
    }

    private static Block blockAt(Level level, BlockPattern pattern, BlockPos origin, int expandedZ, int xIndex) {
        return level.getBlockState(position(pattern, origin, expandedZ, xIndex)).getBlock();
    }

    private static BlockPos position(BlockPattern pattern, BlockPos origin, int expandedZ, int xIndex) {
        return origin.offset(pattern.getActualRelativeOffset(
                pattern.getMinX() + xIndex,
                pattern.getMinY(),
                pattern.getMinZ() + expandedZ,
                FRONT,
                Direction.NORTH,
                false));
    }

    private static int countItem(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void fillInventory(Player player, ItemStack stack) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, stack.copy());
        }
    }

    private static boolean hasDroppedItem(ServerLevel level, BlockPos position, Item item) {
        return !droppedItems(level, position, item).isEmpty();
    }

    private static List<ItemStack> droppedItems(ServerLevel level, BlockPos position, Item item) {
        AABB searchArea = AABB.ofSize(Vec3.atCenterOf(position), 8.0D, 4.0D, 4.0D);
        return level.getEntitiesOfClass(ItemEntity.class, searchArea).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .map(ItemStack::copy)
                .toList();
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
}
