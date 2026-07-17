package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.emi.TrinityMultiblockEmiRecipe;
import com.fish_dan_.data_energistics.client.jei.TrinityMultiblockJeiCategory;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierOption;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructureSelection;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.PreviewMaterialStrip;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIRecipeSlotWidget;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIRecipeIngredientHandler;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIRecipeWidgetHandler;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeIngredientHandler;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeWidgetHandler;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.PatternUnit;
import com.modularmc.mdl.api.multiblock.Predicates;
import com.modularmc.mdl.api.multiblock.RepeatRange;
import dev.emi.emi.api.stack.EmiIngredient;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class MultiblockXeiDynamicIngredientGameTest {

    private static final ResourceLocation CONTROLLER_ID = Data_Energistics.id("xei_dynamic_ingredient_test");
    private static final String TIER_ID = "dynamic_frame_tier";

    private MultiblockXeiDynamicIngredientGameTest() {}

    @TestHolder(value = "multiblock_xei_dynamic_ingredients_remain_live_and_stable", side = Dist.CLIENT)
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void dynamicIngredientsRemainLiveAndStable(GameTestHelper helper) {
        MultiblockPreviewSpec spec = spec(31L);
        MutableCatalog catalog = new MutableCatalog(spec);
        StructurePreviewUiFactory previewFactory = StructurePreviewUiFactory.create((scene, selected) -> {
            throw new GameTestAssertException("Dynamic ingredient fixture must not bind a client Scene");
        });
        MultiblockXeiUiFactory uiFactory = MultiblockXeiUiFactory.create(catalog, previewFactory, false);
        MultiblockXeiRecipe jeiRecipe = MultiblockXeiRecipe.create(CONTROLLER_ID, uiFactory);
        TrinityMultiblockEmiRecipe emiRecipe = new TrinityMultiblockEmiRecipe(
                MultiblockXeiRecipe.create(CONTROLLER_ID, uiFactory));
        AtomicInteger recipeChanges = new AtomicInteger();
        AtomicInteger poolGrowths = new AtomicInteger();
        MultiblockXeiComposition jei = jeiRecipe.createComposition("dynamic_jei", (composition, change) -> {
            recipeChanges.incrementAndGet();
            if (change.widgetPoolGrew()) {
                poolGrowths.incrementAndGet();
            }
        });
        MultiblockXeiComposition emi = emiRecipe.createComposition("dynamic_emi");
        TrinityMultiblockJeiCategory.bindRecipeIngredients(jei);

        MultiblockRecipeView initial = jei.currentRecipeView();
        assertEquals(initial, emi.currentRecipeView());
        assertEquals(3, initial.inputs().size());
        assertFormalIngredients(jei, emiRecipe);
        assertSlotValues(jei, "dynamic_jei", initial);
        assertSlotValues(emi, "dynamic_emi", initial);
        ItemSlot firstJeiSlot = requireSlot(jei, inputSlotId("dynamic_jei", 0));
        ItemSlot firstEmiSlot = requireSlot(emi, inputSlotId("dynamic_emi", 0));
        ItemSlot ownerOutput = requireSlot(jei, "dynamic_jei" + MultiblockXeiComposition.OWNER_OUTPUT_SUFFIX);
        assertWidgetCount(jei, initial.inputs().size() + 1);
        assertWidgetCount(emi, initial.inputs().size() + 1);

        assertSingleChange(recipeChanges, () -> jei.selectStructure("child"));
        emi.selectStructure("child");
        MultiblockRecipeView expanded = jei.currentRecipeView();
        assertEquals(expanded, emi.currentRecipeView());
        assertEquals(4, expanded.inputs().size());
        assertEquals(1, poolGrowths.get());
        assertSame(firstJeiSlot, requireSlot(jei, inputSlotId("dynamic_jei", 0)));
        assertSame(firstEmiSlot, requireSlot(emi, inputSlotId("dynamic_emi", 0)));
        assertSame(ownerOutput, requireSlot(jei, "dynamic_jei" + MultiblockXeiComposition.OWNER_OUTPUT_SUFFIX));
        assertFormalIngredients(jei, emiRecipe);
        assertSlotValues(jei, "dynamic_jei", expanded);
        assertSlotValues(emi, "dynamic_emi", expanded);
        assertWidgetCount(jei, expanded.inputs().size() + 1);
        assertWidgetCount(emi, expanded.inputs().size() + 1);

        assertSingleChange(recipeChanges, () -> jei.selectStructure("main"));
        emi.selectStructure("main");
        MultiblockRecipeView contracted = jei.currentRecipeView();
        assertEquals(contracted, emi.currentRecipeView());
        assertEquals(3, contracted.inputs().size());
        assertEquals(1, poolGrowths.get());
        assertHiddenPoolSlot(jei, "dynamic_jei", 3);
        assertHiddenPoolSlot(emi, "dynamic_emi", 3);
        assertFormalIngredients(jei, emiRecipe);
        assertWidgetCount(jei, expanded.inputs().size() + 1);
        assertWidgetCount(emi, expanded.inputs().size() + 1);

        assertSingleChange(recipeChanges, () -> jei.selectVariant(1));
        emi.selectVariant(1);
        assertEquals(1, poolGrowths.get());
        assertFormalIngredients(jei, emiRecipe);

        assertSingleChange(recipeChanges, () -> jei.previewUi().panel().nextTier());
        emi.previewUi().panel().nextTier();
        assertFormalIngredients(jei, emiRecipe);

        int repeatUnit = jei.previewUi().session().variableRepeatUnits().getFirst();
        assertSingleChange(recipeChanges, () -> jei.previewUi().panel().nextRepeat(repeatUnit));
        emi.previewUi().panel().nextRepeat(repeatUnit);
        assertFormalIngredients(jei, emiRecipe);

        PreviewPredicateSnapshot jeiCandidate = firstSelectableCandidate(jei);
        PreviewPredicateSnapshot emiCandidate = firstSelectableCandidate(emi);
        int candidateIndex = (jeiCandidate.selectedCandidateIndex() + 1) % jeiCandidate.candidates().size();
        assertSingleChange(recipeChanges, () -> jei.selectCandidate(jeiCandidate.key(), candidateIndex));
        emi.selectCandidate(emiCandidate.key(), candidateIndex);
        assertFormalIngredients(jei, emiRecipe);

        int changesBeforeLayer = recipeChanges.get();
        MultiblockRecipeView beforeLayer = jei.currentRecipeView();
        jei.previewUi().panel().nextLayer();
        assertSame(beforeLayer, jei.currentRecipeView());
        assertEquals(changesBeforeLayer, recipeChanges.get());
        assertTrue(!PreviewViewState.initial().equals(jei.previewUi().session().viewState()));

        Object previousJeiSession = jei.previewUi().session();
        Object previousEmiSession = emi.previewUi().session();
        var retainedSelection = jei.previewUi().session().selection();
        MultiblockRecipeView retainedView = jei.currentRecipeView();
        jei.modularUI().onRemoved();
        emi.modularUI().onRemoved();
        MultiblockXeiComposition recreatedJei = jeiRecipe.createComposition("dynamic_jei_recreated");
        MultiblockXeiComposition recreatedEmi = emiRecipe.createComposition("dynamic_emi_recreated");
        TrinityMultiblockJeiCategory.bindRecipeIngredients(recreatedJei);
        assertTrue(previousJeiSession != recreatedJei.previewUi().session());
        assertTrue(previousEmiSession != recreatedEmi.previewUi().session());
        assertEquals(retainedSelection, recreatedJei.previewUi().session().selection());
        assertEquals(retainedSelection, recreatedEmi.previewUi().session().selection());
        assertEquals(retainedView, recreatedJei.currentRecipeView());
        assertEquals(retainedView, recreatedEmi.currentRecipeView());
        assertEquals(PreviewViewState.initial(), recreatedJei.previewUi().session().viewState());
        assertEquals(PreviewViewState.initial(), recreatedEmi.previewUi().session().viewState());
        assertFormalIngredients(recreatedJei, emiRecipe);
        recreatedJei.modularUI().onRemoved();
        recreatedEmi.modularUI().onRemoved();
        helper.succeed();
    }

    @TestHolder(value = "multiblock_xei_material_pool_handles_more_than_ae_capacity", side = Dist.CLIENT)
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void materialPoolHandlesMoreThanAeCapacity(GameTestHelper helper) {
        List<PreviewMaterial> materials = new ArrayList<>();
        BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .limit(82)
                .forEach(item -> materials.add(new PreviewMaterial(AEItemKey.of(item), materials.size() + 2L)));
        assertEquals(82, materials.size());

        PreviewMaterialStrip strip = new PreviewMaterialStrip("large_xei_pool", IngredientIO.INPUT);
        assertTrue(strip.setRecipeInputs(materials));
        assertEquals(82L, strip.selectRegex("_slot$", ItemSlot.class).count());
        ItemSlot first = requireSlot(strip, "large_xei_pool_material_0_slot");
        ItemSlot last = requireSlot(strip, "large_xei_pool_material_81_slot");
        assertEquals(2, first.getValue().getCount());
        assertEquals(83, last.getValue().getCount());
        assertWidgetCount(strip, 82);
        assertNoFormalIngredients(strip);

        assertFalse(strip.setRecipeInputs(materials.subList(0, 1)));
        assertSame(first, requireSlot(strip, "large_xei_pool_material_0_slot"));
        assertSame(last, requireSlot(strip, "large_xei_pool_material_81_slot"));
        assertTrue(last.getValue().isEmpty());
        assertFalse(last.isVisible());
        assertTrue(last.isActive());
        assertTrue(last.isDisplayed());
        assertNoFormalIngredients(strip);

        assertFalse(strip.setRecipeInputs(materials));
        assertSame(last, requireSlot(strip, "large_xei_pool_material_81_slot"));
        assertEquals(83, last.getValue().getCount());
        helper.succeed();
    }

    private static void assertFormalIngredients(MultiblockXeiComposition jei,
                                                TrinityMultiblockEmiRecipe emiRecipe) {
        MultiblockRecipeView view = jei.currentRecipeView();
        JEIRecipeIngredientHandler jeiIngredients = new JEIRecipeIngredientHandler();
        dispatch(jei.modularUI().ui.rootElement, JEIUIEvents.RECIPE_INGREDIENT, jeiIngredients);
        assertEquals(view.inputs().size() + 1, jeiIngredients.focuses.size());
        var jeiInputs = jeiIngredients.focuses.stream()
                .filter(focus -> focus.role() == RecipeIngredientRole.INPUT)
                .toList();
        var jeiOutputs = jeiIngredients.focuses.stream()
                .filter(focus -> focus.role() == RecipeIngredientRole.OUTPUT)
                .toList();
        assertEquals(view.inputs().size(), jeiInputs.size());
        assertEquals(1, jeiOutputs.size());
        for (int index = 0; index < view.inputs().size(); index++) {
            assertEquals(1, jeiInputs.get(index).ingredients().size());
            assertMaterialStack(
                    view.inputs().get(index),
                    requireItemStack(jeiInputs.get(index).ingredients().getFirst()));
        }
        assertEquals(1, jeiOutputs.getFirst().ingredients().size());
        assertMaterialStack(
                view.output(),
                requireItemStack(jeiOutputs.getFirst().ingredients().getFirst()));

        List<EmiIngredient> emiInputs = emiRecipe.getInputs();
        assertEquals(view.inputs().size(), emiInputs.size());
        assertEquals(0, emiRecipe.getCatalysts().size());
        assertEquals(1, emiRecipe.getOutputs().size());
        for (int index = 0; index < view.inputs().size(); index++) {
            assertEmiMaterial(view.inputs().get(index), emiInputs.get(index));
        }
        assertEmiMaterial(view.output(), emiRecipe.getOutputs().getFirst());
    }

    private static void assertSlotValues(MultiblockXeiComposition composition,
                                         String idPrefix,
                                         MultiblockRecipeView view) {
        for (int index = 0; index < view.inputs().size(); index++) {
            assertMaterialStack(view.inputs().get(index), requireSlot(composition, inputSlotId(idPrefix, index)).getValue());
        }
        assertMaterialStack(
                view.output(),
                requireSlot(composition, idPrefix + MultiblockXeiComposition.OWNER_OUTPUT_SUFFIX).getValue());
    }

    private static void assertHiddenPoolSlot(MultiblockXeiComposition composition, String idPrefix, int index) {
        ItemSlot slot = requireSlot(composition, inputSlotId(idPrefix, index));
        assertTrue(slot.getValue().isEmpty());
        assertFalse(slot.isVisible());
        assertTrue(slot.isActive());
        assertTrue(slot.isDisplayed());
    }

    private static String inputSlotId(String idPrefix, int index) {
        return idPrefix + MultiblockXeiComposition.RECIPE_INPUTS_SUFFIX + "_material_" + index + "_slot";
    }

    private static void assertWidgetCount(MultiblockXeiComposition composition, int expected) {
        assertWidgetCount(composition.modularUI().ui.rootElement, expected);
    }

    private static void assertWidgetCount(UIElement root, int expected) {
        JEIRecipeWidgetHandler jeiWidgets = new JEIRecipeWidgetHandler(Matrix4f::new);
        dispatch(root, JEIUIEvents.RECIPE_WIDGET, jeiWidgets);
        assertEquals(expected, jeiWidgets.slots.size());

        EMIRecipeWidgetHandler emiWidgets = new EMIRecipeWidgetHandler(Matrix4f::new);
        dispatch(root, EMIUIEvents.RECIPE_WIDGET, emiWidgets);
        assertEquals(expected, emiWidgets.slots.size());
        assertTrue(emiWidgets.slots.stream().allMatch(EMIRecipeSlotWidget.class::isInstance));
    }

    private static void assertNoFormalIngredients(UIElement root) {
        JEIRecipeIngredientHandler jeiIngredients = new JEIRecipeIngredientHandler();
        dispatch(root, JEIUIEvents.RECIPE_INGREDIENT, jeiIngredients);
        assertEquals(0, jeiIngredients.focuses.size());

        EMIRecipeIngredientHandler emiIngredients = new EMIRecipeIngredientHandler();
        dispatch(root, EMIUIEvents.RECIPE_INGREDIENT, emiIngredients);
        assertEquals(0, emiIngredients.inputs.size());
        assertEquals(0, emiIngredients.catalysts.size());
        assertEquals(0, emiIngredients.outputs.size());
    }

    private static void dispatch(UIElement root, String type, Object handler) {
        UIEvent event = UIEvent.create(type);
        event.target = root;
        event.customData = handler;
        UIEventDispatcher.dispatchAllChildren(event);
    }

    private static ItemSlot requireSlot(MultiblockXeiComposition composition, String id) {
        return requireSlot(composition.modularUI().ui.rootElement, id);
    }

    private static ItemSlot requireSlot(UIElement root, String id) {
        return root.selectId(id, ItemSlot.class)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Missing XEI item slot " + id));
    }

    private static ItemStack requireItemStack(ITypedIngredient<?> ingredient) {
        return ingredient.getItemStack()
                .orElseThrow(() -> new GameTestAssertException("Expected a JEI ItemStack ingredient"));
    }

    private static void assertMaterialStack(PreviewMaterial material, ItemStack actual) {
        ItemStack expected = new MultiblockXeiIngredient(IngredientIO.INPUT, material).toItemStack();
        assertTrue(ItemStack.isSameItemSameComponents(expected, actual));
        assertEquals(Math.toIntExact(material.amount()), actual.getCount());
    }

    private static void assertEmiMaterial(PreviewMaterial material, EmiIngredient ingredient) {
        if (ingredient.getEmiStacks().isEmpty()) {
            throw new GameTestAssertException("Expected a concrete EMI item ingredient");
        }
        var stack = ingredient.getEmiStacks().getFirst();
        assertEquals(material.amount(), stack.getAmount());
        assertTrue(ItemStack.isSameItemSameComponents(material.key().toStack(1), stack.getItemStack()));
    }

    private static void assertSingleChange(AtomicInteger changes, Runnable action) {
        int before = changes.get();
        action.run();
        assertEquals(before + 1, changes.get());
    }

    private static PreviewPredicateSnapshot firstSelectableCandidate(MultiblockXeiComposition composition) {
        return composition.previewUi().session().snapshot().cells().stream()
                .map(cell -> cell.predicate())
                .filter(predicate -> predicate.candidates().size() > 1)
                .filter(predicate -> predicate.candidates().stream()
                        .map(candidate -> candidate.placementKey().orElse(null))
                        .distinct()
                        .count() > 1)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Fixture requires a material-changing candidate"));
    }

    private static MultiblockPreviewSpec spec(long revision) {
        return new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Dynamic XEI ingredient fixture"),
                AEItemKey.of(Blocks.CRAFTING_TABLE),
                revision,
                List.of(
                        substructure("main", Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.COBBLESTONE),
                        substructure("child", Blocks.DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.NETHER_BRICKS)));
    }

    private static SubstructurePreviewSpec substructure(String id,
                                                        Block firstFrame,
                                                        Block secondFrame,
                                                        Block endFrame) {
        JsonMultiBlockStructureKey key = new JsonMultiBlockStructureKey(CONTROLLER_ID, id);
        List<JsonMultiBlockDefinition> variants = List.of(
                new ResolvedJsonMultiBlockDefinition(key, pattern(firstFrame, endFrame)),
                new ResolvedJsonMultiBlockDefinition(key, pattern(secondFrame, endFrame)));
        PreviewTierDomain tier = new PreviewTierDomain(
                TIER_ID,
                Component.literal("Frame tier"),
                List.of(
                        new PreviewTierOption(
                                1,
                                Component.literal("Iron"),
                                ResourceLocation.withDefaultNamespace("iron_block")),
                        new PreviewTierOption(
                                2,
                                Component.literal("Diamond"),
                                ResourceLocation.withDefaultNamespace("diamond_block"))),
                1);
        List<Integer> repeats = variants.getFirst().pattern().getLayout().units().stream()
                .map(PatternUnit::repeats)
                .map(RepeatRange::min)
                .toList();
        return new SubstructurePreviewSpec(
                variants,
                Component.literal(id),
                List.of(tier),
                new SubstructureSelection(0, repeats, Map.of(TIER_ID, 1), Map.of()));
    }

    private static BlockPattern pattern(Block frame, Block endFrame) {
        return FactoryBlockPattern.start()
                .aisle("~TC")
                .beginRepeatable()
                .aisle("ATC")
                .endRepeatable(1, 2)
                .aisle("DTC")
                .where('A', Predicates.blocks(frame))
                .where('D', Predicates.blocks(endFrame))
                .where('T', Predicates.blocks(Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK))
                .where('C', Predicates.blocks(Blocks.STONE, Blocks.GOLD_BLOCK))
                .build();
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new GameTestAssertException("Expected true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new GameTestAssertException("Expected false");
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected identical objects");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static final class MutableCatalog implements MultiblockPreviewCatalog {

        private final MultiblockPreviewCatalogSnapshot snapshot;

        private MutableCatalog(MultiblockPreviewSpec spec) {
            this.snapshot = new MultiblockPreviewCatalogSnapshot(
                    spec.definitionRevision(),
                    Map.of(spec.controllerId(), spec));
        }

        @Override
        public MultiblockPreviewCatalogSnapshot snapshot() {
            return this.snapshot;
        }
    }
}
