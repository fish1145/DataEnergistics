package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class StructurePreviewSessionGameTest {

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
        assertTrue(firstUi.scene().getDummyWorld() == null, "Server scene shell must not own a dummy world");

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
}
