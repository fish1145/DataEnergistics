package com.fish_dan_.data_energistics.common.multiblock.json.matching;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockFrontFacing;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.Predicates;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class JsonMultiBlockPatternMatcherTest {

    private static final BlockPos CONTROLLER = new BlockPos(0, 0, 0);

    private JsonMultiBlockPatternMatcherTest() {}

    @TestHolder("json_multiblock_pattern_matcher_matches_mdlib_pattern")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void matchesMdlibPattern(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                pattern(),
                world(Map.of(CONTROLLER, Blocks.IRON_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertTrue(result.matched(), "Expected controller-only pattern to match");
        helper.assertValueEqual(result.positions(), List.of(CONTROLLER), "Matched positions should include the controller");
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_reports_diagnostic_when_mdlib_pattern_fails")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reportsDiagnosticWhenMdlibPatternFails(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                pattern(),
                world(Map.of(CONTROLLER, Blocks.GOLD_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertFalse(result.matched(), "Expected mismatched controller block to fail");
        PatternDiagnostic diagnostic = result.diagnostic();
        if (diagnostic == null) {
            helper.fail("Failed match should include a diagnostic");
            return;
        }
        helper.assertValueEqual(diagnostic.position(), CONTROLLER, "Diagnostic should point at the mismatched position");
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_uses_gregtech_default_directions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesGregTechDefaultDirections(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                directionalPattern(),
                world(Map.of(
                        CONTROLLER, Blocks.IRON_BLOCK.defaultBlockState(),
                        new BlockPos(-1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState(),
                        new BlockPos(1, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(),
                        new BlockPos(-1, -1, -1), Blocks.EMERALD_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertTrue(result.matched(), "Expected GregTech LEFT/UP/FRONT default directions to match: " + result.diagnostic());
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_finds_horizontal_front_when_host_facing_is_stale")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void findsHorizontalFrontWhenHostFacingIsStale(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                directionalPattern(),
                world(Map.of(
                        CONTROLLER, Blocks.IRON_BLOCK.defaultBlockState(),
                        new BlockPos(0, 0, -1), Blocks.GOLD_BLOCK.defaultBlockState(),
                        new BlockPos(0, -1, 1), Blocks.DIAMOND_BLOCK.defaultBlockState(),
                        new BlockPos(1, -1, -1), Blocks.EMERALD_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertTrue(result.matched(), "Expected matcher to recover the actual horizontal front: " + result.diagnostic());
        helper.assertValueEqual(result.frontFacing(), Direction.EAST, "Recovered front should match the placed structure");
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_exact_match_does_not_rotate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactMatchDoesNotRotate(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.matchExact(
                directionalPattern(),
                rotatedDirectionalWorld(),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertFalse(result.matched(), "Exact matcher should not rotate a child structure away from the supplied front");
        helper.assertValueEqual(result.frontFacing(), Direction.NORTH, "Failed exact match should keep the supplied front");
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_exact_match_does_not_mirror")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactMatchDoesNotMirror(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.matchExact(
                directionalPattern(),
                mirroredDirectionalWorld(),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertFalse(result.matched(), "Exact matcher should not mirror a child structure implicitly");
        helper.assertValueEqual(result.frontFacing(), Direction.NORTH, "Failed exact match should keep the supplied front");
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_exact_match_accepts_explicit_flipped")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactMatchAcceptsExplicitFlipped(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.matchExact(
                directionalPattern(),
                mirroredDirectionalWorld(),
                CONTROLLER,
                Direction.NORTH,
                true,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertTrue(result.matched(), "Explicit flipped exact match should accept a mirrored child structure");
        helper.assertTrue(result.flipped(), "Explicit flipped exact match should report flipped context");
        helper.assertValueEqual(result.frontFacing(), Direction.NORTH, "Explicit flipped exact match should keep the supplied front");
        helper.succeed();
    }

    @TestHolder("json_multiblock_pattern_matcher_exact_match_flipped_does_not_fallback_to_unflipped")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactMatchFlippedDoesNotFallbackToUnflipped(GameTestHelper helper) {
        StructureMatchResult result = JsonMultiBlockPatternMatcher.matchExact(
                directionalPattern(),
                normalDirectionalWorld(),
                CONTROLLER,
                Direction.NORTH,
                true,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);

        helper.assertFalse(result.matched(), "Explicit flipped exact match should not fall back to the unflipped structure");
        helper.assertValueEqual(result.frontFacing(), Direction.NORTH, "Failed exact match should keep the supplied front");
        helper.succeed();
    }

    @TestHolder("json_multiblock_front_facing_uses_host_front_direction")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesHostFrontDirectionAsFront(GameTestHelper helper) {
        BlockState state = Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);

        Direction frontFacing = JsonMultiBlockFrontFacing.fromPlacedHost(
                state,
                HorizontalDirectionalBlock.FACING,
                CONTROLLER,
                "test host");

        helper.assertValueEqual(
                frontFacing,
                Direction.SOUTH,
                "Structure front should match the placed host front");
        helper.succeed();
    }

    private static BlockPattern pattern() {
        return FactoryBlockPattern.start()
                .aisle("~")
                .where('~', Predicates.blocks(Blocks.IRON_BLOCK))
                .build();
    }

    private static BlockPattern directionalPattern() {
        return FactoryBlockPattern.start()
                .aisle("Y  ", " ~X")
                .aisle("  Z", "   ")
                .where('~', Predicates.blocks(Blocks.IRON_BLOCK))
                .where('X', Predicates.blocks(Blocks.GOLD_BLOCK))
                .where('Y', Predicates.blocks(Blocks.DIAMOND_BLOCK))
                .where('Z', Predicates.blocks(Blocks.EMERALD_BLOCK))
                .build();
    }

    private static StructureWorldView normalDirectionalWorld() {
        return world(Map.of(
                CONTROLLER, Blocks.IRON_BLOCK.defaultBlockState(),
                new BlockPos(-1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState(),
                new BlockPos(1, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(),
                new BlockPos(-1, -1, -1), Blocks.EMERALD_BLOCK.defaultBlockState()));
    }

    private static StructureWorldView rotatedDirectionalWorld() {
        return world(Map.of(
                CONTROLLER, Blocks.IRON_BLOCK.defaultBlockState(),
                new BlockPos(0, 0, -1), Blocks.GOLD_BLOCK.defaultBlockState(),
                new BlockPos(0, -1, 1), Blocks.DIAMOND_BLOCK.defaultBlockState(),
                new BlockPos(1, -1, -1), Blocks.EMERALD_BLOCK.defaultBlockState()));
    }

    private static StructureWorldView mirroredDirectionalWorld() {
        return world(Map.of(
                CONTROLLER, Blocks.IRON_BLOCK.defaultBlockState(),
                new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState(),
                new BlockPos(-1, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(),
                new BlockPos(1, -1, -1), Blocks.EMERALD_BLOCK.defaultBlockState()));
    }

    private static StructureWorldView world(Map<BlockPos, BlockState> states) {
        return new StructureWorldView() {

            @Override
            public boolean isLoaded(BlockPos pos) {
                return true;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public HolderLookup.Provider registryAccess() {
                return HolderLookup.Provider.create(Stream.empty());
            }
        };
    }
}
