package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellRole;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewLayerSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjectionImpl;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructureSelection;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.PatternBounds;
import com.modularmc.mdl.api.multiblock.PatternCellSource;
import com.modularmc.mdl.api.multiblock.PatternLayerSource;
import com.modularmc.mdl.api.multiblock.Predicates;

import java.util.List;
import java.util.Map;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class StructurePreviewSceneInfrastructureGameTest {

    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.parse("data_energistics:scene_infrastructure_test");
    private static final String STRUCTURE_ID = "main";

    private StructurePreviewSceneInfrastructureGameTest() {}

    @TestHolder("structure_preview_render_state_maps_candidates_by_logical_layer")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void renderStateMapsConcreteCandidatesAndLogicalLayers(GameTestHelper helper) {
        StructurePreviewSnapshot snapshot = projectedSnapshot();
        StructurePreviewRenderState all = StructurePreviewRenderState.from(snapshot, PreviewViewState.initial());
        StructurePreviewRenderState secondLayer = StructurePreviewRenderState.from(
                snapshot,
                PreviewViewState.initial().showLogicalLayer(1));

        helper.assertTrue(snapshot.cells().stream().anyMatch(cell -> cell.predicate().role() == PreviewCellRole.AIR),
                "The fixture must exercise explicit air candidates");
        helper.assertTrue(
                snapshot.cells().stream().anyMatch(cell -> cell.predicate().role() == PreviewCellRole.WILDCARD),
                "The fixture must exercise wildcard cells");
        helper.assertValueEqual(all.blockStates().size(), 3,
                "Only controller, stone, and gold concrete states may enter the dummy world");
        helper.assertFalse(all.blockStates().values().stream().anyMatch(BlockState::isAir),
                "Air and wildcard cells must not enter the dummy world");

        BlockPos stone = positionOf(all, Blocks.STONE);
        BlockPos gold = positionOf(all, Blocks.GOLD_BLOCK);
        helper.assertValueEqual(stone.getY(), gold.getY(),
                "The fixture's logical layers must share a Y coordinate");
        helper.assertFalse(stone.equals(gold), "Concrete states in separate logical layers need distinct positions");
        helper.assertValueEqual(secondLayer.renderedCore(), List.of(gold),
                "Logical layer selection must follow snapshot layer identity instead of the Y axis");
        helper.assertValueEqual(Set.copyOf(all.renderedCore()), all.blockStates().keySet(),
                "The all-layers core must include every and only concrete render state");
        assertUnsupported(() -> all.blockStates().put(BlockPos.ZERO, Blocks.DIRT.defaultBlockState()));
        helper.succeed();
    }

    @TestHolder("structure_preview_render_state_rejects_duplicate_positions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void renderStateRejectsDuplicatePositions(GameTestHelper helper) {
        StructurePreviewSnapshot projected = projectedSnapshot();
        BlockPos duplicatePosition = new BlockPos(4, 7, -2);
        PreviewCellSnapshot first = cell(
                duplicatePosition,
                new PatternCellSource(0, 0, 0, 0, 0),
                Blocks.STONE);
        PreviewCellSnapshot second = cell(
                duplicatePosition,
                new PatternCellSource(0, 0, 0, 1, 0),
                Blocks.GOLD_BLOCK);
        PreviewLayerSnapshot layer = new PreviewLayerSnapshot(
                0,
                new PatternLayerSource(0, 0, 0, 0),
                List.of(first, second));
        StructurePreviewSnapshot duplicate = new StructurePreviewSnapshot(
                projected.selection(),
                projected.definitionKey(),
                List.of(layer),
                List.of(first, second),
                new PatternBounds(duplicatePosition, duplicatePosition),
                List.of());

        assertIllegalArgument(() -> StructurePreviewRenderState.from(duplicate, PreviewViewState.initial()));
        helper.succeed();
    }

    @TestHolder("structure_preview_scene_shell_closes_without_client_classes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sceneShellClosesWithoutClientClasses(GameTestHelper helper) {
        UIElement root = new UIElement();
        StructurePreviewSceneElement scene = new StructurePreviewSceneElement();
        helper.assertTrue(scene.getChildren().isEmpty(),
                "Fresh common scene shell must not contain physical-client state");
        UIElement clientScene = new UIElement();
        clientScene.markAsInternal();
        scene.attachClientScene(clientScene);
        root.addChild(scene);
        helper.assertTrue(scene.hasChild(clientScene), "Fixture must model one physical-client scene child");

        helper.assertTrue(scene.removeChild(clientScene),
                "Guarded host cleanup must remove the client scene before its common shell");
        UIElement replacement = new UIElement();
        replacement.markAsInternal();
        scene.attachClientScene(replacement);
        helper.assertTrue(scene.removeChild(replacement),
                "Post-order removal must clear shell ownership for the released client scene");
        helper.assertTrue(root.removeChild(scene), "Dedicated-server scene shell must close normally");
        helper.assertFalse(scene.hasParent(), "Closed dedicated-server scene shell must leave its common tree");
        helper.succeed();
    }

    private static StructurePreviewSnapshot projectedSnapshot() {
        var pattern = FactoryBlockPattern.start()
                .aisle("~AE")
                .aisle("BWE")
                .where('A', Predicates.blocks(Blocks.STONE))
                .where('B', Predicates.blocks(Blocks.GOLD_BLOCK))
                .where('W', Predicates.any())
                .where('E', Predicates.air())
                .build();
        ResolvedJsonMultiBlockDefinition definition = new ResolvedJsonMultiBlockDefinition(
                new JsonMultiBlockStructureKey(CONTROLLER_ID, STRUCTURE_ID),
                pattern);
        SubstructurePreviewSpec substructure = new SubstructurePreviewSpec(
                definition,
                Component.literal("Scene infrastructure"),
                List.of(),
                new SubstructureSelection(List.of(1, 1), Map.of(), Map.of()));
        MultiblockPreviewSpec spec = new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Scene infrastructure"),
                itemKey(Blocks.DIAMOND_BLOCK),
                1L,
                List.of(substructure));
        return new StructurePreviewProjectionImpl().project(spec, PreviewSelection.initial(spec));
    }

    private static PreviewCellSnapshot cell(BlockPos position, PatternCellSource source, Block block) {
        PreviewPredicateKey key = new PreviewPredicateKey(source.sourceLayer(), source.y(), source.x());
        PreviewCandidate candidate = PreviewCandidate.concrete(block.defaultBlockState(), itemKey(block));
        return new PreviewCellSnapshot(
                position,
                source,
                new PreviewPredicateSnapshot(key, PreviewCellRole.MATERIAL, List.of(candidate), 0));
    }

    private static BlockPos positionOf(StructurePreviewRenderState state, Block block) {
        return state.blockStates().entrySet().stream()
                .filter(entry -> entry.getValue().is(block))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing rendered block " + block));
    }

    private static AEItemKey itemKey(Block block) {
        AEItemKey key = AEItemKey.of(block);
        if (key == null) {
            throw new GameTestAssertException("Test block has no item key: " + block);
        }
        return key;
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalArgumentException");
    }

    private static void assertUnsupported(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException exception) {
            return;
        }
        throw new GameTestAssertException("Expected UnsupportedOperationException");
    }
}
