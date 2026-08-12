package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;

import net.minecraft.world.inventory.Slot;

import appeng.menu.SlotSemantic;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;

/**
 * Maps the fixed pattern, aggregate display, catalyst, and composite-key surfaces to existing AE2 menu slots.
 */
public final class PatternBufferCompartmentPanel {

    public static final String PANEL_ID = "pattern_buffer_compartment_panel";
    public static final String PATTERN_PANEL_ID = "pattern_buffer_patterns";
    public static final String DISPLAY_PANEL_ID = "pattern_buffer_aggregate_display";
    public static final String CATALYST_PANEL_ID = "pattern_buffer_catalysts";
    public static final String COMPOSITE_PANEL_ID = "pattern_buffer_composite_keys";
    static final String PATTERN_SLOT_ID_PREFIX = "pattern_buffer_pattern_slot_";
    static final String DISPLAY_SLOT_ID_PREFIX = "pattern_buffer_display_slot_";
    static final String CATALYST_SLOT_ID_PREFIX = "pattern_buffer_catalyst_slot_";
    static final String FLUID_SLOT_ID = "pattern_buffer_fluid_slot";
    static final String KEY_SLOT_ID = "pattern_buffer_key_slot";
    static final String EXTRA_FLUID_SLOT_ID = "pattern_buffer_extra_fluid_slot";

    private static final int SLOT_PITCH = 18;
    private static final int SLOT_BORDER = 1;
    private static final int PATTERN_FIRST_MENU_INDEX = 0;
    private static final int DISPLAY_FIRST_MENU_INDEX = PATTERN_FIRST_MENU_INDEX +
            MePatternBufferBlockEntity.PATTERN_SLOT_COUNT;
    private static final int CATALYST_FIRST_MENU_INDEX = DISPLAY_FIRST_MENU_INDEX +
            CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT;
    private static final int FLUID_MENU_INDEX = CATALYST_FIRST_MENU_INDEX +
            CompartmentMenu.SHARED_CATALYST_SLOT_COUNT;
    private static final int KEY_MENU_INDEX = FLUID_MENU_INDEX + 1;
    private static final int EXTRA_FLUID_MENU_INDEX = KEY_MENU_INDEX + 1;
    private static final int PATTERN_SLOT_LEFT = 8;
    private static final int PATTERN_SLOT_TOP = 16;
    private static final int DISPLAY_SLOT_LEFT = 177;
    private static final int DISPLAY_SLOT_TOP = 15;
    private static final int CATALYST_SLOT_LEFT = 177;
    private static final int CATALYST_SLOT_TOP = 160;
    private static final int COMPOSITE_SLOT_LEFT = 231;
    private static final int COMPOSITE_SLOT_TOP = 160;
    private static final int PANEL_WIDTH = 249;
    private static final int PANEL_HEIGHT = 214;
    private static final IGuiTexture EMPTY_PATTERN_TEXTURE = SpriteTexture
            .of("ae2:textures/guis/states.png")
            .setSprite(240, 128, 16, 16);

    private PatternBufferCompartmentPanel() {}

    /**
     * Wraps all ninety machine slots without reallocating or changing their AE2 interaction semantics.
     *
     * @param menu   fully constructed pattern-buffer menu
     * @param bridge existing-slot bridge owned by the current menu construction
     * @return a fresh machine panel containing every non-player slot exactly once
     */
    public static UIElement create(CompartmentMenu menu, AeMenuBridge bridge) {
        requireHost(menu, bridge);
        validateAppEngGroup(
                menu,
                CompartmentMenu.COMPARTMENT_PATTERN,
                PATTERN_FIRST_MENU_INDEX,
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                false);
        validateAppEngGroup(
                menu,
                CompartmentMenu.COMPARTMENT_PATTERN_BUFFER,
                DISPLAY_FIRST_MENU_INDEX,
                CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT,
                true);
        validateAppEngGroup(
                menu,
                CompartmentMenu.COMPARTMENT_CATALYST,
                CATALYST_FIRST_MENU_INDEX,
                CompartmentMenu.SHARED_CATALYST_SLOT_COUNT,
                false);
        validateFakeSlot(menu, CompartmentMenu.COMPARTMENT_FLUID, FLUID_MENU_INDEX);
        validateFakeSlot(menu, CompartmentMenu.COMPARTMENT_KEY, KEY_MENU_INDEX);
        validateFakeSlot(menu, CompartmentMenu.COMPARTMENT_EXTRA_FLUID, EXTRA_FLUID_MENU_INDEX);

        UIElement patterns = CompartmentSlotPanel.createContiguousGrid(
                menu,
                bridge,
                CompartmentMenu.COMPARTMENT_PATTERN,
                PATTERN_FIRST_MENU_INDEX,
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                9,
                PATTERN_SLOT_LEFT,
                PATTERN_SLOT_TOP,
                PATTERN_PANEL_ID,
                PATTERN_SLOT_ID_PREFIX);
        applyEmptyPatternOverlay(patterns);

        UIElement display = CompartmentSlotPanel.createContiguousGrid(
                menu,
                bridge,
                CompartmentMenu.COMPARTMENT_PATTERN_BUFFER,
                DISPLAY_FIRST_MENU_INDEX,
                CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT,
                3,
                DISPLAY_SLOT_LEFT,
                DISPLAY_SLOT_TOP,
                DISPLAY_PANEL_ID,
                DISPLAY_SLOT_ID_PREFIX);
        UIElement catalysts = CompartmentSlotPanel.createContiguousGrid(
                menu,
                bridge,
                CompartmentMenu.COMPARTMENT_CATALYST,
                CATALYST_FIRST_MENU_INDEX,
                CompartmentMenu.SHARED_CATALYST_SLOT_COUNT,
                3,
                CATALYST_SLOT_LEFT,
                CATALYST_SLOT_TOP,
                CATALYST_PANEL_ID,
                CATALYST_SLOT_ID_PREFIX);

        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout.width(PANEL_WIDTH).height(PANEL_HEIGHT));
        panel.addChildren(patterns, display, catalysts, createCompositePanel(menu, bridge));
        return panel;
    }

    private static UIElement createCompositePanel(CompartmentMenu menu, AeMenuBridge bridge) {
        UIElement panel = new UIElement();
        panel.setId(COMPOSITE_PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(COMPOSITE_SLOT_LEFT - SLOT_BORDER)
                .top(COMPOSITE_SLOT_TOP - SLOT_BORDER)
                .width(SLOT_PITCH)
                .height(3 * SLOT_PITCH));
        addFakeSlot(panel, menu, bridge, CompartmentMenu.COMPARTMENT_FLUID, FLUID_SLOT_ID, 0);
        addFakeSlot(panel, menu, bridge, CompartmentMenu.COMPARTMENT_KEY, KEY_SLOT_ID, 1);
        addFakeSlot(panel, menu, bridge, CompartmentMenu.COMPARTMENT_EXTRA_FLUID, EXTRA_FLUID_SLOT_ID, 2);
        return panel;
    }

    private static void addFakeSlot(UIElement panel,
                                    CompartmentMenu menu,
                                    AeMenuBridge bridge,
                                    SlotSemantic semantic,
                                    String id,
                                    int row) {
        Slot slot = menu.getSlots(semantic).getFirst();
        AeItemSlot wrapper = bridge.wrap(slot);
        wrapper.setId(id);
        wrapper.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        wrapper.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(row * SLOT_PITCH));
        panel.addChild(wrapper);
    }

    private static void applyEmptyPatternOverlay(UIElement patterns) {
        for (int index = 0; index < patterns.getChildren().size(); index++) {
            if (!(patterns.getChildren().get(index) instanceof AeItemSlot wrapper)) {
                throw invalid("pattern panel child " + index + " is not an AE slot wrapper");
            }
            Style.importantPipeline(wrapper.getSlotStyle(), style -> style
                    .slotOverlay(EMPTY_PATTERN_TEXTURE)
                    .showSlotOverlayOnlyEmpty(true));
        }
    }

    private static void validateAppEngGroup(CompartmentMenu menu,
                                            SlotSemantic semantic,
                                            int firstMenuIndex,
                                            int expectedCount,
                                            boolean readOnly) {
        List<Slot> slots = menu.getSlots(semantic);
        if (slots.size() != expectedCount) {
            throw invalid(semantic.id() + " must contain " + expectedCount + " slots, found " + slots.size());
        }
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            if (!(slot instanceof AppEngSlot appEngSlot) || slot.index != firstMenuIndex + index) {
                throw invalid(semantic.id() + " slot " + index + " lost its AppEngSlot index contract");
            }
            if (readOnly && appEngSlot.isDraggable()) {
                throw invalid(semantic.id() + " slot " + index + " must remain non-draggable");
            }
        }
    }

    private static void validateFakeSlot(CompartmentMenu menu, SlotSemantic semantic, int expectedMenuIndex) {
        List<Slot> slots = menu.getSlots(semantic);
        if (slots.size() != 1 || !(slots.getFirst() instanceof FakeSlot) ||
                slots.getFirst().index != expectedMenuIndex) {
            throw invalid(semantic.id() + " must retain fake slot index " + expectedMenuIndex);
        }
    }

    private static void requireHost(CompartmentMenu menu, AeMenuBridge bridge) {
        if (menu == null || bridge == null) {
            throw invalid("menu and bridge must both be present");
        }
        if (!(menu.getHost() instanceof MePatternBufferBlockEntity host) ||
                host.compartmentType() != CompartmentType.PATTERN_BUFFER ||
                host.patternStorage().size() != MePatternBufferBlockEntity.PATTERN_SLOT_COUNT) {
            throw invalid("pattern-buffer panel requires its fixed 54-slot host");
        }
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Pattern-buffer LDLib2 panel invariant failed: {}", message);
        return new IllegalStateException(message);
    }
}
