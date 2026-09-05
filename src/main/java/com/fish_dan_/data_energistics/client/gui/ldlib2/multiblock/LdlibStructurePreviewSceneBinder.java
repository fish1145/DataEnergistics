package com.fish_dan_.data_energistics.client.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewRenderState;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneElement;

import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * LDLib2 client adapter that gives every bound scene its own synthetic world and camera state.
 */
@OnlyIn(Dist.CLIENT)
public final class LdlibStructurePreviewSceneBinder implements StructurePreviewSceneBinder {

    private static final BiConsumer<BlockPos, Direction> NO_SELECTION = (position, direction) -> {};
    /** Moves the initial camera closer than the rotation-invariant sphere fit while retaining manual scaling. */
    private static final double DEFAULT_CAMERA_DISTANCE_SCALE = 0.7;
    /** Looks slightly below the geometric center so the structure is framed higher in the preview. */
    private static final double DEFAULT_CAMERA_TARGET_LOWERING_SCALE = 0.1;
    /** Supersamples compact structure previews before linearly downscaling them into GUI pixels. */
    private static final double RENDER_SUPERSAMPLE_SCALE = 2.0;

    @Override
    public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                             BiConsumer<BlockPos, Direction> selectionConsumer) {
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
                    .setBeforeWorldRender(ignored -> clientScene.applyFboFilter())
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
        private int viewportWidth;
        private int viewportHeight;
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
        public void constrainToViewport(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Structure preview viewport dimensions must be positive");
            }
            if (this.released) {
                throw new IllegalStateException("Released structure preview scene binding cannot be constrained");
            }
            this.viewportWidth = width;
            this.viewportHeight = height;
            double renderScale = Minecraft.getInstance().getWindow().getGuiScale() * RENDER_SUPERSAMPLE_SCALE;
            int renderWidth = Math.max(width, (int) Math.ceil(width * renderScale));
            int renderHeight = Math.max(height, (int) Math.ceil(height * renderScale));
            this.scene.createScene(this.world, true, Size.of(renderWidth, renderHeight))
                    .setTickWorld(false)
                    .setDraggable(true)
                    .setScalable(true)
                    .setRenderSelect(true)
                    .setRenderFacing(false)
                    .setShowHoverBlockTips(true)
                    .useCacheBuffer();
            if (this.currentSnapshot != null && this.currentViewState != null) {
                StructurePreviewRenderState renderState = StructurePreviewRenderState.from(
                        this.currentSnapshot,
                        this.currentViewState);
                replaceSnapshot(renderState);
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
            this.viewportWidth = 0;
            this.viewportHeight = 0;

            UIElement parent = this.scene.getParent();
            Throwable failure = parent != null && parent != this.shell ?
                    new IllegalStateException("Structure preview client scene left its owning shell") : null;
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
            boolean constrained = this.viewportWidth > 0 && this.viewportHeight > 0;
            this.scene.setRenderedCore(renderedCore, null, !constrained && !renderedCore.isEmpty());
            if (constrained && !renderedCore.isEmpty()) {
                fitConstrainedCamera(renderedCore);
            }
        }

        /**
         * Starts from a complete-volume fit instead of LDLib2's largest-axis heuristic, then applies the
         * authored close-up and upward framing needed by this compact preview cavity.
         */
        private void fitConstrainedCamera(List<BlockPos> renderedCore) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos position : renderedCore) {
                minX = Math.min(minX, position.getX());
                minY = Math.min(minY, position.getY());
                minZ = Math.min(minZ, position.getZ());
                maxX = Math.max(maxX, position.getX());
                maxY = Math.max(maxY, position.getY());
                maxZ = Math.max(maxZ, position.getZ());
            }

            double halfWidth = (maxX - minX + 1) / 2.0;
            double halfHeight = (maxY - minY + 1) / 2.0;
            double halfDepth = (maxZ - minZ + 1) / 2.0;
            double radius = Math.sqrt(
                    halfWidth * halfWidth + halfHeight * halfHeight + halfDepth * halfDepth);
            double verticalHalfFov = Math.toRadians(30);
            double horizontalFitAngle = Math.atan(
                    Math.tan(verticalHalfFov) * this.viewportWidth / this.viewportHeight);
            double limitingAngle = Math.min(verticalHalfFov, horizontalFitAngle);
            float cameraDistance = (float) (radius / Math.sin(limitingAngle) * DEFAULT_CAMERA_DISTANCE_SCALE);
            float targetLowering = (float) (radius * DEFAULT_CAMERA_TARGET_LOWERING_SCALE);
            Vector3f center = new Vector3f(
                    (minX + maxX) / 2.0f + 0.5f,
                    (minY + maxY) / 2.0f + 0.5f - targetLowering,
                    (minZ + maxZ) / 2.0f + 0.5f);
            this.scene.setCenter(center);
            this.scene.setZoom(cameraDistance);
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

        @Nullable
        private RenderTarget filteredFbo;
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
                failure = removalFailure;
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

        void applyFboFilter() {
            if (!(this.renderer instanceof FBOWorldSceneRenderer fboRenderer)) {
                return;
            }
            RenderTarget fbo = fboRenderer.getFbo();
            if (fbo == null || fbo == this.filteredFbo) {
                return;
            }
            fbo.setFilterMode(GL11.GL_LINEAR);
            this.filteredFbo = fbo;
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
            this.filteredFbo = null;
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
