package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewLayerSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable common-side mapping from a structure snapshot to exact render states and one visible logical slice.
 *
 * @param blockStates  every selected concrete block in stable snapshot order
 * @param renderedCore concrete positions included by the current logical-layer view
 */
public record StructurePreviewRenderState(Map<BlockPos, BlockState> blockStates,
                                          List<BlockPos> renderedCore) {

    /**
     * Copies positions and states so a client renderer never observes caller mutation.
     */
    public StructurePreviewRenderState {
        if (blockStates == null || renderedCore == null) {
            throw new IllegalArgumentException("Structure preview render state arguments cannot be null");
        }
        LinkedHashMap<BlockPos, BlockState> copiedStates = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blockStates.entrySet()) {
            BlockPos position = entry.getKey();
            BlockState state = entry.getValue();
            if (position == null || state == null || state.isAir()) {
                throw new IllegalArgumentException("Structure preview render blocks require concrete positions and states");
            }
            if (copiedStates.put(position.immutable(), state) != null) {
                throw new IllegalArgumentException("Structure preview render blocks contain duplicate position " + position);
            }
        }

        Set<BlockPos> visiblePositions = new HashSet<>();
        List<BlockPos> copiedCore = renderedCore.stream().map(position -> {
            if (position == null) {
                throw new IllegalArgumentException("Structure preview rendered core cannot contain null positions");
            }
            BlockPos immutablePosition = position.immutable();
            if (!copiedStates.containsKey(immutablePosition)) {
                throw new IllegalArgumentException(
                        "Structure preview rendered core position has no concrete state: " + immutablePosition);
            }
            if (!visiblePositions.add(immutablePosition)) {
                throw new IllegalArgumentException(
                        "Structure preview rendered core contains duplicate position " + immutablePosition);
            }
            return immutablePosition;
        }).toList();
        blockStates = Collections.unmodifiableMap(copiedStates);
        renderedCore = copiedCore;
    }

    /**
     * Resolves selected candidates without applying an axis assumption to logical preview layers.
     *
     * @param snapshot  complete projected structure
     * @param viewState all layers or one logical layer
     * @return detached exact block map and filtered rendered core
     */
    public static StructurePreviewRenderState from(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
        if (snapshot == null || viewState == null) {
            throw new IllegalArgumentException("Structure preview render projection arguments cannot be null");
        }

        LinkedHashMap<BlockPos, BlockState> blockStates = new LinkedHashMap<>();
        Set<BlockPos> occupiedPositions = new HashSet<>();
        for (PreviewCellSnapshot cell : snapshot.cells()) {
            BlockPos position = cell.relativePosition().immutable();
            if (!occupiedPositions.add(position)) {
                throw new IllegalArgumentException(
                        "Structure preview snapshot contains duplicate position " + position);
            }
            PreviewCandidate candidate = cell.predicate().selectedCandidate().orElse(null);
            if (candidate == null || candidate.state().isEmpty()) {
                continue;
            }
            BlockState state = candidate.state().orElseThrow();
            if (state.isAir()) {
                throw new IllegalArgumentException(
                        "Structure preview concrete candidate resolves to air at " + position);
            }
            blockStates.put(position, state);
        }

        List<BlockPos> renderedCore = snapshot.visibleLayers(viewState).stream()
                .map(PreviewLayerSnapshot::cells)
                .flatMap(List::stream)
                .map(PreviewCellSnapshot::relativePosition)
                .filter(blockStates::containsKey)
                .map(BlockPos::immutable)
                .toList();
        return new StructurePreviewRenderState(blockStates, renderedCore);
    }
}
