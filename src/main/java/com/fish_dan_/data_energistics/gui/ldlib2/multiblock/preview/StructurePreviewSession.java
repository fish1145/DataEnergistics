package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.StructurePreviewSnapshot;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Owns one preview selection and keeps recipe-affecting choices separate from view-only state.
 */
public interface StructurePreviewSession {

    /**
     * Returns the revision-bound definition captured when this session was created.
     */
    MultiblockPreviewSpec spec();

    /**
     * Returns the currently active stable named structure.
     */
    String structureKey();

    /**
     * Returns the ordered structures this session permits callers to activate.
     */
    List<String> allowedStructureKeys();

    /**
     * Returns the complete immutable selection retained by this session.
     */
    PreviewSelection selection();

    /**
     * Returns the current immutable structure projection.
     */
    StructurePreviewSnapshot snapshot();

    /**
     * Returns the current view-only logical-layer state.
     */
    PreviewViewState viewState();

    /**
     * Returns the ordinary material-input/controller-output recipe projection.
     */
    MultiblockRecipeView recipeView();

    /**
     * Returns the indexes of repeatable pattern units whose legal range contains more than one value.
     */
    List<Integer> variableRepeatUnits();

    /**
     * Activates an allowed named structure while retaining every structure-local selection.
     */
    void selectStructure(String structureKey);

    /**
     * Selects one exact zero-based shape variant declared by the active structure.
     */
    void selectVariant(int variantIndex);

    /**
     * Selects one exact candidate of a predicate present in the current projected snapshot.
     */
    void selectCandidate(PreviewPredicateKey predicateKey, int candidateIndex);

    /**
     * Selects one exact value of a business tier domain declared by the active structure.
     */
    void selectTier(String domainId, int value);

    /**
     * Selects the preceding shape variant, wrapping within the declared variant domain.
     */
    void previousVariant();

    /**
     * Selects the following shape variant, wrapping within the declared variant domain.
     */
    void nextVariant();

    /**
     * Selects the preceding option of the sole tier domain, wrapping in declaration order.
     */
    void previousTier();

    /**
     * Selects the following option of the sole tier domain, wrapping in declaration order.
     */
    void nextTier();

    /**
     * Selects the preceding legal repeat count for one variable unit, wrapping at its minimum.
     */
    void previousRepeat(int unitIndex);

    /**
     * Selects the following legal repeat count for one variable unit, wrapping at its maximum.
     */
    void nextRepeat(int unitIndex);

    /**
     * Shows every projected logical layer without rebuilding materials or recipe identity.
     */
    void showAllLayers();

    /**
     * Selects the preceding logical layer, with ALL as an explicit member of the cycle.
     */
    void previousLayer();

    /**
     * Selects the following logical layer, with ALL as an explicit member of the cycle.
     */
    void nextLayer();

    /**
     * Shows one exact zero-based logical layer without changing the projected recipe.
     */
    void showLayer(int layerIndex);

    /**
     * Selects one projected position for predicate and candidate diagnostics.
     */
    void selectBlock(BlockPos position);

    /**
     * Returns the currently selected projected cell, or {@code null} before a scene selection.
     */
    @Nullable
    PreviewCellSnapshot selectedCell();

    /**
     * Returns the logical layer containing the selected cell, or {@code -1} when no cell is selected.
     */
    int selectedCellLayer();
}
