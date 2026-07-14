package com.fish_dan_.data_energistics.client.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewRenderState;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * LDLib2 2.2.8 client adapter that gives every bound scene its own synthetic world and camera state.
 */
@OnlyIn(Dist.CLIENT)
public final class StructurePreviewSceneBinderImpl implements StructurePreviewSceneBinder {

    @Override
    public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                             BiConsumer<BlockPos, Direction> selectionConsumer) {
        if (scene == null || selectionConsumer == null) {
            throw new IllegalArgumentException("Structure preview scene binding arguments cannot be null");
        }
        if (!scene.hasParent()) {
            throw new IllegalStateException("Structure preview scene must belong to an element tree before binding");
        }

        try {
            TrackedDummyWorld world = new TrackedDummyWorld();
            scene.setOnSelected(selectionConsumer);
            scene.createScene(world)
                    .setTickWorld(false)
                    .setDraggable(true)
                    .setScalable(true)
                    .setRenderSelect(true)
                    .setRenderFacing(false)
                    .setShowHoverBlockTips(true)
                    .useCacheBuffer();
            return new BindingImpl(scene, world);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to bind an LDLib2 structure preview scene", failure);
            throw failure;
        }
    }

    /**
     * Per-scene mutable client view state; no instance or dummy world is shared between windows.
     */
    private static final class BindingImpl implements StructurePreviewSceneBinding {

        /**
         * Common element that owns renderer release through its normal LDLib2 removal lifecycle.
         */
        private final StructurePreviewSceneElement scene;
        /**
         * Synthetic world containing every concrete state from the current snapshot.
         */
        private final TrackedDummyWorld world;
        /**
         * Last successfully installed structure, used to avoid rebuilding the world for layer-only changes.
         */
        @Nullable
        private StructurePreviewSnapshot currentSnapshot;
        /**
         * Last successfully installed client-only layer selection.
         */
        @Nullable
        private PreviewViewState currentViewState;

        private BindingImpl(StructurePreviewSceneElement scene, TrackedDummyWorld world) {
            this.scene = scene;
            this.world = world;
        }

        @Override
        public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
            if (snapshot == null || viewState == null) {
                throw new IllegalArgumentException("Structure preview scene refresh arguments cannot be null");
            }
            boolean snapshotChanged = !snapshot.equals(this.currentSnapshot);
            boolean viewChanged = !viewState.equals(this.currentViewState);
            if (!snapshotChanged && !viewChanged) {
                return;
            }

            try {
                StructurePreviewRenderState renderState = StructurePreviewRenderState.from(snapshot, viewState);
                this.scene.clearSelection();
                if (snapshotChanged) {
                    replaceSnapshot(renderState);
                } else {
                    replaceRenderedCorePreservingCamera(renderState.renderedCore());
                }
                this.currentSnapshot = snapshot;
                this.currentViewState = viewState;
            } catch (RuntimeException | Error failure) {
                if (snapshotChanged) {
                    this.currentSnapshot = null;
                    this.currentViewState = null;
                }
                Data_Energistics.LOGGER.error(
                        "Failed to refresh LDLib2 structure preview scene for {}",
                        snapshot.definitionKey(),
                        failure);
                throw failure;
            }
        }

        /**
         * Replaces world contents only when structure selection actually produced a different snapshot.
         */
        private void replaceSnapshot(StructurePreviewRenderState renderState) {
            this.scene.setRenderedCore(List.of(), null, false);
            this.world.clear();
            Map<BlockPos, BlockInfo> blocks = new LinkedHashMap<>();
            renderState.blockStates().forEach((position, state) -> blocks.put(position, new BlockInfo(state)));
            this.world.addBlocks(blocks);
            List<BlockPos> renderedCore = renderState.renderedCore();
            this.scene.setRenderedCore(renderedCore, null, !renderedCore.isEmpty());
        }

        /**
         * Works around 2.2.8 changing Scene.center even when automatic camera fitting is disabled.
         */
        private void replaceRenderedCorePreservingCamera(List<BlockPos> renderedCore) {
            Vector3f center = new Vector3f(this.scene.getCenter());
            this.scene.setRenderedCore(renderedCore, null, false);
            this.scene.setCenter(center);
        }
    }
}
