package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructureSelection;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneElement;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityDataCoreAutoBuildProviderGameTest {

    private TrinityDataCoreAutoBuildProviderGameTest() {}

    @TestHolder("trinity_auto_build_provider_retains_draft_and_submits_exact_generation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void providerRetainsDraftAndSubmitsExactGeneration(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        RecordingSceneBinder binder = new RecordingSceneBinder();
        StructurePreviewUiFactory previewFactory = StructurePreviewUiFactory.create(binder);
        List<MultiblockPreviewSpec> suppliedSpecs = new ArrayList<>();
        List<SubmissionRecord> submissions = new ArrayList<>();
        List<Long> pendingGenerations = new ArrayList<>();
        HostSubUiProvider clientProvider = TrinityDataCoreStructureProviders.autoBuildForTesting(
                () -> {
                    MultiblockPreviewSpec spec = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                            .require(ModVerticalMultiBlocks.trinityDataCoreId());
                    suppliedSpecs.add(spec);
                    return spec;
                },
                previewFactory,
                () -> true,
                (generation, submission) -> {
                    submissions.add(new SubmissionRecord(generation, submission));
                    pendingGenerations.add(generation);
                },
                pendingGenerations::contains);
        Endpoint client = createEndpoint(player, 93, clientProvider);

        open(client, 1L);
        AutoBuildWindow first = captureWindow(client.modularUI());
        assertEquals(1, suppliedSpecs.size());
        assertEquals(1, binder.bindCount());
        assertEquals(1, binder.refreshCount());
        assertDefaultDraft(first.controls().draft());
        exerciseIndependentStructureChoices(client.modularUI(), first.controls());
        assertNoCandidateOverrides(first.controls().draft());

        TrinityAutoBuildSubmission beforeLayer = first.controls().draft().submission();
        dispatch(client.modularUI(), previewId() + StructurePreviewPanel.LAYER_NEXT_SUFFIX);
        assertEquals(beforeLayer, first.controls().draft().submission());

        TrinityAutoBuildSubmission expectedSubmission = first.controls().draft().submission();
        dispatch(client.modularUI(), TrinityDataCoreAutoBuildPanel.CONFIRM_BUTTON_ID);
        assertEquals(List.of(new SubmissionRecord(1L, expectedSubmission)), submissions);
        dispatch(client.modularUI(), TrinityDataCoreAutoBuildPanel.CONFIRM_BUTTON_ID);
        assertEquals(1, submissions.size());
        pendingGenerations.clear();
        first.controls().screenTick();
        dispatch(client.modularUI(), TrinityDataCoreAutoBuildPanel.CONFIRM_BUTTON_ID);
        assertEquals(
                List.of(new SubmissionRecord(1L, expectedSubmission), new SubmissionRecord(1L, expectedSubmission)),
                submissions);

        int refreshCountBeforeReopen = binder.refreshCount();
        close(client, 2L);
        assertTrue(!first.root().hasParent(), "Closed automatic-build root must leave the host tree");
        assertTrue(first.root().getModularUI() == null, "Closed automatic-build root must release its ModularUI");
        assertTrue(!first.scene().hasParent(), "Closed automatic-build scene must leave the released element tree");
        assertTrue(first.scene().getModularUI() == null, "Closed automatic-build scene must release its ModularUI");

        pendingGenerations.clear();
        open(client, 3L);
        AutoBuildWindow reopened = captureWindow(client.modularUI());
        assertEquals(2, suppliedSpecs.size());
        assertNotSame(first.root(), reopened.root());
        assertNotSame(first.controls(), reopened.controls());
        assertNotSame(first.controls().draft(), reopened.controls().draft());
        assertNotSame(first.preview(), reopened.preview());
        assertNotSame(first.preview().session(), reopened.preview().session());
        assertNotSame(first.preview().session().selection(), reopened.preview().session().selection());
        assertNotSame(first.preview().session().snapshot(), reopened.preview().session().snapshot());
        assertNotSame(first.preview().session().viewState(), reopened.preview().session().viewState());
        assertNotSame(first.preview().session().recipeView(), reopened.preview().session().recipeView());
        assertNotSame(first.scene(), reopened.scene());
        assertEquals(2, binder.bindCount());
        assertEquals(refreshCountBeforeReopen + 1, binder.refreshCount());
        assertDefaultDraft(reopened.controls().draft());
        dispatch(client.modularUI(), TrinityDataCoreAutoBuildPanel.CONFIRM_BUTTON_ID);
        assertEquals(3L, submissions.getLast().generation());
        client.close();

        int bindCountBeforeServer = binder.bindCount();
        HostSubUiProvider serverProvider = TrinityDataCoreStructureProviders.autoBuildForTesting(
                () -> ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                        .require(ModVerticalMultiBlocks.trinityDataCoreId()),
                previewFactory,
                () -> false,
                (generation, submission) -> {
                    throw new GameTestAssertException("Logical server tree must not submit a local automatic build");
                },
                generation -> false);
        Endpoint server = createEndpoint(player, 94, serverProvider);
        open(server, 1L);
        AutoBuildWindow serverWindow = captureWindow(server.modularUI());
        assertEquals(bindCountBeforeServer, binder.bindCount());
        assertTrue(serverWindow.scene().getDummyWorld() == null,
                "Logical server automatic-build scene must retain a null dummy world");
        server.close();
        helper.succeed();
    }

    private static void exerciseIndependentStructureChoices(HostModularUI modularUI,
                                                            TrinityDataCoreAutoBuildPanel controls) {
        TrinityAutoBuildDraft initial = controls.draft();
        SubstructureSelection initialCpu = initial.previewSelection()
                .selection(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME);
        SubstructureSelection initialCrafting = initial.previewSelection()
                .selection(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME);

        TrinityAutoBuildSubmission mainBeforeTier = controls.draft().submission();
        dispatch(modularUI, previewId() + StructurePreviewPanel.TIER_NEXT_SUFFIX);
        assertNotEquals(mainBeforeTier, controls.draft().submission());
        SubstructureSelection changedMain = controls.draft().previewSelection().selection("main");
        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.BUILD_REQUESTED_BUTTON_ID);
        assertTrue(!controls.draft().activeBuildRequested(), "Main build choice must toggle off");

        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.STRUCTURE_NEXT_ID);
        assertEquals(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                controls.draft().previewSelection().activeSubstructureId());
        assertEquals(initialCpu, controls.draft().previewSelection().activeSelection());
        assertTrue(!controls.draft().activeBuildRequested(), "CPU must retain its disabled default");
        TrinityAutoBuildSubmission cpuBeforeTier = controls.draft().submission();
        dispatch(modularUI, previewId() + StructurePreviewPanel.TIER_NEXT_SUFFIX);
        assertNotEquals(cpuBeforeTier, controls.draft().submission());
        int cpuRepeatUnit = controls.preview().session().variableRepeatUnits().getFirst();
        TrinityAutoBuildSubmission cpuBeforeRepeat = controls.draft().submission();
        dispatch(modularUI, previewId() + StructurePreviewPanel.REPEAT_SUFFIX + cpuRepeatUnit + "_next");
        assertNotEquals(cpuBeforeRepeat, controls.draft().submission());
        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.BUILD_REQUESTED_BUTTON_ID);
        SubstructureSelection changedCpu = controls.draft().previewSelection().activeSelection();
        assertTrue(controls.draft().activeBuildRequested(), "CPU build choice must toggle on");

        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.STRUCTURE_NEXT_ID);
        assertEquals(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME,
                controls.draft().previewSelection().activeSubstructureId());
        assertEquals(initialCrafting, controls.draft().previewSelection().activeSelection());
        assertTrue(!controls.draft().activeBuildRequested(), "Crafting must retain its disabled default");
        dispatch(modularUI, previewId() + StructurePreviewPanel.TIER_NEXT_SUFFIX);
        int craftingRepeatUnit = controls.preview().session().variableRepeatUnits().getFirst();
        dispatch(modularUI, previewId() + StructurePreviewPanel.REPEAT_SUFFIX + craftingRepeatUnit + "_next");
        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.BUILD_REQUESTED_BUTTON_ID);
        SubstructureSelection changedCrafting = controls.draft().previewSelection().activeSelection();
        assertTrue(controls.draft().activeBuildRequested(), "Crafting build choice must toggle on");

        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.STRUCTURE_NEXT_ID);
        assertEquals(changedMain, controls.draft().previewSelection().activeSelection());
        assertTrue(!controls.draft().activeBuildRequested(), "Main build choice must remain off");
        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.STRUCTURE_NEXT_ID);
        assertEquals(changedCpu, controls.draft().previewSelection().activeSelection());
        assertTrue(controls.draft().activeBuildRequested(), "CPU build choice must remain on");
        dispatch(modularUI, TrinityDataCoreAutoBuildPanel.STRUCTURE_NEXT_ID);
        assertEquals(changedCrafting, controls.draft().previewSelection().activeSelection());
        assertTrue(controls.draft().activeBuildRequested(), "Crafting build choice must remain on");
    }

    private static void assertDefaultDraft(TrinityAutoBuildDraft draft) {
        assertEquals("main", draft.previewSelection().activeSubstructureId());
        assertTrue(draft.buildRequested("main"), "Main must use the enabled automatic-build default");
        assertTrue(!draft.buildRequested(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME),
                "CPU must use the disabled automatic-build default");
        assertTrue(!draft.buildRequested(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME),
                "Crafting must use the disabled automatic-build default");
        assertEquals(TrinityAutoBuildDraft.initial(draft.spec()).previewSelection(), draft.previewSelection());
    }

    private static void assertNoCandidateOverrides(TrinityAutoBuildDraft draft) {
        for (SubstructureSelection selection : draft.previewSelection().substructureSelections().values()) {
            assertTrue(selection.candidateSelections().isEmpty(),
                    "Automatic-build UI must not create candidate overrides");
            assertEquals(0, selection.variantIndex());
        }
    }

    private static AutoBuildWindow captureWindow(HostModularUI modularUI) {
        UIElement root = requireElement(modularUI, TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID);
        TrinityDataCoreAutoBuildPanel controls = requireControls(modularUI);
        StructurePreviewPanel preview = requirePreview(modularUI);
        StructurePreviewSceneElement scene = requireScene(modularUI);
        assertSame(preview, controls.preview().panel());
        assertSame(scene, controls.preview().scene());
        assertSame(preview.session(), controls.preview().session());
        return new AutoBuildWindow(root, controls, preview, scene);
    }

    private static Endpoint createEndpoint(Player player, int containerId, HostSubUiProvider provider) {
        TestMenu menu = new TestMenu(player, containerId);
        UIElement root = new UIElement();
        HostUiExtension extension = HostUiExtension.create(root);
        extension.register(provider);
        HostUiCoordinator coordinator = HostUiCoordinator.createServer(extension, () -> {});
        HostModularUI modularUI = extension.createModularUI(UI.of(root), player);
        AeMenuBridge.create(menu).mount(modularUI);
        return new Endpoint(coordinator, modularUI);
    }

    private static void open(Endpoint endpoint, long sequence) {
        HostUiResponse response = endpoint.coordinator().handleRequest(
                new HostUiRequest(HostUiOperation.OPEN, TrinityDataCoreHostUiKeys.AUTO_BUILD, sequence),
                true);
        assertTrue(response.accepted(), "Expected accepted automatic-build OPEN at " + sequence);
    }

    private static void close(Endpoint endpoint, long sequence) {
        HostUiResponse response = endpoint.coordinator().handleRequest(
                new HostUiRequest(HostUiOperation.CLOSE, TrinityDataCoreHostUiKeys.AUTO_BUILD, sequence),
                true);
        assertTrue(response.accepted(), "Expected accepted automatic-build CLOSE at " + sequence);
    }

    private static void dispatch(HostModularUI modularUI, String id) {
        UIElement element = requireElement(modularUI, id);
        if (!(element instanceof Button button)) {
            throw new GameTestAssertException("LDLib2 automatic-build control is not a button: " + id);
        }
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = 0;
        UIEventDispatcher.dispatchEvent(event);
    }

    private static TrinityDataCoreAutoBuildPanel requireControls(HostModularUI modularUI) {
        UIElement element = requireElement(modularUI, TrinityDataCoreAutoBuildPanel.PANEL_ID);
        if (element instanceof TrinityDataCoreAutoBuildPanel controls) {
            return controls;
        }
        throw new GameTestAssertException("Automatic-build controls use the wrong element type");
    }

    private static StructurePreviewPanel requirePreview(HostModularUI modularUI) {
        UIElement element = requireElement(modularUI, previewId());
        if (element instanceof StructurePreviewPanel preview) {
            return preview;
        }
        throw new GameTestAssertException("Automatic-build preview uses the wrong element type");
    }

    private static StructurePreviewSceneElement requireScene(HostModularUI modularUI) {
        UIElement element = requireElement(modularUI, previewId() + StructurePreviewPanel.SCENE_SUFFIX);
        if (element instanceof StructurePreviewSceneElement scene) {
            return scene;
        }
        throw new GameTestAssertException("Automatic-build scene uses the wrong element type");
    }

    private static UIElement requireElement(HostModularUI modularUI, String id) {
        UIElement element = modularUI.getElementById(id);
        if (element == null) {
            throw new GameTestAssertException("Missing LDLib2 automatic-build element " + id);
        }
        return element;
    }

    private static String previewId() {
        return TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_preview";
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
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

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static final class TestMenu extends AEBaseMenu {

        private TestMenu(Player player, int containerId) {
            super(null, containerId, player.getInventory(), null);
        }
    }

    private static final class RecordingSceneBinder implements StructurePreviewSceneBinder {

        private final List<StructurePreviewSceneElement> scenes = new ArrayList<>();
        private int refreshCount;

        @Override
        public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                                 BiConsumer<BlockPos, Direction> selectionConsumer) {
            this.scenes.add(scene);
            return (snapshot, viewState) -> this.refreshCount++;
        }

        private int bindCount() {
            return this.scenes.size();
        }

        private int refreshCount() {
            return this.refreshCount;
        }
    }

    private record SubmissionRecord(long generation, TrinityAutoBuildSubmission submission) {}

    private record AutoBuildWindow(UIElement root,
                                   TrinityDataCoreAutoBuildPanel controls,
                                   StructurePreviewPanel preview,
                                   StructurePreviewSceneElement scene) {}

    private record Endpoint(HostUiCoordinator coordinator, HostModularUI modularUI) {

        private void close() {
            this.modularUI.onRemoved();
        }
    }
}
