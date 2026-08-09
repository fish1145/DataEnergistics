package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.ComponentAwarePreviewMaterialAggregator;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterialAggregator;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellRole;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewLayerSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEItemKey;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.ExpandedPatternSnapshot;
import com.modularmc.mdl.api.multiblock.PatternCandidate;
import com.modularmc.mdl.api.multiblock.PatternCellSnapshot;
import com.modularmc.mdl.api.multiblock.PatternCellSource;
import com.modularmc.mdl.api.multiblock.PatternLayerSnapshot;
import com.modularmc.mdl.api.multiblock.PatternLayout;
import com.modularmc.mdl.api.multiblock.PatternProjector;
import com.modularmc.mdl.api.multiblock.PatternRepeatSelection;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical north-facing MDLib projector with explicit, component-aware candidate resolution.
 */
public final class MdlibNorthFacingStructurePreviewProjection implements StructurePreviewProjection {

    private final PreviewMaterialAggregator materialAggregator;

    /**
     * Creates a projector with the standard exact material aggregator.
     */
    public MdlibNorthFacingStructurePreviewProjection() {
        this(new ComponentAwarePreviewMaterialAggregator());
    }

    /**
     * Creates a projector whose material strategy can be replaced by direct logic tests or future consumers.
     *
     * @param materialAggregator component-aware aggregation implementation
     */
    public MdlibNorthFacingStructurePreviewProjection(PreviewMaterialAggregator materialAggregator) {
        this.materialAggregator = materialAggregator;
    }

    @Override
    public StructurePreviewSnapshot project(MultiblockPreviewSpec spec, PreviewSelection selection) {
        selection.validateAgainst(spec);
        SubstructurePreviewSpec substructure = spec.substructure(selection.activeSubstructureId());
        JsonMultiBlockDefinition definition = substructure.definition(selection.activeSelection().variantIndex());
        BlockPattern pattern = definition.pattern();
        PatternLayout layout = pattern.getLayout();
        PatternRepeatSelection repeatSelection = PatternRepeatSelection.of(
                layout,
                selection.activeSelection().repeatCounts());
        ExpandedPatternSnapshot expanded = PatternProjector.expand(pattern, repeatSelection);

        Map<PreviewPredicateKey, PreviewPredicateSnapshot> predicates = new LinkedHashMap<>();
        Set<String> appliedTierDomains = new HashSet<>();
        List<PreviewLayerSnapshot> layers = new ArrayList<>(expanded.layers().size());
        List<PreviewCellSnapshot> cells = new ArrayList<>(expanded.cells().size());
        for (PatternLayerSnapshot expandedLayer : expanded.layers()) {
            List<PreviewCellSnapshot> layerCells = new ArrayList<>(expandedLayer.cells().size());
            for (PatternCellSnapshot expandedCell : expandedLayer.cells()) {
                PatternCellSource source = expandedCell.source();
                PreviewPredicateKey key = new PreviewPredicateKey(source.sourceLayer(), source.y(), source.x());
                PreviewPredicateSnapshot predicate = predicates.get(key);
                if (predicate == null) {
                    predicate = resolvePredicate(
                            spec,
                            substructure,
                            selection.activeSelection(),
                            layout,
                            expandedCell,
                            key,
                            appliedTierDomains);
                    predicates.put(key, predicate);
                }
                PreviewCellSnapshot cell = new PreviewCellSnapshot(
                        expandedCell.relativePosition(),
                        source,
                        predicate);
                layerCells.add(cell);
                cells.add(cell);
            }
            layers.add(new PreviewLayerSnapshot(expandedLayer.index(), expandedLayer.source(), layerCells));
        }
        requireEveryTierDomainApplied(substructure, appliedTierDomains);
        List<PreviewMaterial> materials = this.materialAggregator.aggregate(cells);
        return new StructurePreviewSnapshot(
                selection,
                definition.key(),
                layers,
                cells,
                expanded.bounds(),
                materials);
    }

    private static PreviewPredicateSnapshot resolvePredicate(MultiblockPreviewSpec spec,
                                                             SubstructurePreviewSpec substructure,
                                                             SubstructureSelection selection,
                                                             PatternLayout layout,
                                                             PatternCellSnapshot cell,
                                                             PreviewPredicateKey key,
                                                             Set<String> appliedTierDomains) {
        Integer candidateOverride = selection.candidateSelections().get(key);
        if (isController(layout, cell.source())) {
            if (candidateOverride != null) {
                throw projectionFailure(substructure, cell, "Controller anchor does not accept candidate overrides");
            }
            if (!(spec.ownerOutput().getItem() instanceof BlockItem controllerItem)) {
                throw projectionFailure(substructure, cell, "Controller owner output is not a block item");
            }
            PreviewCandidate controller = PreviewCandidate.concrete(
                    controllerItem.getBlock().defaultBlockState(),
                    spec.ownerOutput());
            return new PreviewPredicateSnapshot(key, PreviewCellRole.CONTROLLER, List.of(controller), 0);
        }

        TraceabilityPredicate predicate = cell.predicate();
        if (predicate.isAny()) {
            if (candidateOverride != null) {
                throw projectionFailure(substructure, cell, "Wildcard cell does not accept candidate overrides");
            }
            return new PreviewPredicateSnapshot(key, PreviewCellRole.WILDCARD, List.of(), -1);
        }

        List<BlockState> candidateStates = predicate.blockStateCandidates();
        boolean allowsAir = predicate.hasAir() || candidateStates.stream().anyMatch(BlockState::isAir);
        List<PreviewCandidate> concreteCandidates = pairedCandidates(predicate, substructure, cell);
        if (concreteCandidates.isEmpty() && allowsAir) {
            int selectedIndex = candidateOverride == null ? 0 : candidateOverride;
            return createPredicateSnapshot(
                    substructure,
                    cell,
                    key,
                    PreviewCellRole.AIR,
                    List.of(PreviewCandidate.empty()),
                    selectedIndex);
        }
        if (concreteCandidates.isEmpty()) {
            throw projectionFailure(
                    substructure,
                    cell,
                    "Material cell requires at least one paired preview state and placement item");
        }

        concreteCandidates = applyTierSelection(
                concreteCandidates,
                substructure,
                selection,
                cell,
                appliedTierDomains);
        List<PreviewCandidate> candidates = new ArrayList<>(concreteCandidates.size() + (allowsAir ? 1 : 0));
        if (allowsAir) {
            candidates.add(PreviewCandidate.empty());
        }
        candidates.addAll(concreteCandidates);
        int selectedIndex = candidateOverride == null ? 0 : candidateOverride;
        return createPredicateSnapshot(
                substructure,
                cell,
                key,
                allowsAir ? PreviewCellRole.OPTIONAL : PreviewCellRole.MATERIAL,
                candidates,
                selectedIndex);
    }

    private static List<PreviewCandidate> pairedCandidates(TraceabilityPredicate predicate,
                                                           SubstructurePreviewSpec substructure,
                                                           PatternCellSnapshot cell) {
        List<PatternCandidate> pairs;
        try {
            pairs = predicate.patternCandidates();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw projectionFailure(substructure, cell, "Predicate candidate pairing failed", exception);
        }
        List<PreviewCandidate> result = new ArrayList<>();
        for (PatternCandidate pair : pairs) {
            BlockState state = pair.previewState();
            if (state.isAir()) {
                throw projectionFailure(
                        substructure,
                        cell,
                        "Air candidates cannot expose placement items");
            }
            ItemStack placementStack = pair.placementStack();
            AEItemKey placementKey = AEItemKey.of(placementStack);
            if (placementKey == null) {
                throw projectionFailure(substructure, cell, "Predicate exposed an empty placement candidate");
            }
            PreviewCandidate candidate = PreviewCandidate.concrete(state, placementKey);
            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static List<PreviewCandidate> applyTierSelection(List<PreviewCandidate> candidates,
                                                             SubstructurePreviewSpec substructure,
                                                             SubstructureSelection selection,
                                                             PatternCellSnapshot cell,
                                                             Set<String> appliedTierDomains) {
        List<PreviewTierDomain> matchingDomains = substructure.tierDomains().stream()
                .filter(domain -> candidates.stream().anyMatch(candidate -> domain.containsBlock(
                        blockId(candidate.state().orElseThrow()))))
                .toList();
        if (matchingDomains.isEmpty()) {
            return candidates;
        }
        if (matchingDomains.size() != 1) {
            throw projectionFailure(substructure, cell, "Predicate candidates overlap multiple preview tier domains");
        }
        PreviewTierDomain domain = matchingDomains.getFirst();
        for (PreviewCandidate candidate : candidates) {
            ResourceLocation candidateBlock = blockId(candidate.state().orElseThrow());
            if (!domain.containsBlock(candidateBlock)) {
                throw projectionFailure(
                        substructure,
                        cell,
                        "Tier domain " + domain.id() + " does not cover every predicate block candidate");
            }
        }
        int selectedValue = selection.tierSelections().get(domain.id());
        ResourceLocation selectedBlock = domain.option(selectedValue).blockId();
        List<PreviewCandidate> selected = candidates.stream()
                .filter(candidate -> blockId(candidate.state().orElseThrow()).equals(selectedBlock))
                .toList();
        if (selected.isEmpty()) {
            throw projectionFailure(
                    substructure,
                    cell,
                    "Selected tier block " + selectedBlock + " is not a predicate candidate");
        }
        appliedTierDomains.add(domain.id());
        return selected;
    }

    private static PreviewPredicateSnapshot createPredicateSnapshot(SubstructurePreviewSpec substructure,
                                                                    PatternCellSnapshot cell,
                                                                    PreviewPredicateKey key,
                                                                    PreviewCellRole role,
                                                                    List<PreviewCandidate> candidates,
                                                                    int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) {
            throw projectionFailure(
                    substructure,
                    cell,
                    "Selected candidate index " + selectedIndex + " is outside 0.." +
                            (candidates.size() - 1));
        }
        return new PreviewPredicateSnapshot(key, role, candidates, selectedIndex);
    }

    private static boolean isController(PatternLayout layout, PatternCellSource source) {
        return source.sourceLayer() == layout.controllerSourceLayer() &&
                source.x() == layout.controllerX() && source.y() == layout.controllerY();
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static void requireEveryTierDomainApplied(SubstructurePreviewSpec substructure,
                                                      Set<String> appliedTierDomains) {
        List<String> expected = substructure.tierDomains().stream().map(PreviewTierDomain::id).toList();
        if (!appliedTierDomains.containsAll(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(appliedTierDomains);
            throw new IllegalStateException("Substructure " + substructure.definition().key() +
                    " does not expose candidates for tier domains " + missing);
        }
    }

    private static IllegalStateException projectionFailure(SubstructurePreviewSpec substructure,
                                                           PatternCellSnapshot cell,
                                                           String detail) {
        return new IllegalStateException("Cannot resolve preview candidates for " + substructure.definition().key() +
                " at " + cell.relativePosition() + " from " + cell.source() + ": " + detail);
    }

    private static IllegalStateException projectionFailure(SubstructurePreviewSpec substructure,
                                                           PatternCellSnapshot cell,
                                                           String detail,
                                                           RuntimeException cause) {
        return new IllegalStateException("Cannot resolve preview candidates for " + substructure.definition().key() +
                " at " + cell.relativePosition() + " from " + cell.source() + ": " + detail, cause);
    }
}
