package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierOption;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewVisibleLayer;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructureSelection;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.Predicates;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class StructurePreviewSessionGameTest {

    private static final ResourceLocation INTERACTIVE_CONTROLLER_ID = Data_Energistics.id("preview_session_fixture");
    private static final String INTERACTIVE_TIER_ID = "frame";

    private StructurePreviewSessionGameTest() {}

    @TestHolder("structure_preview_sessions_isolate_recipe_and_layer_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sessionsIsolateRecipeAndLayerState(GameTestHelper helper) {
        StructurePreviewUiFactory factory = StructurePreviewUiFactory.create((scene, selected) -> {
            throw new GameTestAssertException("Server-side preview creation must not invoke a scene binder");
        });
        StructurePreviewUi firstUi = factory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "session_first",
                false);
        StructurePreviewUi secondUi = factory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "session_second",
                false);
        UIElement owner = new UIElement();
        owner.addChildren(firstUi.panel(), secondUi.panel());

        StructurePreviewSession first = firstUi.session();
        StructurePreviewSession second = secondUi.session();
        assertNotSame(firstUi.panel(), secondUi.panel());
        assertNotSame(firstUi.scene(), secondUi.scene());
        assertNotSame(first.selection(), second.selection());
        assertNotSame(first.snapshot(), second.snapshot());
        assertNotSame(first.viewState(), second.viewState());
        assertNotSame(first.recipeView(), second.recipeView());
        assertEquals(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME, first.structureKey());
        assertEquals(first.structureKey(), first.selection().activeSubstructureId());
        assertTrue(firstUi.scene().getChildren().isEmpty(),
                "Server scene shell must not contain a physical-client scene");

        PreviewSelection untouchedSelection = second.selection();
        StructurePreviewSnapshot untouchedSnapshot = second.snapshot();
        MultiblockRecipeView beforeTier = first.recipeView();
        first.nextTier();
        assertNotEquals(beforeTier.projectionFingerprint(), first.recipeView().projectionFingerprint());
        assertNotEquals(beforeTier.inputs(), first.recipeView().inputs());
        assertSame(untouchedSelection, second.selection());
        assertSame(untouchedSnapshot, second.snapshot());

        List<Integer> variableUnits = first.variableRepeatUnits();
        assertFalse(variableUnits.isEmpty(), "CPU structure fixture must expose one variable repeat unit");
        int unitIndex = variableUnits.getFirst();
        int beforeRepeat = first.selection().activeSelection().repeatCounts().get(unitIndex);
        MultiblockRecipeView beforeRepeatRecipe = first.recipeView();
        first.nextRepeat(unitIndex);
        assertEquals(
                beforeRepeat + 1,
                first.selection().activeSelection().repeatCounts().get(unitIndex).intValue());
        assertNotEquals(beforeRepeatRecipe.projectionFingerprint(), first.recipeView().projectionFingerprint());
        assertNotEquals(beforeRepeatRecipe.inputs(), first.recipeView().inputs());

        StructurePreviewSnapshot beforeLayerSnapshot = first.snapshot();
        MultiblockRecipeView beforeLayerRecipe = first.recipeView();
        PreviewViewState beforeLayerView = first.viewState();
        first.nextLayer();
        assertSame(beforeLayerSnapshot, first.snapshot());
        assertSame(beforeLayerRecipe, first.recipeView());
        assertNotEquals(beforeLayerView, first.viewState());
        assertEquals(beforeLayerRecipe.inputs(), first.recipeView().inputs());
        assertEquals(beforeLayerRecipe.projectionFingerprint(), first.recipeView().projectionFingerprint());
        int lastLayer = first.snapshot().layers().size() - 1;
        first.showLayer(lastLayer);
        assertTrue(first.viewState().visibleLayer().includes(lastLayer), "Exact logical layer must be selectable");
        first.showAllLayers();
        assertTrue(first.viewState().visibleLayer().includes(0), "ALL must include the first logical layer");
        assertTrue(first.viewState().visibleLayer().includes(lastLayer), "ALL must include the final logical layer");

        first.selectBlock(first.snapshot().cells().getFirst().relativePosition());
        assertTrue(first.selectedCell() != null, "Projected block selection must expose cell diagnostics");
        assertTrue(first.selectedCellLayer() >= 0, "Selected cell must retain its logical layer identity");
        helper.succeed();
    }

    @TestHolder("structure_preview_presentations_keep_shared_geometry_and_recipe_roles_separate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void presentationsKeepSharedGeometryAndRecipeRolesSeparate(GameTestHelper helper) {
        StructurePreviewUiFactory factory = StructurePreviewUiFactory.create((scene, selected) -> {
            throw new GameTestAssertException("Server-side presentation fixture must not bind a Scene");
        });
        StructurePreviewUi hosted = factory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "presentation_hosted",
                false);
        StructurePreviewUi xei = factory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "presentation_xei",
                false,
                StructurePreviewPresentation.XEI);

        assertEquals(196, StructurePreviewPresentation.WIDTH);
        assertEquals(128, StructurePreviewPresentation.SCENE_HEIGHT);
        assertEquals(184, StructurePreviewPresentation.HOSTED.height());
        assertEquals(158, StructurePreviewPresentation.XEI.height());
        assertSame(StructurePreviewPresentation.HOSTED, hosted.panel().presentation());
        assertSame(StructurePreviewPresentation.XEI, xei.panel().presentation());
        PreviewMaterialStrip hostedMaterials = (PreviewMaterialStrip) requireId(
                hosted.panel(), "presentation_hosted" + StructurePreviewPanel.MATERIALS_SUFFIX);
        assertEquals(1, countId(hosted.panel(), "presentation_hosted" + StructurePreviewPanel.MATERIALS_SUFFIX));
        assertSame(IngredientIO.NONE, hostedMaterials.recipeRole());
        assertEquals(0, countId(xei.panel(), "presentation_xei" + StructurePreviewPanel.MATERIALS_SUFFIX));
        assertEquals(1, countId(xei.panel(), "presentation_xei" + StructurePreviewPanel.SCENE_SUFFIX));
        assertEquals(1, countId(xei.panel(), "presentation_xei" + StructurePreviewPanel.SELECTED_BLOCK_SUFFIX));
        assertEquals(1, countId(xei.panel(), "presentation_xei" + StructurePreviewPanel.LAYER_ALL_SUFFIX));

        UIElement controls = requireId(xei.panel(), "presentation_xei_controls");
        assertEquals("presentation_xei_variant", controls.getChildren().get(0).getId());
        assertEquals("presentation_xei_tier", controls.getChildren().get(1).getId());
        assertEquals("presentation_xei_repeat_controls", controls.getChildren().get(2).getId());
        assertEquals(1, countId(xei.panel(),
                "presentation_xei" + StructurePreviewPanel.VARIANT_PREVIOUS_SUFFIX));
        assertEquals(1, countId(xei.panel(),
                "presentation_xei" + StructurePreviewPanel.TIER_NEXT_SUFFIX));
        int repeatUnit = xei.session().variableRepeatUnits().getFirst();
        assertEquals(1, countId(xei.panel(),
                "presentation_xei" + StructurePreviewPanel.REPEAT_SUFFIX + repeatUnit + "_previous"));
        assertEquals(1, countId(xei.panel(),
                "presentation_xei" + StructurePreviewPanel.REPEAT_SUFFIX + repeatUnit + "_next"));

        AtomicInteger selectionChanges = new AtomicInteger();
        xei.panel().setSelectionChangeListener(selection -> selectionChanges.incrementAndGet());
        PreviewSelection beforeLayerSelection = xei.session().selection();
        MultiblockRecipeView beforeLayerRecipe = xei.session().recipeView();
        xei.panel().nextLayer();
        assertSame(beforeLayerSelection, xei.session().selection());
        assertSame(beforeLayerRecipe, xei.session().recipeView());
        assertEquals(0, selectionChanges.get());
        xei.panel().nextTier();
        assertNotSame(beforeLayerSelection, xei.session().selection());
        assertNotSame(beforeLayerRecipe, xei.session().recipeView());
        assertEquals(1, selectionChanges.get());
        helper.succeed();
    }

    @TestHolder("structure_preview_direct_controls_retain_layers_and_close_selector_popup")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void directControlsRetainLayersAndCloseSelectorPopup(GameTestHelper helper) {
        MultiblockPreviewSpec spec = interactiveSpec();
        StructurePreviewUiFactory factory = StructurePreviewUiFactory.create((scene, selected) -> {
            throw new GameTestAssertException("Logical-server interactive fixture must not bind a Scene");
        });
        StructurePreviewUi preview = factory.create(
                spec,
                PreviewSelection.initial(spec),
                List.of("main", "child"),
                "interactive_preview",
                false);
        StructurePreviewPanel panel = preview.panel();
        StructurePreviewSession session = preview.session();
        StructurePreviewSceneElement scene = preview.scene();
        UIElement owner = new UIElement();
        owner.addChild(panel);
        ModularUI modularUI = ModularUI.of(UI.of(owner));
        modularUI.setMenu(null);

        ScrollerView repeatControls = (ScrollerView) requireId(panel, "interactive_preview_repeat_controls");
        assertEquals(2, session.variableRepeatUnits().size());
        assertSame(
                ScrollerMode.HORIZONTAL,
                repeatControls.getScrollerViewStyle().getInline(PropertyRegistry.SCROLLER_VIEW_MODE));
        assertSame(
                ScrollDisplay.AUTO,
                repeatControls.getScrollerViewStyle().getInline(PropertyRegistry.SCROLLER_HORIZONTAL_DISPLAY));
        assertSame(
                ScrollDisplay.NEVER,
                repeatControls.getScrollerViewStyle().getInline(PropertyRegistry.SCROLLER_VERTICAL_DISPLAY));
        assertEquals(28, StructurePreviewPresentation.CONTROL_RAIL_HEIGHT);
        assertEquals(23, StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT);
        assertEquals(5, StructurePreviewPresentation.CONTROL_RAIL_HEIGHT -
                StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT);
        assertEquals(session.variableRepeatUnits().size(), repeatControls.viewContainer.getChildren().size());
        for (UIElement child : repeatControls.viewContainer.getChildren()) {
            assertTrue(child instanceof PreviewStepper, "Repeat viewport may only contain preview steppers");
        }

        PreviewLayerSelector layerSelector = (PreviewLayerSelector) requireId(panel, "interactive_preview_layer");
        assertLayerCandidates(session, layerSelector);
        AtomicInteger selectionChanges = new AtomicInteger();
        panel.setSelectionChangeListener(selection -> selectionChanges.incrementAndGet());

        int retainedLayer = 2;
        panel.showLayer(retainedLayer);
        assertVisibleLayer(session, retainedLayer);
        assertEquals(0, selectionChanges.get());

        panel.nextTier();
        assertVisibleLayer(session, retainedLayer);
        assertEquals(1, selectionChanges.get());

        PreviewPredicateSnapshot selectable = firstSelectablePredicate(session);
        int nextCandidate = (selectable.selectedCandidateIndex() + 1) % selectable.candidates().size();
        panel.selectCandidate(selectable.key(), nextCandidate);
        assertVisibleLayer(session, retainedLayer);
        assertEquals(2, selectionChanges.get());

        List<Integer> repeatUnits = session.variableRepeatUnits();
        panel.nextRepeat(repeatUnits.getFirst());
        assertVisibleLayer(session, retainedLayer);
        assertLayerCandidates(session, layerSelector);
        assertEquals(3, selectionChanges.get());

        Selector<?> selector = (Selector<?>) requireId(
                panel, "interactive_preview" + StructurePreviewPanel.LAYER_SELECTOR_SUFFIX);
        owner.addChild(selector.dialog);
        assertTrue(layerSelector.isPopupOpen(), "Layer selector popup must attach to the ModularUI root");
        UIElement layerPopup = owner.getChildren().stream()
                .filter(child -> child.hasClass(HostUiExtension.TRANSIENT_POPUP_CLASS))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Layer selector popup lacks the host transient marker"));
        assertSame(selector.dialog, layerPopup);
        assertSame(owner, layerPopup.getParent());
        Integer popupZIndex = layerPopup.getStyle().getImportant(PropertyRegistry.Z_INDEX);
        if (popupZIndex == null) {
            throw new GameTestAssertException("Layer selector popup lacks an important z-index");
        }
        assertEquals(HostUiExtension.TRANSIENT_POPUP_Z, popupZIndex.intValue());
        panel.nextRepeat(repeatUnits.get(1));
        assertVisibleLayer(session, retainedLayer);
        assertLayerCandidates(session, layerSelector);
        assertTrue(layerSelector.isPopupOpen(), "Snapshot refresh must preserve the open selector popup");
        assertEquals(4, selectionChanges.get());

        panel.selectStructure("child");
        assertVisibleLayer(session, retainedLayer);
        assertLayerCandidates(session, layerSelector);
        panel.selectStructure("main");
        assertVisibleLayer(session, retainedLayer);
        assertEquals(6, selectionChanges.get());

        panel.selectVariant(1);
        assertVisibleLayer(session, retainedLayer);
        assertLayerCandidates(session, layerSelector);
        panel.selectVariant(0);
        assertVisibleLayer(session, retainedLayer);
        assertEquals(8, selectionChanges.get());

        int finalLongLayer = session.snapshot().layers().size() - 1;
        panel.showLayer(finalLongLayer);
        assertVisibleLayer(session, finalLongLayer);
        assertEquals(8, selectionChanges.get());
        panel.selectVariant(1);
        assertEquals(PreviewVisibleLayer.all(), session.viewState().visibleLayer());
        assertLayerCandidates(session, layerSelector);
        assertEquals(9, selectionChanges.get());

        PreviewSelection beforeInvalidSelection = session.selection();
        PreviewPredicateSnapshot currentPredicate = firstSelectablePredicate(session);
        assertIllegalArgument(() -> panel.selectVariant(-1));
        assertIllegalArgument(() -> panel.selectVariant(2));
        assertIllegalArgument(() -> panel.selectCandidate(currentPredicate.key(), -1));
        assertIllegalArgument(() -> panel.selectCandidate(
                currentPredicate.key(), currentPredicate.candidates().size()));
        assertIllegalArgument(() -> panel.selectCandidate(new PreviewPredicateKey(99, 0, 0), 0));
        assertSame(beforeInvalidSelection, session.selection());
        assertEquals(9, selectionChanges.get());

        assertSame(panel, preview.panel());
        assertSame(session, preview.session());
        assertSame(scene, preview.scene());
        owner.removeChild(panel);
        assertFalse(layerSelector.isPopupOpen(), "Removing the panel must close its root-mounted selector popup");
        assertFalse(owner.hasChild(layerPopup), "Removing the panel must detach the selector popup from its root");
        modularUI.onRemoved();
        helper.succeed();
    }

    @TestHolder("preview_material_strip_preserves_long_visual_amounts_and_rejects_xei_overflow")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void materialStripPreservesLongAmountsAndRejectsXeiOverflow(GameTestHelper helper) {
        AEItemKey stone = AEItemKey.of(Blocks.STONE);
        if (stone == null) {
            throw new GameTestAssertException("Stone must expose an AE item key");
        }
        PreviewMaterial largestXeiAmount = new PreviewMaterial(stone, Integer.MAX_VALUE);
        PreviewMaterial oversized = new PreviewMaterial(stone, (long) Integer.MAX_VALUE + 1L);
        PreviewMaterialStrip strip = new PreviewMaterialStrip("long_materials");

        strip.setMaterials(List.of(oversized));

        assertEquals(1, countId(strip, "long_materials_material_0"));
        assertSame(IngredientIO.NONE, strip.recipeRole());
        assertEquals(Integer.MAX_VALUE, PreviewMaterialStrip.xeiAmount(largestXeiAmount));
        assertIllegalArgument(() -> PreviewMaterialStrip.xeiAmount(oversized));
        helper.succeed();
    }

    @TestHolder("structure_preview_factory_rejects_missing_sole_tier_domain")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void factoryRejectsMissingSoleTierDomain(GameTestHelper helper) {
        MultiblockPreviewSpec catalog = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS
                .snapshot()
                .require(ModVerticalMultiBlocks.trinityDataCoreId());
        SubstructurePreviewSpec source = catalog.substructure(
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME);
        SubstructureSelection defaults = new SubstructureSelection(
                source.defaults().variantIndex(),
                source.defaults().repeatCounts(),
                Map.of(),
                source.defaults().candidateSelections());
        SubstructurePreviewSpec withoutTier = new SubstructurePreviewSpec(
                source.variants(),
                source.title(),
                List.of(),
                defaults);
        MultiblockPreviewSpec invalid = new MultiblockPreviewSpec(
                catalog.controllerId(),
                catalog.title(),
                catalog.ownerOutput(),
                catalog.definitionRevision(),
                List.of(withoutTier));
        StructurePreviewUiFactory factory = StructurePreviewUiFactory.create((scene, selected) -> {
            throw new GameTestAssertException("Invalid server-side preview must fail before binding a Scene");
        });

        assertIllegalArgument(() -> factory.create(
                invalid,
                PreviewSelection.initial(invalid),
                List.of(withoutTier.id()),
                "missing_tier_domain",
                false));
        helper.succeed();
    }

    @TestHolder("structure_preview_panel_aggregates_binding_and_tree_release_failures")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void panelAggregatesBindingAndTreeReleaseFailures(GameTestHelper helper) {
        RuntimeException bindingFailure = new RuntimeException("binding release");
        RuntimeException treeFailure = new RuntimeException("tree release");
        AtomicInteger releaseAttempts = new AtomicInteger();
        StructurePreviewUiFactory factory = StructurePreviewUiFactory.create((scene, selected) -> new StructurePreviewSceneBinding() {

            @Override
            public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {}

            @Override
            public void release() {
                releaseAttempts.incrementAndGet();
                throw bindingFailure;
            }
        });
        StructurePreviewUi preview = factory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "session_release_failure",
                true);
        UIElement owner = new UIElement();
        owner.addChild(preview.panel());
        preview.panel().addEventListener(UIEvents.REMOVED, event -> {
            throw treeFailure;
        });

        RuntimeException thrown = captureRuntimeFailure(() -> owner.removeChild(preview.panel()));

        assertSame(bindingFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(treeFailure, thrown.getSuppressed()[0]);
        assertEquals(1, releaseAttempts.get());
        helper.succeed();
    }

    @TestHolder("structure_preview_factory_preserves_same_bind_and_release_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void factoryPreservesSameBindAndReleaseFailure(GameTestHelper helper) {
        RuntimeException sharedFailure = new RuntimeException("shared bind and release failure");
        AtomicInteger releaseAttempts = new AtomicInteger();
        StructurePreviewUiFactory factory = StructurePreviewUiFactory.create((scene, selected) -> new StructurePreviewSceneBinding() {

            @Override
            public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
                throw sharedFailure;
            }

            @Override
            public void release() {
                releaseAttempts.incrementAndGet();
                throw sharedFailure;
            }
        });

        RuntimeException thrown = captureRuntimeFailure(() -> factory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "session_same_release_failure",
                true));

        assertSame(sharedFailure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(1, releaseAttempts.get());
        helper.succeed();
    }

    private static MultiblockPreviewSpec interactiveSpec() {
        AEItemKey owner = AEItemKey.of(Blocks.CRAFTING_TABLE);
        if (owner == null) {
            throw new GameTestAssertException("Crafting table must expose an AE item key");
        }
        return new MultiblockPreviewSpec(
                INTERACTIVE_CONTROLLER_ID,
                Component.literal("Interactive preview fixture"),
                owner,
                1L,
                List.of(
                        interactiveSubstructure(
                                "main", Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.OBSIDIAN, true),
                        interactiveSubstructure(
                                "child", Blocks.DEEPSLATE, Blocks.POLISHED_DEEPSLATE,
                                Blocks.NETHER_BRICKS, false)));
    }

    private static SubstructurePreviewSpec interactiveSubstructure(String id,
                                                                   Block firstFrame,
                                                                   Block secondFrame,
                                                                   Block endFrame,
                                                                   boolean includeShortVariant) {
        JsonMultiBlockStructureKey key = new JsonMultiBlockStructureKey(INTERACTIVE_CONTROLLER_ID, id);
        List<JsonMultiBlockDefinition> variants = includeShortVariant ?
                List.of(
                        new ResolvedJsonMultiBlockDefinition(
                                key, longInteractivePattern(firstFrame, secondFrame, endFrame)),
                        new ResolvedJsonMultiBlockDefinition(
                                key, shortInteractivePattern(firstFrame, secondFrame))) :
                List.of(new ResolvedJsonMultiBlockDefinition(
                        key, longInteractivePattern(firstFrame, secondFrame, endFrame)));
        PreviewTierDomain tier = new PreviewTierDomain(
                INTERACTIVE_TIER_ID,
                Component.literal("Frame"),
                List.of(
                        new PreviewTierOption(
                                1, Component.literal("Iron"), ResourceLocation.withDefaultNamespace("iron_block")),
                        new PreviewTierOption(
                                2, Component.literal("Diamond"),
                                ResourceLocation.withDefaultNamespace("diamond_block"))),
                1);
        return new SubstructurePreviewSpec(
                variants,
                Component.literal(id),
                List.of(tier),
                new SubstructureSelection(
                        0,
                        variants.getFirst().pattern().getLayout().units().stream()
                                .map(unit -> unit.repeats().min())
                                .toList(),
                        Map.of(INTERACTIVE_TIER_ID, 1),
                        Map.of()));
    }

    private static BlockPattern longInteractivePattern(Block firstFrame, Block secondFrame, Block endFrame) {
        return FactoryBlockPattern.start()
                .aisle("~TC")
                .beginRepeatable()
                .aisle("ATC")
                .endRepeatable(1, 2)
                .beginRepeatable()
                .aisle("BTC")
                .endRepeatable(1, 2)
                .aisle("DTC")
                .where('A', Predicates.blocks(firstFrame))
                .where('B', Predicates.blocks(secondFrame))
                .where('D', Predicates.blocks(endFrame))
                .where('T', Predicates.blocks(Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK))
                .where('C', Predicates.blocks(Blocks.STONE, Blocks.GOLD_BLOCK))
                .build();
    }

    private static BlockPattern shortInteractivePattern(Block firstFrame, Block secondFrame) {
        return FactoryBlockPattern.start()
                .aisle("~TC")
                .beginRepeatable()
                .aisle("ATC")
                .endRepeatable(1, 2)
                .beginRepeatable()
                .aisle("BTC")
                .endRepeatable(1, 2)
                .where('A', Predicates.blocks(firstFrame))
                .where('B', Predicates.blocks(secondFrame))
                .where('T', Predicates.blocks(Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK))
                .where('C', Predicates.blocks(Blocks.STONE, Blocks.GOLD_BLOCK))
                .build();
    }

    private static PreviewPredicateSnapshot firstSelectablePredicate(StructurePreviewSession session) {
        return session.snapshot().cells().stream()
                .map(cell -> cell.predicate())
                .filter(predicate -> predicate.candidates().size() > 1)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(
                        "Interactive fixture requires a predicate with at least two candidates"));
    }

    private static void assertVisibleLayer(StructurePreviewSession session, int layerIndex) {
        assertEquals(PreviewVisibleLayer.logicalLayer(layerIndex), session.viewState().visibleLayer());
    }

    private static void assertLayerCandidates(StructurePreviewSession session,
                                              PreviewLayerSelector layerSelector) {
        assertEquals(session.snapshot().layers().size() + 1, layerSelector.candidates().size());
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected identical objects");
        }
    }

    private static void assertNotSame(Object first, Object second) {
        if (first == second) {
            throw new GameTestAssertException("Expected distinct object identities");
        }
    }

    private static void assertNotEquals(Object first, Object second) {
        if (first.equals(second)) {
            throw new GameTestAssertException("Expected distinct values");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static int countId(UIElement root, String id) {
        return Math.toIntExact(root.selectId(id).count());
    }

    private static UIElement requireId(UIElement root, String id) {
        return root.selectId(id)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing UI element id: " + id));
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || expected.getMessage().isBlank()) {
                throw new GameTestAssertException("IllegalArgumentException must explain the invalid value");
            }
            return;
        }
        throw new GameTestAssertException("Expected IllegalArgumentException");
    }

    private static RuntimeException captureRuntimeFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new GameTestAssertException("Expected RuntimeException");
    }
}
