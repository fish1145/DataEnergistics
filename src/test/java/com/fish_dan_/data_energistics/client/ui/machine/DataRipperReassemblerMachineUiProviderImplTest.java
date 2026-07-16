package com.fish_dan_.data_energistics.client.ui.machine;

import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.ae2.DataKeyType;
import com.fish_dan_.data_energistics.client.ui.machine.DataRipperReassemblerMachineUiState.SlotGroup;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.core.localization.Tooltips;
import com.lowdragmc.lowdraglib2.core.mixins.accessor.SlotAccessor;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRipperReassemblerMachineUiProviderImplTest {

    private static final List<Component> FULL_KEY_TOOLTIP = List.of(
            Component.literal("Data"),
            Component.literal("Data Energistics"));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        if (ModList.get() == null) {
            ModList.of(List.of(), List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LayoutProperties.init();
        AEKeyRendering.register(DataKeyType.TYPE, DataKey.class, new TestDataKeyRenderHandlerImpl());
    }

    @Test
    void mapsExistingSlotsWithoutGrowingTheMenu() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        FakeModularUiHolderMenuImpl menu = state.menu;
        int slotCount = menu.slots.size();
        var provider = new DataRipperReassemblerMachineUiProviderImpl();

        ModularUI modularUI = provider.createModularUI(state);
        menu.setModularUI(modularUI);
        provider.mapExistingSlots(menu);
        modularUI.init(176, 183);
        provider.updateMappedSlotPositions();

        assertEquals(slotCount, menu.slots.size());
        for (Slot slot : menu.slots) {
            ItemSlot mapped = menu.ldlib2$getItemSlot(slot);
            assertNotNull(mapped);
            assertSame(slot, mapped.getSlot());
            GuiTextureGroup hover = assertInstanceOf(
                    GuiTextureGroup.class,
                    mapped.getSlotStyle().hoverOverlay());
            assertEquals(2, hover.getTextures().length);
            ColorRectTexture fill = assertInstanceOf(ColorRectTexture.class, hover.getTextures()[0]);
            ColorBorderTexture border = assertInstanceOf(ColorBorderTexture.class, hover.getTextures()[1]);
            assertEquals(0x6693FFDE, fill.color);
            assertEquals(1, border.border);
            assertEquals(0xFF57E0BB, border.color);
            assertTrue(slot instanceof SlotAccessor);
        }

        ItemSlot firstInput = menu.ldlib2$getItemSlot(state.single(SlotGroup.ITEM_INPUT));
        assertEquals(19, Math.round(firstInput.getContentX()));
        assertEquals(22, Math.round(firstInput.getContentY()));
        assertSlotPosition(state.single(SlotGroup.ITEM_INPUT), 19, 22);
        assertSlotPosition(state.single(SlotGroup.FLUID_INPUT_A), 74, 22);
        assertSlotPosition(state.single(SlotGroup.PLAYER_INVENTORY), 8, 99);
        assertSlotPosition(state.single(SlotGroup.UPGRADE), 175, 6);
        assertSlotPosition(state.single(SlotGroup.TOOLBOX), 175, 99);
    }

    @Test
    void mappedSlotsOnlyHitTheirSixteenPixelContent() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        ModularUI modularUI = provider.createModularUI(state);
        state.menu.setModularUI(modularUI);
        provider.mapExistingSlots(state.menu);
        modularUI.init(176, 183);
        provider.updateMappedSlotPositions();

        ItemSlot itemInput = state.menu.ldlib2$getItemSlot(state.single(SlotGroup.ITEM_INPUT));
        ItemSlot keyInput = state.menu.ldlib2$getItemSlot(state.single(SlotGroup.KEY_INPUT));
        ItemSlot upgrade = state.menu.ldlib2$getItemSlot(state.single(SlotGroup.UPGRADE));
        ItemSlot toolbox = state.menu.ldlib2$getItemSlot(state.single(SlotGroup.TOOLBOX));
        assertNotNull(itemInput);
        assertNotNull(keyInput);
        assertNotNull(upgrade);
        assertNotNull(toolbox);

        assertSlotHitBounds(modularUI, itemInput, 19, 22);
        assertSlotHitBounds(modularUI, keyInput, 74, 40);
        assertSlotHitBounds(modularUI, upgrade, 175, 6);
        assertSlotHitBounds(modularUI, toolbox, 175, 99);
    }

    @Test
    void upgradeSlotsUseTheAe2UpgradeBackground() {
        assertSame(
                Icon.BACKGROUND_UPGRADE,
                DataReassemblerUpgradePanelElement.UpgradeSlot.emptyBackgroundIcon());
    }

    @Test
    void ldlibViewerExclusionsTrackExternalPanelsWithAndWithoutToolbox() {
        ModularUI withToolbox = initializedUi(new FakeMachineUiStateImpl(true));
        assertTrue(hasArea(withToolbox.getGuiExtraAreas(), 172, -2, 32, 86));
        assertTrue(hasArea(withToolbox.getGuiExtraAreas(), 174, 93, 59, 66));
        assertNull(withToolbox.ui.rootElement.hitTest(173, -1));
        var panelHit = withToolbox.ui.rootElement.hitTest(174, 0);
        assertNotNull(panelHit);
        assertInstanceOf(DataReassemblerUpgradePanelElement.class, panelHit.getA());

        ModularUI withoutToolbox = initializedUi(new FakeMachineUiStateImpl(false));
        assertTrue(hasArea(withoutToolbox.getGuiExtraAreas(), 172, -2, 32, 86));
        assertFalse(hasArea(withoutToolbox.getGuiExtraAreas(), 174, 93, 59, 66));
    }

    @Test
    void toolbarKeepsTheAe2BoundsForEveryVisibleButtonCount() {
        FakeMachineUiStateImpl oneButtonState = new FakeMachineUiStateImpl();
        assertToolbarBounds(initializedUi(oneButtonState), 24, 28);

        FakeMachineUiStateImpl twoButtonState = new FakeMachineUiStateImpl();
        twoButtonState.hasHelp = true;
        assertToolbarBounds(initializedUi(twoButtonState), 46, 50);

        FakeMachineUiStateImpl threeButtonState = new FakeMachineUiStateImpl();
        threeButtonState.hasHelp = true;
        threeButtonState.autoExportEnabled = true;
        assertToolbarBounds(initializedUi(threeButtonState), 68, 72);
    }

    @Test
    void toolbarOnlyHitsItsFocusableButtons() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        state.hasHelp = true;
        state.autoExportEnabled = true;
        ModularUI modularUI = initializedUi(state);
        UIElement toolbar = element(modularUI, "toolbar");
        UIElement helpButton = element(modularUI, "toolbar-help");
        UIElement autoExportButton = element(modularUI, "toolbar-auto-export");
        UIElement outputSidesButton = element(modularUI, "toolbar-output-sides");

        assertFalse(toolbar.isAllowHitTest());
        assertTrue(helpButton.isFocusable());
        assertTrue(autoExportButton.isFocusable());
        assertTrue(outputSidesButton.isFocusable());
        assertNull(modularUI.ui.rootElement.hitTest(-16, 10));
        assertNull(modularUI.ui.rootElement.hitTest(-7, 22));
        assertNull(modularUI.ui.rootElement.hitTest(-7, 44));
        assertSame(helpButton, modularUI.ui.rootElement.hitTest(-7, 10).getA());
        assertSame(autoExportButton, modularUI.ui.rootElement.hitTest(-7, 32).getA());
        assertSame(outputSidesButton, modularUI.ui.rootElement.hitTest(-7, 54).getA());
    }

    @Test
    void toolbarButtonsKeepAe2HoverFocusAndPressedStates() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        state.autoExportEnabled = true;
        ModularUI modularUI = initializedUi(state);
        DataReassemblerIconButtonElement autoExportButton = assertInstanceOf(
                DataReassemblerIconButtonElement.class,
                element(modularUI, "toolbar-auto-export"));

        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND, autoExportButton.backgroundIcon());
        assertEquals(0, autoExportButton.contentYOffset());

        modularUI.requestFocus(autoExportButton);
        assertTrue(autoExportButton.isFocused());
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS, autoExportButton.backgroundIcon());
        assertEquals(0, autoExportButton.contentYOffset());

        dispatchMouse(autoExportButton, UIEvents.MOUSE_ENTER);
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER, autoExportButton.backgroundIcon());
        assertEquals(1, autoExportButton.contentYOffset());

        dispatchMouse(autoExportButton, UIEvents.MOUSE_DOWN);
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER, autoExportButton.backgroundIcon());
        assertEquals(1, autoExportButton.contentYOffset());

        dispatchMouse(autoExportButton, UIEvents.MOUSE_LEAVE);
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS, autoExportButton.backgroundIcon());
        assertEquals(0, autoExportButton.contentYOffset());
    }

    @Test
    void selectedIconButtonsKeepTheirSelectionWithoutFocus() {
        boolean[] selected = { false };
        var button = new DataReassemblerIconButtonElement(
                Icon.HELP::getBlitter,
                () -> ItemStack.EMPTY,
                List::of,
                () -> selected[0],
                false,
                () -> {});

        assertFalse(button.isFocusable());
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND, button.backgroundIcon());
        selected[0] = true;
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS, button.backgroundIcon());

        dispatchMouse(button, UIEvents.MOUSE_ENTER);
        assertSame(Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER, button.backgroundIcon());
        assertEquals(1, button.contentYOffset());
    }

    @Test
    void repeatedAutoExportClicksUseTheLatestLocalValue() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        provider.createModularUI(state);

        provider.toggleAutoExport();
        assertTrue(state.autoExportEnabled);
        assertEquals(List.of(true), state.autoExportRequests);

        provider.toggleAutoExport();
        assertFalse(state.autoExportEnabled);
        assertEquals(List.of(true, false), state.autoExportRequests);
    }

    @Test
    void outputSideButtonsKeepTheBaselineRelativeLayout() {
        assertArrayEquals(new int[] { 34, 16 }, DataReassemblerOutputSideDialog.position(RelativeSide.TOP));
        assertArrayEquals(new int[] { 34, 38 }, DataReassemblerOutputSideDialog.position(RelativeSide.FRONT));
        assertArrayEquals(new int[] { 14, 38 }, DataReassemblerOutputSideDialog.position(RelativeSide.LEFT));
        assertArrayEquals(new int[] { 54, 38 }, DataReassemblerOutputSideDialog.position(RelativeSide.RIGHT));
        assertArrayEquals(new int[] { 54, 60 }, DataReassemblerOutputSideDialog.position(RelativeSide.BACK));
        assertArrayEquals(new int[] { 34, 60 }, DataReassemblerOutputSideDialog.position(RelativeSide.BOTTOM));
    }

    @Test
    void genericSlotKeepsTheFullAeKeyTooltipBeforeCapacity() {
        GenericStack stack = new GenericStack(DataKey.of(), 42L);
        var slot = new DataReassemblerGenericSlotElement(
                new Slot(new SimpleContainer(1), 0, 0, 0),
                DataRipperReassemblerMachineUiState.GenericStorage.KEY_INPUT,
                () -> stack,
                () -> 64L);
        List<Component> expected = new ArrayList<>(FULL_KEY_TOOLTIP);
        expected.add(Component.literal("42 / 64").withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));

        assertEquals(expected, slot.buildTooltip());
    }

    @Test
    void progressTooltipKeepsTheExpandedAe2Bounds() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        state.hasProgressRange = true;
        state.progressFraction = 0.49D;
        state.progressPercent = 49;
        ModularUI modularUI = initializedUi(state);
        UIElement tooltipArea = modularUI.ui.selectId("progress-tooltip").findFirst().orElseThrow();
        UIElement progress = modularUI.ui.selectId("progress").findFirst().orElseThrow();

        assertElementBounds(tooltipArea, 162, 37, 10, 22);
        assertElementBounds(progress, 164, 39, 6, 18);
        var hit = modularUI.ui.rootElement.hitTest(162, 37);
        assertNotNull(hit);
        assertSame(tooltipArea, hit.getA());

        UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        event.target = tooltipArea;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        assertNotNull(event.hoverTooltips);
        assertEquals(List.of(Component.literal("49%")), event.hoverTooltips.tooltipTexts());
    }

    @Test
    void outputDialogBlocksTheMainUiAndEveryExistingExternalPanel() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        state.hasHelp = true;
        state.autoExportEnabled = true;
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        ModularUI modularUI = provider.createModularUI(state);
        state.menu.setModularUI(modularUI);
        modularUI.init(176, 183);

        provider.openOutputDialog();
        modularUI.init(176, 183);
        assertTrue(provider.isOutputDialogOpen());
        Dialog dialog = assertInstanceOf(
                Dialog.class,
                modularUI.ui.rootElement.getChildren().getLast());

        assertModalMouseTarget(modularUI, dialog, 10, 170);
        assertModalMouseTarget(modularUI, dialog, -7, 10);
        assertModalMouseTarget(modularUI, dialog, 180, 10);
        assertModalMouseTarget(modularUI, dialog, 180, 100);

        assertSame(dialog, modularUI.getFocusedElement());
        assertTrue(provider.isOutputDialogOpen());
        assertTrue(state.autoExportRequests.isEmpty());
        assertTrue(state.outputSides.isEmpty());
    }

    @Test
    void outputDialogOnlyBlocksExternalPanelsThatExist() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl(false);
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        ModularUI modularUI = provider.createModularUI(state);
        state.menu.setModularUI(modularUI);
        modularUI.init(176, 183);

        provider.openOutputDialog();
        modularUI.init(176, 183);

        assertNull(modularUI.ui.rootElement.hitTest(180, 100));
    }

    @Test
    void outputDialogFailFastKeepsClosedStateBeforeLayout() {
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        provider.createModularUI(new FakeMachineUiStateImpl());

        assertThrows(IllegalStateException.class, provider::openOutputDialog);

        assertFalse(provider.isOutputDialogOpen());
    }

    @Test
    void outputDialogConsumesKeysAndEscapeClosesThroughTheModularUi() {
        FakeMachineUiStateImpl state = new FakeMachineUiStateImpl();
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        ModularUI modularUI = provider.createModularUI(state);
        state.menu.setModularUI(modularUI);
        modularUI.init(176, 183);

        provider.openOutputDialog();
        Dialog dialog = assertInstanceOf(
                Dialog.class,
                modularUI.ui.rootElement.getChildren().getLast());
        assertSame(dialog, modularUI.getFocusedElement());

        assertTrue(modularUI.getWidget().keyPressed(GLFW.GLFW_KEY_G, 0, 0));
        assertTrue(provider.isOutputDialogOpen());
        assertSame(dialog, modularUI.getFocusedElement());

        assertTrue(modularUI.getWidget().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(provider.isOutputDialogOpen());
        assertFalse(modularUI.ui.rootElement.getChildren().contains(dialog));
        assertSame(modularUI.ui.rootElement, modularUI.getFocusedElement());
    }

    private static final class FakeMachineUiStateImpl implements DataRipperReassemblerMachineUiState {

        private final FakeModularUiHolderMenuImpl menu = new FakeModularUiHolderMenuImpl();
        private final SimpleContainer container = new SimpleContainer(64);
        private final EnumMap<SlotGroup, List<Slot>> slots = new EnumMap<>(SlotGroup.class);
        private final EnumMap<Direction, Boolean> outputSides = new EnumMap<>(Direction.class);
        private final List<Boolean> autoExportRequests = new ArrayList<>();
        private int nextContainerSlot;
        private boolean autoExportEnabled;
        private boolean hasHelp;
        private boolean hasProgressRange;
        private double progressFraction;
        private int progressPercent;

        private FakeMachineUiStateImpl() {
            this(true);
        }

        private FakeMachineUiStateImpl(boolean includeToolbox) {
            put(SlotGroup.ITEM_INPUT, 9);
            put(SlotGroup.FLUID_INPUT_A, 1);
            put(SlotGroup.FLUID_INPUT_B, 1);
            put(SlotGroup.KEY_INPUT, 1);
            put(SlotGroup.ITEM_OUTPUT_A, 1);
            put(SlotGroup.ITEM_OUTPUT_B, 1);
            put(SlotGroup.ITEM_OUTPUT_C, 1);
            put(SlotGroup.FLUID_OUTPUT_A, 1);
            put(SlotGroup.FLUID_OUTPUT_B, 1);
            put(SlotGroup.KEY_OUTPUT, 1);
            put(SlotGroup.PLAYER_INVENTORY, 27);
            put(SlotGroup.PLAYER_HOTBAR, 9);
            put(SlotGroup.UPGRADE, 4);
            if (includeToolbox) {
                put(SlotGroup.TOOLBOX, 9);
            }
        }

        private void put(SlotGroup group, int count) {
            var groupSlots = new ArrayList<Slot>(count);
            for (int i = 0; i < count; i++) {
                Slot slot = new TestSlotImpl(this.container, this.nextContainerSlot++);
                this.menu.addExistingSlot(slot);
                groupSlots.add(slot);
            }
            this.slots.put(group, List.copyOf(groupSlots));
        }

        private Slot single(SlotGroup group) {
            return this.slots.get(group).getFirst();
        }

        @Override
        public Component title() {
            return Component.literal("Data Reassembler");
        }

        @Override
        public Component inventoryTitle() {
            return Component.literal("Inventory");
        }

        @Override
        public List<Slot> slots(SlotGroup group) {
            return this.slots.getOrDefault(group, List.of());
        }

        @Override
        public @Nullable GenericStack genericStack(GenericStorage storage) {
            return null;
        }

        @Override
        public long capacity(GenericStorage storage) {
            return 64_000L;
        }

        @Override
        public boolean hasProgressRange() {
            return this.hasProgressRange;
        }

        @Override
        public double progressFraction() {
            return this.progressFraction;
        }

        @Override
        public int progressPercent() {
            return this.progressPercent;
        }

        @Override
        public boolean isAutoExportEnabled() {
            return this.autoExportEnabled;
        }

        @Override
        public void setAutoExportEnabled(boolean enabled) {
            this.autoExportEnabled = enabled;
            this.autoExportRequests.add(enabled);
        }

        @Override
        public boolean isOutputSideEnabled(Direction side) {
            return this.outputSides.getOrDefault(side, false);
        }

        @Override
        public void setOutputSideEnabled(Direction side, boolean enabled) {
            this.outputSides.put(side, enabled);
        }

        @Override
        public Direction resolveSide(RelativeSide side) {
            return BlockOrientation.EAST_UP.getSide(side);
        }

        @Override
        public ItemStack outputSideIcon(Direction side) {
            return ItemStack.EMPTY;
        }

        @Override
        public Component machineName() {
            return Component.literal("Data Reassembler");
        }

        @Override
        public boolean hasHelp() {
            return this.hasHelp;
        }

        @Override
        public void openHelp() {
            throw new UnsupportedOperationException("No help topic is configured for this test state");
        }

        @Override
        public List<Component> compatibleUpgradeTooltip() {
            return List.of();
        }

        @Override
        public Component toolboxName() {
            return Component.literal("Toolbox");
        }
    }

    private static void assertSlotPosition(Slot slot, int x, int y) {
        assertEquals(x, slot.x);
        assertEquals(y, slot.y);
    }

    private static ModularUI initializedUi(FakeMachineUiStateImpl state) {
        var provider = new DataRipperReassemblerMachineUiProviderImpl();
        ModularUI modularUI = provider.createModularUI(state);
        state.menu.setModularUI(modularUI);
        modularUI.init(176, 183);
        return modularUI;
    }

    private static boolean hasArea(List<Rect2i> areas, int x, int y, int width, int height) {
        return areas.stream().anyMatch(area -> area.getX() == x && area.getY() == y && area.getWidth() == width && area.getHeight() == height);
    }

    private static UIElement element(ModularUI modularUI, String id) {
        return modularUI.ui.selectId(id).findFirst().orElseThrow();
    }

    private static void assertToolbarBounds(ModularUI modularUI, int toolbarHeight, int backgroundHeight) {
        var toolbar = assertInstanceOf(
                DataRipperReassemblerMachineUiProviderImpl.ToolbarElement.class,
                element(modularUI, "toolbar"));
        assertElementBounds(toolbar, -17, 1, 20, toolbarHeight);
        assertRect(toolbar.backgroundBounds(), -19, 0, 21, backgroundHeight);
        assertTrue(hasArea(modularUI.getGuiExtraAreas(), -17, 1, 20, toolbarHeight));
    }

    private static void assertRect(Rect2i rect, int x, int y, int width, int height) {
        assertEquals(x, rect.getX());
        assertEquals(y, rect.getY());
        assertEquals(width, rect.getWidth());
        assertEquals(height, rect.getHeight());
    }

    private static void dispatchMouse(UIElement target, String eventType) {
        UIEvent event = UIEvent.create(eventType);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void assertModalMouseTarget(ModularUI modularUI, Dialog dialog, float x, float y) {
        var hit = modularUI.ui.rootElement.hitTest(x, y);
        assertNotNull(hit);
        assertTrue(dialog.isAncestorOf(hit.getA()));

        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.x = x;
        event.y = y;
        event.target = hit.getA();
        UIEventDispatcher.dispatchEvent(event);
        assertTrue(event.hasHandler);
        assertSame(dialog, modularUI.getFocusedElement());
    }

    private static void assertElementBounds(UIElement element, int x, int y, int width, int height) {
        assertEquals(x, Math.round(element.getPositionX()));
        assertEquals(y, Math.round(element.getPositionY()));
        assertEquals(width, Math.round(element.getSizeWidth()));
        assertEquals(height, Math.round(element.getSizeHeight()));
    }

    private static void assertSlotHitBounds(ModularUI modularUI, ItemSlot slot, int x, int y) {
        assertEquals(x, Math.round(slot.getPositionX()));
        assertEquals(y, Math.round(slot.getPositionY()));
        assertEquals(16, Math.round(slot.getSizeWidth()));
        assertEquals(16, Math.round(slot.getSizeHeight()));
        assertTrue(hits(modularUI, slot, x, y));
        assertTrue(hits(modularUI, slot, x + 15.5D, y + 15.5D));
        assertFalse(hits(modularUI, slot, x - 0.5D, y + 8));
        assertFalse(hits(modularUI, slot, x + 16.0D, y + 8));
        assertFalse(hits(modularUI, slot, x + 8, y - 0.5D));
        assertFalse(hits(modularUI, slot, x + 8, y + 16.0D));
    }

    private static boolean hits(ModularUI modularUI, ItemSlot slot, double x, double y) {
        var hit = modularUI.ui.rootElement.hitTest(x, y);
        return hit != null && hit.getA() == slot;
    }

    private static final class TestSlotImpl extends Slot implements SlotAccessor {

        private TestSlotImpl(SimpleContainer container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public int getX() {
            return this.x;
        }

        @Override
        public int getY() {
            return this.y;
        }

        @Override
        public void setX(int x) {
            this.x = x;
        }

        @Override
        public void setY(int y) {
            this.y = y;
        }
    }

    private static final class FakeModularUiHolderMenuImpl extends AbstractContainerMenu
                                                           implements IModularUIHolderMenu {

        private final Map<Slot, ItemSlot> mappedSlots = new IdentityHashMap<>();
        private ModularUI modularUI;

        private FakeModularUiHolderMenuImpl() {
            super(null, 0);
        }

        private void addExistingSlot(Slot slot) {
            addSlot(slot);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public @Nullable ModularUI ldlib2$getModularUI() {
            return this.modularUI;
        }

        @Override
        public @Nullable ItemSlot ldlib2$getItemSlot(Slot slot) {
            return this.mappedSlots.get(slot);
        }

        @Override
        public void ldlib2$addSlot(ItemSlot itemSlot) {
            this.mappedSlots.put(itemSlot.getSlot(), itemSlot);
        }

        @Override
        public void ldlib2$setModularUI(ModularUI modularUI) {
            this.modularUI = modularUI;
            modularUI.setMenu(this);
        }
    }

    private static final class TestDataKeyRenderHandlerImpl implements AEKeyRenderHandler<DataKey> {

        @Override
        public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, DataKey stack) {
            throw new UnsupportedOperationException("Rendering is outside this tooltip test");
        }

        @Override
        public void drawOnBlockFace(
                                    PoseStack poseStack,
                                    MultiBufferSource buffers,
                                    DataKey what,
                                    float scale,
                                    int combinedLight,
                                    Level level) {
            throw new UnsupportedOperationException("Block rendering is outside this tooltip test");
        }

        @Override
        public Component getDisplayName(DataKey stack) {
            return FULL_KEY_TOOLTIP.getFirst();
        }

        @Override
        public List<Component> getTooltip(DataKey stack) {
            return FULL_KEY_TOOLTIP;
        }
    }
}
