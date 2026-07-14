package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewLayerSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierOption;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewVisibleLayer;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjection;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;

import net.minecraft.core.BlockPos;

import com.modularmc.mdl.api.multiblock.RepeatRange;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Default mutable session shell whose published model values remain immutable common-layer snapshots.
 */
final class StructurePreviewSessionImpl implements StructurePreviewSession {

    private final MultiblockPreviewSpec spec;
    private final List<String> allowedStructureKeys;
    private final StructurePreviewProjection projection;
    private PreviewSelection selection;
    private StructurePreviewSnapshot snapshot;
    private PreviewViewState viewState;
    private MultiblockRecipeView recipeView;
    @Nullable
    private PreviewCellSnapshot selectedCell;
    private int selectedCellLayer = -1;

    StructurePreviewSessionImpl(MultiblockPreviewSpec spec,
                                PreviewSelection initialSelection,
                                List<String> allowedStructureKeys,
                                StructurePreviewProjection projection) {
        if (spec == null || initialSelection == null || allowedStructureKeys == null || projection == null) {
            throw new IllegalArgumentException("Structure preview session arguments cannot be null");
        }
        initialSelection.validateAgainst(spec);
        this.allowedStructureKeys = validateAllowedStructures(spec, initialSelection, allowedStructureKeys);
        this.spec = spec;
        this.projection = projection;
        this.selection = initialSelection;
        this.viewState = PreviewViewState.initial();
        this.snapshot = projection.project(spec, this.selection);
        this.recipeView = MultiblockRecipeView.from(spec, this.snapshot);
    }

    @Override
    public MultiblockPreviewSpec spec() {
        return this.spec;
    }

    @Override
    public String structureKey() {
        return this.selection.activeSubstructureId();
    }

    @Override
    public List<String> allowedStructureKeys() {
        return this.allowedStructureKeys;
    }

    @Override
    public PreviewSelection selection() {
        return this.selection;
    }

    @Override
    public StructurePreviewSnapshot snapshot() {
        return this.snapshot;
    }

    @Override
    public PreviewViewState viewState() {
        return this.viewState;
    }

    @Override
    public MultiblockRecipeView recipeView() {
        return this.recipeView;
    }

    @Override
    public List<Integer> variableRepeatUnits() {
        List<RepeatRange> ranges = activeSubstructure().repeatRanges(this.selection.activeSelection().variantIndex());
        return IntStream.range(0, ranges.size())
                .filter(index -> ranges.get(index).min() != ranges.get(index).max())
                .boxed()
                .toList();
    }

    @Override
    public void selectStructure(String structureKey) {
        if (!this.allowedStructureKeys.contains(structureKey)) {
            throw new IllegalArgumentException("Structure preview session cannot select structure: " + structureKey);
        }
        replaceSelection(this.selection.select(structureKey));
    }

    @Override
    public void previousVariant() {
        int count = activeSubstructure().variantCount();
        int current = this.selection.activeSelection().variantIndex();
        replaceSelection(this.selection.withVariantIndex((current + count - 1) % count));
    }

    @Override
    public void nextVariant() {
        int count = activeSubstructure().variantCount();
        int current = this.selection.activeSelection().variantIndex();
        replaceSelection(this.selection.withVariantIndex((current + 1) % count));
    }

    @Override
    public void previousTier() {
        changeTier(-1);
    }

    @Override
    public void nextTier() {
        changeTier(1);
    }

    @Override
    public void previousRepeat(int unitIndex) {
        changeRepeat(unitIndex, -1);
    }

    @Override
    public void nextRepeat(int unitIndex) {
        changeRepeat(unitIndex, 1);
    }

    @Override
    public void showAllLayers() {
        replaceViewState(this.viewState.showAllLayers());
    }

    @Override
    public void previousLayer() {
        int layerCount = this.snapshot.layers().size();
        PreviewVisibleLayer visibleLayer = this.viewState.visibleLayer();
        if (visibleLayer instanceof PreviewVisibleLayer.All) {
            showLayer(layerCount - 1);
            return;
        }
        int current = ((PreviewVisibleLayer.LogicalLayer) visibleLayer).layerIndex();
        if (current == 0) {
            showAllLayers();
        } else {
            showLayer(current - 1);
        }
    }

    @Override
    public void nextLayer() {
        PreviewVisibleLayer visibleLayer = this.viewState.visibleLayer();
        if (visibleLayer instanceof PreviewVisibleLayer.All) {
            showLayer(0);
            return;
        }
        int current = ((PreviewVisibleLayer.LogicalLayer) visibleLayer).layerIndex();
        if (current + 1 >= this.snapshot.layers().size()) {
            showAllLayers();
        } else {
            showLayer(current + 1);
        }
    }

    @Override
    public void showLayer(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= this.snapshot.layers().size()) {
            throw new IllegalArgumentException("Structure preview logical layer " + layerIndex + " is outside 0.." +
                    (this.snapshot.layers().size() - 1));
        }
        replaceViewState(this.viewState.showLogicalLayer(layerIndex));
    }

    @Override
    public void selectBlock(BlockPos position) {
        if (position == null) {
            throw new IllegalArgumentException("Structure preview selected position cannot be null");
        }
        for (PreviewLayerSnapshot layer : this.snapshot.layers()) {
            for (PreviewCellSnapshot cell : layer.cells()) {
                if (cell.relativePosition().equals(position)) {
                    this.selectedCell = cell;
                    this.selectedCellLayer = layer.index();
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Selected position is outside the projected structure: " + position);
    }

    @Override
    public @Nullable PreviewCellSnapshot selectedCell() {
        return this.selectedCell;
    }

    @Override
    public int selectedCellLayer() {
        return this.selectedCellLayer;
    }

    private void changeTier(int direction) {
        PreviewTierDomain domain = activeSubstructure().tierDomains().getFirst();
        List<PreviewTierOption> options = domain.options();
        int currentValue = this.selection.activeSelection().tierSelections().get(domain.id());
        int currentIndex = -1;
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).value() == currentValue) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            throw new IllegalStateException("Current preview tier is absent from domain " + domain.id());
        }
        int nextIndex = Math.floorMod(currentIndex + direction, options.size());
        replaceSelection(this.selection.withTier(domain.id(), options.get(nextIndex).value()));
    }

    private void changeRepeat(int unitIndex, int direction) {
        List<RepeatRange> ranges = activeSubstructure().repeatRanges(this.selection.activeSelection().variantIndex());
        if (unitIndex < 0 || unitIndex >= ranges.size()) {
            throw new IllegalArgumentException("Unknown structure preview repeat unit: " + unitIndex);
        }
        RepeatRange range = ranges.get(unitIndex);
        if (range.min() == range.max()) {
            throw new IllegalArgumentException("Structure preview repeat unit " + unitIndex + " is fixed");
        }
        int current = this.selection.activeSelection().repeatCounts().get(unitIndex);
        int next = current + direction;
        if (next < range.min()) {
            next = range.max();
        } else if (next > range.max()) {
            next = range.min();
        }
        replaceSelection(this.selection.withRepeat(unitIndex, next));
    }

    private void replaceSelection(PreviewSelection updated) {
        if (updated.equals(this.selection)) {
            return;
        }
        StructurePreviewSnapshot updatedSnapshot = this.projection.project(this.spec, updated);
        MultiblockRecipeView updatedRecipe = MultiblockRecipeView.from(this.spec, updatedSnapshot);
        this.selection = updated;
        this.snapshot = updatedSnapshot;
        this.recipeView = updatedRecipe;
        this.viewState = PreviewViewState.initial();
        this.selectedCell = null;
        this.selectedCellLayer = -1;
    }

    private void replaceViewState(PreviewViewState updated) {
        this.snapshot.visibleLayers(updated);
        this.viewState = updated;
        this.selectedCell = null;
        this.selectedCellLayer = -1;
    }

    private SubstructurePreviewSpec activeSubstructure() {
        return this.spec.substructure(structureKey());
    }

    private static List<String> validateAllowedStructures(MultiblockPreviewSpec spec,
                                                          PreviewSelection initialSelection,
                                                          List<String> allowedStructureKeys) {
        if (allowedStructureKeys.isEmpty()) {
            throw new IllegalArgumentException("Structure preview session requires at least one allowed structure");
        }
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        for (String structureKey : allowedStructureKeys) {
            if (structureKey == null || structureKey.isBlank() || !uniqueKeys.add(structureKey)) {
                throw new IllegalArgumentException("Structure preview allowed keys cannot be null, blank, or duplicate");
            }
            SubstructurePreviewSpec substructure = spec.substructure(structureKey);
            if (substructure.tierDomains().size() != 1) {
                throw new IllegalArgumentException("Hosted structure preview requires exactly one tier domain for " +
                        structureKey + ", got " + substructure.tierDomains().size());
            }
        }
        if (!uniqueKeys.contains(initialSelection.activeSubstructureId())) {
            throw new IllegalArgumentException("Active structure is not allowed by this preview session: " +
                    initialSelection.activeSubstructureId());
        }
        return List.copyOf(uniqueKeys);
    }
}
