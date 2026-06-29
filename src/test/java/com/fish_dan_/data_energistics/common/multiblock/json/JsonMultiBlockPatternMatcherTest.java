package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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

    private static BlockPattern pattern() {
        return FactoryBlockPattern.start()
                .aisle("~")
                .where('~', Predicates.blocks(Blocks.IRON_BLOCK))
                .build();
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
