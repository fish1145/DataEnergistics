package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;

import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Builds fixed LDLib2 grids from slots already owned and indexed by an AE2 compartment menu.
 */
public final class CompartmentSlotPanel {

    private static final int SLOT_PITCH = 18;
    private static final int SLOT_BORDER = 1;

    private CompartmentSlotPanel() {}

    /**
     * Wraps one contiguous semantic group without allocating, reordering, or reindexing any menu slot.
     *
     * @param menu              menu that already owns the complete slot group
     * @param bridge            bridge responsible for the existing-slot mappings
     * @param semantic          AE2 semantic that identifies the group
     * @param firstMenuIndex    required menu index of the first group member
     * @param expectedSlotCount required number of slots in the group
     * @param columns           number of columns in the rectangular grid
     * @param slotLeft          content X coordinate of the first slot
     * @param slotTop           content Y coordinate of the first slot
     * @param panelId           stable element id of the grid root
     * @param slotIdPrefix      stable element id prefix for individual slots
     * @return a fresh panel containing exactly one wrapper for every group slot
     */
    public static UIElement createContiguousGrid(AEBaseMenu menu,
                                                 AeMenuBridge bridge,
                                                 SlotSemantic semantic,
                                                 int firstMenuIndex,
                                                 int expectedSlotCount,
                                                 int columns,
                                                 int slotLeft,
                                                 int slotTop,
                                                 String panelId,
                                                 String slotIdPrefix) {
        validateArguments(
                menu,
                bridge,
                semantic,
                firstMenuIndex,
                expectedSlotCount,
                columns,
                slotLeft,
                slotTop,
                panelId,
                slotIdPrefix);
        List<Slot> slots = menu.getSlots(semantic);
        if (slots.size() != expectedSlotCount) {
            throw invalid(semantic.id() + " must contain " + expectedSlotCount + " slots, found " + slots.size());
        }
        validateSlotOrder(slots, semantic, firstMenuIndex);
        return createGridPanel(
                bridge,
                slots,
                columns,
                slotLeft,
                slotTop,
                panelId,
                slotIdPrefix);
    }

    /**
     * Wraps row-specific semantic groups whose menu indices advance by a fixed stride.
     *
     * @param menu            menu that already owns every row slot
     * @param bridge          bridge responsible for the existing-slot mappings
     * @param rowSemantics    semantic group for each visual row
     * @param firstMenuIndex  menu index of the first visual slot
     * @param menuIndexStride index distance between adjacent visual slots
     * @param columns         number of slots in each row
     * @param slotLeft        content X coordinate of the first slot
     * @param slotTop         content Y coordinate of the first slot
     * @param panelId         stable element id of the grid root
     * @param slotIdPrefix    stable element id prefix for individual slots
     * @return a fresh grid ordered by row and column without changing menu slot order
     */
    public static UIElement createStridedRowGrid(AEBaseMenu menu,
                                                 AeMenuBridge bridge,
                                                 List<SlotSemantic> rowSemantics,
                                                 int firstMenuIndex,
                                                 int menuIndexStride,
                                                 int columns,
                                                 int slotLeft,
                                                 int slotTop,
                                                 String panelId,
                                                 String slotIdPrefix) {
        validateStridedArguments(
                menu,
                bridge,
                rowSemantics,
                firstMenuIndex,
                menuIndexStride,
                columns,
                slotLeft,
                slotTop,
                panelId,
                slotIdPrefix);
        List<Slot> orderedSlots = new ArrayList<>(rowSemantics.size() * columns);
        Set<SlotSemantic> distinctSemantics = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Slot> distinctSlots = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int row = 0; row < rowSemantics.size(); row++) {
            SlotSemantic semantic = rowSemantics.get(row);
            if (semantic == null || !distinctSemantics.add(semantic)) {
                throw invalid("row semantics must be present and distinct");
            }
            List<Slot> rowSlots = menu.getSlots(semantic);
            if (rowSlots.size() != columns) {
                throw invalid(semantic.id() + " must contain " + columns + " slots, found " + rowSlots.size());
            }
            for (int column = 0; column < columns; column++) {
                Slot slot = rowSlots.get(column);
                if (!distinctSlots.add(slot)) {
                    throw invalid(semantic.id() + " overlaps another row at column " + column);
                }
                int visualIndex = row * columns + column;
                int expectedMenuIndex = firstMenuIndex + visualIndex * menuIndexStride;
                if (slot.index != expectedMenuIndex) {
                    throw invalid(semantic.id() + " slot " + column + " has menu index " + slot.index +
                            ", expected " + expectedMenuIndex);
                }
                orderedSlots.add(slot);
            }
        }
        return createGridPanel(
                bridge,
                orderedSlots,
                columns,
                slotLeft,
                slotTop,
                panelId,
                slotIdPrefix);
    }

    private static UIElement createGridPanel(AeMenuBridge bridge,
                                             List<Slot> slots,
                                             int columns,
                                             int slotLeft,
                                             int slotTop,
                                             String panelId,
                                             String slotIdPrefix) {
        int rows = slots.size() / columns;
        UIElement panel = new UIElement();
        panel.setId(panelId);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(slotLeft - SLOT_BORDER)
                .top(slotTop - SLOT_BORDER)
                .width(columns * SLOT_PITCH)
                .height(rows * SLOT_PITCH));

        for (int index = 0; index < slots.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            AeItemSlot wrapper = bridge.wrap(slots.get(index));
            wrapper.setId(slotIdPrefix + index);
            wrapper.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            wrapper.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(column * SLOT_PITCH)
                    .top(row * SLOT_PITCH));
            panel.addChild(wrapper);
        }
        return panel;
    }

    private static void validateStridedArguments(AEBaseMenu menu,
                                                 AeMenuBridge bridge,
                                                 List<SlotSemantic> rowSemantics,
                                                 int firstMenuIndex,
                                                 int menuIndexStride,
                                                 int columns,
                                                 int slotLeft,
                                                 int slotTop,
                                                 String panelId,
                                                 String slotIdPrefix) {
        if (menu == null || bridge == null || rowSemantics == null || rowSemantics.isEmpty()) {
            throw invalid("menu, bridge, and row semantics must all be present");
        }
        if (firstMenuIndex < 0 || menuIndexStride <= 0 || columns <= 0) {
            throw invalid("slot index, stride, and column constraints are invalid");
        }
        validatePanelArguments(slotLeft, slotTop, panelId, slotIdPrefix);
    }

    private static void validateArguments(AEBaseMenu menu,
                                          AeMenuBridge bridge,
                                          SlotSemantic semantic,
                                          int firstMenuIndex,
                                          int expectedSlotCount,
                                          int columns,
                                          int slotLeft,
                                          int slotTop,
                                          String panelId,
                                          String slotIdPrefix) {
        if (menu == null || bridge == null || semantic == null) {
            throw invalid("menu, bridge, and semantic must all be present");
        }
        if (firstMenuIndex < 0 || expectedSlotCount <= 0 || columns <= 0) {
            throw invalid("slot index, count, and column constraints are invalid");
        }
        if (expectedSlotCount % columns != 0) {
            throw invalid("slot count must form a complete rectangular grid");
        }
        validatePanelArguments(slotLeft, slotTop, panelId, slotIdPrefix);
    }

    private static void validatePanelArguments(int slotLeft,
                                               int slotTop,
                                               String panelId,
                                               String slotIdPrefix) {
        if (slotLeft < SLOT_BORDER || slotTop < SLOT_BORDER) {
            throw invalid("slot content coordinates must leave room for their border");
        }
        if (panelId == null || panelId.isBlank() || slotIdPrefix == null || slotIdPrefix.isBlank()) {
            throw invalid("panel and slot element ids must not be blank");
        }
    }

    private static void validateSlotOrder(List<Slot> slots, SlotSemantic semantic, int firstMenuIndex) {
        Set<Slot> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            if (!identities.add(slot)) {
                throw invalid(semantic.id() + " contains a duplicate slot identity at group index " + index);
            }
            int expectedMenuIndex = firstMenuIndex + index;
            if (slot.index != expectedMenuIndex) {
                throw invalid(semantic.id() + " slot " + index + " has menu index " + slot.index +
                        ", expected " + expectedMenuIndex);
            }
        }
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Compartment LDLib2 slot panel invariant failed: {}", message);
        return new IllegalStateException(message);
    }
}
