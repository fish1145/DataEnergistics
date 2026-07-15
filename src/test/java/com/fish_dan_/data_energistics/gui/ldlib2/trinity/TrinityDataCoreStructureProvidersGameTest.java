package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneElement;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import dev.vfyjxf.taffy.style.TaffyDimension;
import org.appliedenergistics.yoga.YogaOverflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityDataCoreStructureProvidersGameTest {

    private static final int AUTO_BUILD_ACTION_TEXT_WIDTH = 62;

    private TrinityDataCoreStructureProvidersGameTest() {}

    @TestHolder("trinity_structure_providers_create_fresh_isolated_preview_windows")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void providersCreateFreshIsolatedPreviewWindows(GameTestHelper helper) {
        assertDefaultWindowOffsets();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TrinityDataCoreMenu stateMenu = new TrinityDataCoreMenu(90, player.getInventory(), null);
        seedDistinctStatus(stateMenu);
        assertStatusBoundaries(stateMenu);

        RecordingSceneBinder binder = new RecordingSceneBinder();
        StructurePreviewUiFactory previewFactory = StructurePreviewUiFactory.create(binder);
        List<Long> refundGenerations = new ArrayList<>();
        List<Long> pendingRefundGenerations = new ArrayList<>();
        List<HostSubUiProvider> clientProviders = new ArrayList<>(TrinityDataCoreStructureProviders.createForTesting(
                stateMenu,
                previewFactory,
                () -> true,
                generation -> {
                    refundGenerations.add(generation);
                    pendingRefundGenerations.add(generation);
                },
                pendingRefundGenerations::contains));
        clientProviders.add(TrinityDataCoreStructureProviders.autoBuildForTesting(
                () -> ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                        .require(ModVerticalMultiBlocks.trinityDataCoreId()),
                previewFactory,
                () -> true,
                (generation, submission) -> {
                    throw new GameTestAssertException("Window isolation must not submit an automatic build");
                },
                generation -> false));
        Endpoint client = createEndpoint(player, 91, clientProviders);

        long sequence = 1L;
        for (HostUiKey key : TrinityDataCoreHostUiKeys.registrationOrder()) {
            open(client, key, sequence++);
        }
        assertEquals(4, binder.bindCount());
        assertEquals(4, binder.refreshCount());
        assertStatusTextBehavior(client.modularUI());
        assertEquals(TrinityDataCoreHostUiKeys.registrationOrder(), client.extension().registeredKeys());

        Map<HostUiKey, WindowIdentity> firstWindows = captureWindows(client.modularUI());
        assertCompleteControls(client.modularUI());
        assertIndependentSessions(firstWindows);
        requestRefund(client.modularUI());
        assertEquals(List.of(3L), refundGenerations);
        requestRefund(client.modularUI());
        assertEquals(List.of(3L), refundGenerations);
        pendingRefundGenerations.clear();

        for (HostUiKey key : List.of(
                TrinityDataCoreHostUiKeys.MAIN,
                TrinityDataCoreHostUiKeys.CPU,
                TrinityDataCoreHostUiKeys.CRAFTING)) {
            close(client, key, sequence++);
            open(client, key, sequence++);
            WindowIdentity reopened = captureWindow(client.modularUI(), key);
            WindowIdentity first = firstWindows.get(key);
            assertNotSame(first.root(), reopened.root());
            assertNotSame(first.panel(), reopened.panel());
            assertNotSame(first.panel().session(), reopened.panel().session());
            assertNotSame(first.scene(), reopened.scene());
        }
        assertEquals(3, binder.releaseCount());
        assertEquals(7, binder.bindCount());
        assertEquals(7, binder.refreshCount());
        requestRefund(client.modularUI());
        assertEquals(List.of(3L, 10L), refundGenerations);
        client.close();
        assertEquals(7, binder.releaseCount());

        int bindCountBeforeServer = binder.bindCount();
        List<HostSubUiProvider> serverProviders = TrinityDataCoreStructureProviders.createForTesting(
                stateMenu,
                previewFactory,
                () -> false,
                generation -> {
                    throw new GameTestAssertException("Server tree must not emit a local refund action");
                },
                generation -> false);
        Endpoint server = createEndpoint(player, 92, serverProviders);
        sequence = 1L;
        for (HostUiKey key : List.of(
                TrinityDataCoreHostUiKeys.MAIN,
                TrinityDataCoreHostUiKeys.CPU,
                TrinityDataCoreHostUiKeys.CRAFTING)) {
            open(server, key, sequence++);
            assertTrue(captureWindow(server.modularUI(), key).scene().getChildren().isEmpty(),
                    "Logical server shell must not contain a physical-client scene");
        }
        assertEquals(bindCountBeforeServer, binder.bindCount());
        server.close();
        helper.succeed();
    }

    @TestHolder("trinity_auto_build_actions_keep_long_text_bounded_and_toggle_from_label")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void autoBuildActionsKeepLongTextBoundedAndToggleFromLabel(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        RecordingSceneBinder binder = new RecordingSceneBinder();
        HostSubUiProvider provider = TrinityDataCoreStructureProviders.autoBuildForTesting(
                () -> ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                        .require(ModVerticalMultiBlocks.trinityDataCoreId()),
                StructurePreviewUiFactory.create(binder),
                () -> true,
                (generation, submission) -> {
                    throw new GameTestAssertException("Text interaction must not submit an automatic build");
                },
                generation -> false);
        Endpoint client = createEndpoint(player, 95, List.of(provider));
        open(client, TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L);

        UIElement panelElement = requireElement(client.modularUI(), TrinityDataCoreAutoBuildPanel.PANEL_ID);
        if (!(panelElement instanceof TrinityDataCoreAutoBuildPanel panel)) {
            throw new GameTestAssertException("Automatic-build controls are not the production panel");
        }
        Toggle buildRequested = requireToggle(
                client.modularUI(), TrinityDataCoreAutoBuildPanel.BUILD_REQUESTED_TOGGLE_ID);
        Button confirm = requireButton(client.modularUI(), TrinityDataCoreAutoBuildPanel.CONFIRM_BUTTON_ID);
        Component longText = Component.literal(
                "Automatic structure construction with an intentionally long localized action label");
        buildRequested.setText(longText);
        confirm.setText(longText);

        assertBoundedRollingText(buildRequested.toggleLabel, AUTO_BUILD_ACTION_TEXT_WIDTH);
        assertBoundedRollingText(confirm.text, AUTO_BUILD_ACTION_TEXT_WIDTH);
        assertEquals(YogaOverflow.HIDDEN, confirm.getStyle().getInline(LayoutProperties.OVERFLOW));

        boolean initiallyRequested = buildRequested.isOn();
        assertTrue(
                panel.draft().activeBuildRequested() == initiallyRequested,
                "Automatic-build draft and toggle must start synchronized");
        dispatchClick(buildRequested.toggleLabel, 1);
        assertTrue(buildRequested.isOn() == initiallyRequested, "Right-clicking the label must not toggle it");
        dispatchClick(buildRequested.toggleLabel, 0);
        assertTrue(buildRequested.isOn() != initiallyRequested, "Left-clicking the label must toggle exactly once");
        assertTrue(
                panel.draft().activeBuildRequested() == buildRequested.isOn(),
                "Label interaction must update the production automatic-build draft");

        client.close();
        helper.succeed();
    }

    private static void assertDefaultWindowOffsets() {
        assertEquals(
                new TrinityHostedWindowChrome.WindowOffset(-104, -24),
                TrinityHostedWindowChrome.defaultOffset(TrinityDataCoreHostUiKeys.MAIN));
        assertEquals(
                new TrinityHostedWindowChrome.WindowOffset(-72, -16),
                TrinityHostedWindowChrome.defaultOffset(TrinityDataCoreHostUiKeys.CPU));
        assertEquals(
                new TrinityHostedWindowChrome.WindowOffset(-40, -8),
                TrinityHostedWindowChrome.defaultOffset(TrinityDataCoreHostUiKeys.CRAFTING));
        assertEquals(
                new TrinityHostedWindowChrome.WindowOffset(-8, 0),
                TrinityHostedWindowChrome.defaultOffset(TrinityDataCoreHostUiKeys.AUTO_BUILD));
    }

    private static Endpoint createEndpoint(Player player, int containerId, List<HostSubUiProvider> providers) {
        TestMenu menu = new TestMenu(player, containerId);
        UIElement root = new UIElement();
        HostUiExtension extension = HostUiExtension.create(root);
        providers.forEach(extension::register);
        HostUiCoordinator coordinator = HostUiCoordinator.createServer(extension, () -> {});
        HostModularUI modularUI = extension.createModularUI(UI.of(root), player);
        AeMenuBridge.create(menu).mount(modularUI);
        return new Endpoint(extension, coordinator, modularUI);
    }

    private static void seedDistinctStatus(TrinityDataCoreMenu menu) {
        menu.online = true;
        menu.structureFormed = true;
        menu.matchedBlockCount = 101;
        menu.lastFailureReason = "main_failure";
        menu.lastFailurePosition = "1, 2, 3";
        menu.storedTypeCount = 102;
        menu.storedTypeCapacityText = "103";
        menu.storedAmountText = "104";
        menu.storedAmountCapacityText = "105";

        menu.cpuStructureFormed = false;
        menu.cpuStructureMatchedBlockCount = 201;
        menu.cpuLastFailureReason = "cpu_failure";
        menu.cpuLastFailurePosition = "4, 5, 6";
        menu.cpuPartitionCount = 202;
        menu.busyCpuPartitionCount = 203;
        menu.cpuStorageBytes = 204L;
        menu.cpuCoProcessors = 205;
        menu.busyCraftingCpuCount = 206;

        menu.craftingStructureFormed = true;
        menu.craftingStructureMatchedBlockCount = 301;
        menu.craftingLastFailureReason = "crafting_failure";
        menu.craftingLastFailurePosition = "7, 8, 9";
        menu.craftingPatternCoreCount = 302;
        menu.craftingPatternCapacity = 303;
        menu.hasRefundablePatternState = true;
    }

    private static void assertStatusBoundaries(TrinityDataCoreMenu menu) {
        TrinityDataCoreStructureDescriptor main = TrinityDataCoreStructureDescriptor.main(menu);
        TrinityDataCoreStructureDescriptor cpu = TrinityDataCoreStructureDescriptor.cpu(menu);
        TrinityDataCoreStructureDescriptor crafting = TrinityDataCoreStructureDescriptor.crafting(menu);

        List<Component> mainBefore = main.statusSnapshot();
        menu.cpuStructureMatchedBlockCount++;
        menu.cpuPartitionCount++;
        menu.craftingPatternCoreCount++;
        assertEquals(mainBefore, main.statusSnapshot());

        List<Component> cpuBefore = cpu.statusSnapshot();
        menu.matchedBlockCount++;
        menu.storedTypeCount++;
        menu.craftingPatternCapacity++;
        assertEquals(cpuBefore, cpu.statusSnapshot());

        List<Component> craftingBefore = crafting.statusSnapshot();
        menu.structureFormed = !menu.structureFormed;
        menu.storedAmountText = "changed";
        menu.cpuCoProcessors++;
        assertEquals(craftingBefore, crafting.statusSnapshot());
    }

    private static Map<HostUiKey, WindowIdentity> captureWindows(HostModularUI modularUI) {
        Map<HostUiKey, WindowIdentity> result = new LinkedHashMap<>();
        result.put(TrinityDataCoreHostUiKeys.MAIN, captureWindow(modularUI, TrinityDataCoreHostUiKeys.MAIN));
        result.put(TrinityDataCoreHostUiKeys.CPU, captureWindow(modularUI, TrinityDataCoreHostUiKeys.CPU));
        result.put(TrinityDataCoreHostUiKeys.CRAFTING, captureWindow(modularUI, TrinityDataCoreHostUiKeys.CRAFTING));
        result.put(TrinityDataCoreHostUiKeys.AUTO_BUILD, captureWindow(
                modularUI, TrinityDataCoreHostUiKeys.AUTO_BUILD));
        return Map.copyOf(result);
    }

    private static WindowIdentity captureWindow(HostModularUI modularUI, HostUiKey key) {
        String structureKey = structureKey(key);
        UIElement root = requireElement(modularUI, TrinityDataCoreStructureProviders.windowId(structureKey));
        StructurePreviewPanel panel = requirePanel(
                modularUI,
                TrinityDataCoreStructureProviders.windowId(structureKey) + "_preview");
        StructurePreviewSceneElement scene = requireScene(
                modularUI,
                TrinityDataCoreStructureProviders.windowId(structureKey) + "_preview" +
                        StructurePreviewPanel.SCENE_SUFFIX);
        assertEquals(
                key.equals(TrinityDataCoreHostUiKeys.AUTO_BUILD) ? "main" : structureKey,
                panel.session().structureKey());
        return new WindowIdentity(root, panel, scene);
    }

    private static void assertCompleteControls(HostModularUI modularUI) {
        String cpuPrefix = TrinityDataCoreStructureProviders.CPU_WINDOW_ID + "_preview";
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.VARIANT_PREVIOUS_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.VARIANT_NEXT_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.TIER_PREVIOUS_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.TIER_NEXT_SUFFIX);
        StructurePreviewPanel cpu = requirePanel(modularUI, cpuPrefix);
        int unitIndex = cpu.session().variableRepeatUnits().getFirst();
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.REPEAT_SUFFIX + unitIndex + "_previous");
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.REPEAT_SUFFIX + unitIndex + "_next");
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.LAYER_ALL_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.LAYER_PREVIOUS_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.LAYER_NEXT_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.SELECTED_BLOCK_SUFFIX);
        requireElement(modularUI, cpuPrefix + StructurePreviewPanel.MATERIALS_SUFFIX);
    }

    private static void assertIndependentSessions(Map<HostUiKey, WindowIdentity> windows) {
        List<WindowIdentity> identities = TrinityDataCoreHostUiKeys.registrationOrder().stream()
                .map(windows::get)
                .toList();
        for (int first = 0; first < identities.size(); first++) {
            for (int second = first + 1; second < identities.size(); second++) {
                assertNotSame(identities.get(first).panel().session(), identities.get(second).panel().session());
                assertNotSame(identities.get(first).scene(), identities.get(second).scene());
            }
        }
    }

    private static void assertStatusTextBehavior(HostModularUI modularUI) {
        String statusId = TrinityDataCoreStructureProviders.windowId("main") + "_main_failure";
        UIElement element = requireElement(modularUI, statusId);
        if (!(element instanceof Label label)) {
            throw new GameTestAssertException("Hosted structure status row is not a label: " + statusId);
        }
        assertEquals(TextWrap.HOVER_ROLL, label.getTextStyle().getInline(PropertyRegistry.TEXT_WRAP));
        assertEquals(YogaOverflow.HIDDEN, label.getStyle().getInline(LayoutProperties.OVERFLOW));
    }

    private static void assertBoundedRollingText(TextElement text, int width) {
        assertEquals(Boolean.FALSE, text.getTextStyle().getInline(PropertyRegistry.ADAPTIVE_WIDTH));
        assertEquals(TextWrap.HOVER_ROLL, text.getTextStyle().getInline(PropertyRegistry.TEXT_WRAP));
        assertEquals(TaffyDimension.length(width), text.getLayout().getInline(LayoutProperties.WIDTH));
        assertEquals(YogaOverflow.HIDDEN, text.getStyle().getInline(LayoutProperties.OVERFLOW));
    }

    private static void dispatchClick(UIElement target, int button) {
        UIEvent event = UIEvent.create(UIEvents.CLICK);
        event.target = target;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, true, true, false);
    }

    private static void requestRefund(HostModularUI modularUI) {
        UIElement element = requireElement(modularUI, TrinityDataCoreStructureStatusPanel.REFUND_BUTTON_ID);
        if (!(element instanceof Button)) {
            throw new GameTestAssertException("Hosted refund element is not a button");
        }
        if (!(element.getParent() instanceof TrinityDataCoreStructureStatusPanel statusPanel)) {
            throw new GameTestAssertException("Hosted refund button must belong to the crafting status panel");
        }
        statusPanel.requestRefund();
    }

    private static void open(Endpoint endpoint, HostUiKey key, long sequence) {
        HostUiResponse response = endpoint.coordinator().handleRequest(
                new HostUiRequest(HostUiOperation.OPEN, key, sequence),
                true);
        assertTrue(response.accepted(), "Expected accepted OPEN for " + key.id() + " at " + sequence);
    }

    private static void close(Endpoint endpoint, HostUiKey key, long sequence) {
        HostUiResponse response = endpoint.coordinator().handleRequest(
                new HostUiRequest(HostUiOperation.CLOSE, key, sequence),
                true);
        assertTrue(response.accepted(), "Expected accepted CLOSE for " + key.id() + " at " + sequence);
    }

    private static String structureKey(HostUiKey key) {
        if (key.equals(TrinityDataCoreHostUiKeys.MAIN)) {
            return "main";
        }
        if (key.equals(TrinityDataCoreHostUiKeys.CPU)) {
            return "cpu";
        }
        if (key.equals(TrinityDataCoreHostUiKeys.CRAFTING)) {
            return "crafting";
        }
        if (key.equals(TrinityDataCoreHostUiKeys.AUTO_BUILD)) {
            return "auto_build";
        }
        throw new GameTestAssertException("Unknown structure provider key " + key.id());
    }

    private static UIElement requireElement(HostModularUI modularUI, String id) {
        UIElement element = modularUI.getElementById(id);
        if (element == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
        return element;
    }

    private static Button requireButton(HostModularUI modularUI, String id) {
        if (requireElement(modularUI, id) instanceof Button button) {
            return button;
        }
        throw new GameTestAssertException("Element is not a button: " + id);
    }

    private static Toggle requireToggle(HostModularUI modularUI, String id) {
        if (requireElement(modularUI, id) instanceof Toggle toggle) {
            return toggle;
        }
        throw new GameTestAssertException("Element is not a toggle: " + id);
    }

    private static StructurePreviewPanel requirePanel(HostModularUI modularUI, String id) {
        if (requireElement(modularUI, id) instanceof StructurePreviewPanel panel) {
            return panel;
        }
        throw new GameTestAssertException("Element is not a structure preview panel: " + id);
    }

    private static StructurePreviewSceneElement requireScene(HostModularUI modularUI, String id) {
        if (requireElement(modularUI, id) instanceof StructurePreviewSceneElement scene) {
            return scene;
        }
        throw new GameTestAssertException("Element is not a structure preview scene: " + id);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void assertNotSame(Object first, Object second) {
        if (first == second) {
            throw new GameTestAssertException("Expected distinct object identities");
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

    private static final class TestMenu extends AEBaseMenu {

        private TestMenu(Player player, int containerId) {
            super(null, containerId, player.getInventory(), null);
        }
    }

    private static final class RecordingSceneBinder implements StructurePreviewSceneBinder {

        private final List<StructurePreviewSceneElement> scenes = new ArrayList<>();
        private int refreshCount;
        private int releaseCount;

        @Override
        public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                                 BiConsumer<BlockPos, Direction> selectionConsumer) {
            this.scenes.add(scene);
            return new StructurePreviewSceneBinding() {

                @Override
                public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
                    refreshCount++;
                }

                @Override
                public void release() {
                    releaseCount++;
                }
            };
        }

        private int bindCount() {
            return this.scenes.size();
        }

        private int refreshCount() {
            return this.refreshCount;
        }

        private int releaseCount() {
            return this.releaseCount;
        }
    }

    private record WindowIdentity(UIElement root,
                                  StructurePreviewPanel panel,
                                  StructurePreviewSceneElement scene) {}

    private record Endpoint(HostUiExtension extension,
                            HostUiCoordinator coordinator,
                            HostModularUI modularUI) {

        private void close() {
            this.modularUI.onRemoved();
        }
    }
}
