package com.fish_dan_.data_energistics.client.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewRenderState;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * LDLib2 client adapter that gives every bound scene its own synthetic world and camera state.
 */
@OnlyIn(Dist.CLIENT)
public final class StructurePreviewSceneBinderImpl implements StructurePreviewSceneBinder {

    private static final BiConsumer<BlockPos, Direction> NO_SELECTION = (position, direction) -> {};

    @Override
    public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                             BiConsumer<BlockPos, Direction> selectionConsumer) {
        if (scene == null || selectionConsumer == null) {
            throw new IllegalArgumentException("Structure preview scene binding arguments cannot be null");
        }
        if (!scene.hasParent()) {
            throw new IllegalStateException("Structure preview scene must belong to an element tree before binding");
        }

        ClientScene clientScene = new ClientScene();
        clientScene.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .widthPercent(100)
                .heightPercent(100));
        clientScene.markAsInternal();
        TrackedDummyWorld world = new TrackedDummyWorld();
        BindingImpl binding = new BindingImpl(scene, clientScene, world);
        try {
            scene.attachClientScene(clientScene);
            clientScene.setOnSelected(selectionConsumer);
            clientScene.createScene(world)
                    .setTickWorld(false)
                    .setDraggable(true)
                    .setScalable(true)
                    .setRenderSelect(true)
                    .setRenderFacing(false)
                    .setShowHoverBlockTips(true)
                    .useCacheBuffer();
            return binding;
        } catch (RuntimeException | Error failure) {
            try {
                binding.release();
            } catch (RuntimeException | Error releaseFailure) {
                if (failure != releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            Data_Energistics.LOGGER.error("Failed to bind an LDLib2 structure preview scene", failure);
            throw failure;
        }
    }

    /**
     * Per-scene mutable client view state; no instance or dummy world is shared between windows.
     */
    static final class BindingImpl implements StructurePreviewSceneBinding {

        /**
         * Common shell that owns the client scene as a non-addressable internal child.
         */
        private final StructurePreviewSceneElement shell;
        /**
         * Physical-client LDLib2 scene that owns renderer interaction and camera state.
         */
        private final ClientScene scene;
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
        private boolean released;

        BindingImpl(StructurePreviewSceneElement shell,
                    ClientScene scene,
                    TrackedDummyWorld world) {
            this.shell = shell;
            this.scene = scene;
            this.world = world;
        }

        @Override
        public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
            if (snapshot == null || viewState == null) {
                throw new IllegalArgumentException("Structure preview scene refresh arguments cannot be null");
            }
            if (this.released) {
                throw new IllegalStateException("Released structure preview scene binding cannot be refreshed");
            }
            boolean snapshotChanged = !snapshot.equals(this.currentSnapshot);
            boolean viewChanged = !viewState.equals(this.currentViewState);
            if (!snapshotChanged && !viewChanged) {
                return;
            }

            try {
                StructurePreviewRenderState renderState = StructurePreviewRenderState.from(snapshot, viewState);
                this.scene.clearInteraction();
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

        @Override
        public void release() {
            if (this.released) {
                return;
            }
            this.released = true;
            this.currentSnapshot = null;
            this.currentViewState = null;

            Throwable failure = null;
            UIElement parent = this.scene.getParent();
            if (parent != null && parent != this.shell) {
                failure = mergeFailures(
                        failure,
                        new IllegalStateException("Structure preview client scene left its owning shell"));
            }
            failure = runCleanup(failure, this.scene::clearSelectionCallback);
            failure = runCleanup(failure, this.scene::clearInteraction);
            if (!this.scene.rendererReleaseAttempted()) {
                failure = runCleanup(failure, this.scene::clearRenderedCore);
            }
            failure = runCleanup(failure, this.world::clear);
            if (this.scene.getParent() == this.shell || this.shell.hasChild(this.scene)) {
                failure = runCleanup(failure, () -> this.shell.detachClientScene(this.scene));
            }
            failure = runCleanup(failure, this.scene::releaseRendererOnce);
            rethrow(failure);
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
         * Preserves the camera because Scene changes its center even when automatic fitting is disabled.
         */
        private void replaceRenderedCorePreservingCamera(List<BlockPos> renderedCore) {
            Vector3f center = new Vector3f(this.scene.getCenter());
            this.scene.setRenderedCore(renderedCore, null, false);
            this.scene.setCenter(center);
        }
    }

    /**
     * Client-only LDLib2 scene whose renderer release remains exactly-once even when LDLib2 lifecycle listeners fail.
     */
    static class ClientScene extends Scene {

        private boolean rendererReleaseAttempted;

        ClientScene() {
            this.autoReleased = false;
        }

        @Override
        protected void onRemoved() {
            Throwable failure = null;
            try {
                super.onRemoved();
            } catch (RuntimeException | Error removalFailure) {
                failure = mergeFailures(failure, removalFailure);
            }
            failure = runCleanup(failure, this::releaseRendererOnce);
            rethrow(failure);
        }

        void clearSelectionCallback() {
            setOnSelected(NO_SELECTION);
        }

        void clearInteraction() {
            this.dragging = false;
            this.lastClickPosFace = null;
            this.lastHoverPosFace = null;
            this.lastSelectedPosFace = null;
            this.lastHoverItem = null;
        }

        void clearRenderedCore() {
            setRenderedCore(List.of(), null, false);
        }

        boolean rendererReleaseAttempted() {
            return this.rendererReleaseAttempted;
        }

        void releaseRendererOnce() {
            if (this.rendererReleaseAttempted) {
                return;
            }
            this.rendererReleaseAttempted = true;
            releaseRendererNow();
        }

        /**
         * Isolated physical release hook used by the once guard and direct client lifecycle tests.
         */
        protected void releaseRendererNow() {
            super.releaseRendererResource();
        }
    }

    private static @Nullable Throwable runCleanup(@Nullable Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error cleanupFailure) {
            return mergeFailures(failure, cleanupFailure);
        }
        return failure;
    }

    private static Throwable mergeFailures(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
