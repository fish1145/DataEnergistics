package com.fish_dan_.data_energistics.client.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.impl.test.MethodBasedTest;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ForEachTest(side = Dist.CLIENT)
final class StructurePreviewSceneBinderClientTestHolder {

    private static final String SUCCESS_LOG = "Data Energistics client Scene lifecycle tests passed";

    private StructurePreviewSceneBinderClientTestHolder() {}

    @TestHolder(
                value = "structure_preview_client_scene_lifecycle",
                side = Dist.CLIENT,
                enabledByDefault = true)
    static void clientSceneLifecycle(MethodBasedTest test) {
        test.whenEnabled(buses -> {
            try {
                bindingReleasesEveryClientResourceOnce();
                bindingPreservesCleanupFailureOrderAndStillAttemptsRendererOnce();
                muiChangedFailureRetainsTerminalOwnershipAfterRendererRelease();
                attachFailureRetainsPartiallyMountedSceneForCleanup();
                test.pass();
                test.logger().info(SUCCESS_LOG);
            } catch (RuntimeException | Error failure) {
                test.fail(failure.getClass().getSimpleName() + ": " + failure.getMessage());
                test.logger().error("Data Energistics client Scene lifecycle tests failed", failure);
            }
        });
    }

    private static void bindingReleasesEveryClientResourceOnce() {
        List<String> order = new ArrayList<>();
        StructurePreviewSceneElement shell = new StructurePreviewSceneElement();
        RecordingClientScene scene = attach(shell, order);
        RecordingWorld world = new RecordingWorld(order);
        AtomicInteger selections = new AtomicInteger();
        scene.setOnSelected((position, direction) -> selections.incrementAndGet());
        StructurePreviewSceneBinderImpl.BindingImpl binding = new StructurePreviewSceneBinderImpl.BindingImpl(shell, scene, world);

        binding.release();

        assertEquals(List.of("selection", "interaction", "core", "world", "renderer"), order);
        assertEquals(1, scene.rendererReleaseAttempts);
        assertEquals(1, world.clearAttempts);
        assertFalse(scene.hasParent(), "Released client Scene must leave its shell");
        assertTrue(shell.getChildren().isEmpty(), "Released shell must not retain the client Scene");
        scene.getOnSelected().accept(BlockPos.ZERO, Direction.NORTH);
        assertEquals(0, selections.get());

        binding.release();
        assertEquals(List.of("selection", "interaction", "core", "world", "renderer"), order);
        assertEquals(1, scene.rendererReleaseAttempts);
        assertEquals(1, world.clearAttempts);
    }

    private static void bindingPreservesCleanupFailureOrderAndStillAttemptsRendererOnce() {
        List<String> order = new ArrayList<>();
        StructurePreviewSceneElement shell = new StructurePreviewSceneElement();
        RecordingClientScene scene = attach(shell, order);
        RecordingWorld world = new RecordingWorld(order);
        RuntimeException selectionFailure = new RuntimeException("selection");
        RuntimeException coreFailure = new RuntimeException("core");
        RuntimeException worldFailure = new RuntimeException("world");
        RuntimeException removedFailure = new RuntimeException("removed");
        RuntimeException rendererFailure = new RuntimeException("renderer");
        scene.selectionFailure = selectionFailure;
        scene.coreFailure = coreFailure;
        scene.rendererFailure = rendererFailure;
        world.failure = worldFailure;
        scene.addEventListener(UIEvents.REMOVED, event -> {
            order.add("removed");
            throw removedFailure;
        });
        StructurePreviewSceneBinderImpl.BindingImpl binding = new StructurePreviewSceneBinderImpl.BindingImpl(shell, scene, world);

        RuntimeException thrown = captureRuntimeFailure(binding::release);

        assertSame(selectionFailure, thrown);
        assertEquals(List.of(coreFailure, worldFailure, removedFailure), List.of(thrown.getSuppressed()));
        assertEquals(List.of(rendererFailure), List.of(removedFailure.getSuppressed()));
        assertEquals(List.of("selection", "interaction", "core", "world", "removed", "renderer"), order);
        assertEquals(1, scene.rendererReleaseAttempts);
        assertEquals(1, world.clearAttempts);
        assertSame(shell, scene.getParent());
        assertFalse(shell.hasChild(scene), "LDLib2 removes the failed child before dispatching REMOVED");

        assertThrowsIllegalState(() -> shell.attachClientScene(internalElement()));
        binding.release();
        assertEquals(1, scene.rendererReleaseAttempts);
        assertEquals(1, world.clearAttempts);
    }

    private static void muiChangedFailureRetainsTerminalOwnershipAfterRendererRelease() {
        List<String> order = new ArrayList<>();
        UIElement root = new UIElement();
        StructurePreviewSceneElement shell = new StructurePreviewSceneElement();
        RecordingClientScene scene = attach(shell, order);
        RecordingWorld world = new RecordingWorld(order);
        AtomicInteger selections = new AtomicInteger();
        scene.setOnSelected((position, direction) -> selections.incrementAndGet());
        root.addChild(shell);
        ModularUI modularUI = ModularUI.of(UI.of(root));
        modularUI.setMenu(null);
        RuntimeException muiFailure = new RuntimeException("mui");
        scene.addEventListener(UIEvents.MUI_CHANGED, event -> {
            if (event.customData == modularUI) {
                order.add("mui");
                throw muiFailure;
            }
        });
        StructurePreviewSceneBinderImpl.BindingImpl binding = new StructurePreviewSceneBinderImpl.BindingImpl(shell, scene, world);

        RuntimeException thrown = captureRuntimeFailure(() -> shell.removeChild(scene));

        assertSame(muiFailure, thrown);
        assertEquals(List.of("renderer", "mui"), order);
        assertEquals(1, scene.rendererReleaseAttempts);
        assertSame(shell, scene.getParent());
        assertFalse(shell.hasChild(scene), "MUI_CHANGED failure must expose LDLib2's incomplete detach");
        assertThrowsIllegalState(() -> shell.attachClientScene(internalElement()));

        assertThrowsIllegalState(binding::release);
        assertEquals(1, world.clearAttempts);
        assertEquals(1, scene.rendererReleaseAttempts);
        scene.getOnSelected().accept(BlockPos.ZERO, Direction.NORTH);
        assertEquals(0, selections.get());
        binding.release();
    }

    private static void attachFailureRetainsPartiallyMountedSceneForCleanup() {
        List<String> order = new ArrayList<>();
        UIElement root = new UIElement();
        StructurePreviewSceneElement shell = new StructurePreviewSceneElement();
        root.addChild(shell);
        ModularUI modularUI = ModularUI.of(UI.of(root));
        modularUI.setMenu(null);
        RecordingClientScene scene = new RecordingClientScene(order);
        scene.markAsInternal();
        RuntimeException attachFailure = new RuntimeException("attach");
        UIEventListener listener = event -> {
            throw attachFailure;
        };
        scene.addEventListener(UIEvents.MUI_CHANGED, listener);

        RuntimeException thrown = captureRuntimeFailure(() -> shell.attachClientScene(scene));

        assertSame(attachFailure, thrown);
        assertTrue(shell.hasChild(scene), "Partially attached Scene must remain reachable for cleanup");
        assertSame(shell, scene.getParent());
        assertSame(modularUI, scene.getModularUI());
        assertThrowsIllegalState(() -> shell.attachClientScene(internalElement()));

        scene.removeEventListener(UIEvents.MUI_CHANGED, listener);
        RecordingWorld world = new RecordingWorld(order);
        StructurePreviewSceneBinderImpl.BindingImpl binding = new StructurePreviewSceneBinderImpl.BindingImpl(shell, scene, world);
        binding.release();
        assertFalse(scene.hasParent(), "Rollback must complete the partially attached Scene detach");
        assertTrue(shell.getChildren().isEmpty(), "Rollback must clear the partially attached Scene");
        assertEquals(1, world.clearAttempts);
        assertEquals(1, scene.rendererReleaseAttempts);
    }

    private static RecordingClientScene attach(StructurePreviewSceneElement shell, List<String> order) {
        RecordingClientScene scene = new RecordingClientScene(order);
        scene.markAsInternal();
        shell.attachClientScene(scene);
        return scene;
    }

    private static UIElement internalElement() {
        UIElement element = new UIElement();
        element.markAsInternal();
        return element;
    }

    private static RuntimeException captureRuntimeFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new AssertionError("Expected RuntimeException");
    }

    private static void assertThrowsIllegalState(Runnable action) {
        RuntimeException failure = captureRuntimeFailure(action);
        if (!(failure instanceof IllegalStateException)) {
            throw new AssertionError("Expected IllegalStateException, got " + failure.getClass().getSimpleName());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("Expected identical objects");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static final class RecordingClientScene extends StructurePreviewSceneBinderImpl.ClientScene {

        private final List<String> order;
        private RuntimeException selectionFailure;
        private RuntimeException coreFailure;
        private RuntimeException rendererFailure;
        private int rendererReleaseAttempts;

        private RecordingClientScene(List<String> order) {
            this.order = order;
        }

        @Override
        void clearSelectionCallback() {
            this.order.add("selection");
            if (this.selectionFailure != null) {
                throw this.selectionFailure;
            }
            super.clearSelectionCallback();
        }

        @Override
        void clearInteraction() {
            this.order.add("interaction");
            super.clearInteraction();
        }

        @Override
        void clearRenderedCore() {
            this.order.add("core");
            if (this.coreFailure != null) {
                throw this.coreFailure;
            }
            super.clearRenderedCore();
        }

        @Override
        protected void releaseRendererNow() {
            this.order.add("renderer");
            this.rendererReleaseAttempts++;
            if (this.rendererFailure != null) {
                throw this.rendererFailure;
            }
        }
    }

    private static final class RecordingWorld extends TrackedDummyWorld {

        private final List<String> order;
        private RuntimeException failure;
        private int clearAttempts;

        private RecordingWorld(List<String> order) {
            this.order = order;
        }

        @Override
        public void clear() {
            this.order.add("world");
            this.clearAttempts++;
            if (this.failure != null) {
                throw this.failure;
            }
        }
    }
}
