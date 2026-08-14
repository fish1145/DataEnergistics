package com.fish_dan_.data_energistics.gui.ldlib2.ae.inventory;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;

import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Builds the reusable LDLib2 player-inventory panel from slots already owned by an AE2 menu.
 *
 * <p>
 * The panel preserves both AE2 semantic groups and delegates every wrapper to {@link AeMenuBridge}; it never
 * allocates replacement slots or changes menu indices.
 * </p>
 */
public final class AePlayerInventoryPanel {

    /**
     * Stable root id used by host composition and runtime verification.
     */
    public static final String PANEL_ID = "ae_player_inventory";
    private static final String INVENTORY_SLOT_ID_PREFIX = "ae_player_inventory_";
    private static final String HOTBAR_SLOT_ID_PREFIX = "ae_player_hotbar_";
    private static final int COLUMN_COUNT = 9;
    private static final int INVENTORY_SLOT_COUNT = 27;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int SLOT_PITCH = 18;
    private static final int SLOT_BORDER = 1;

    private AePlayerInventoryPanel() {}

    /**
     * Creates one panel and wraps the menu's exact player slots in their semantic order.
     *
     * @param menu   menu that already owns the player inventory and hotbar slots
     * @param bridge bridge responsible for registering existing-slot wrappers
     * @param layout slot-content coordinates relative to the mounted UI root
     * @return fresh panel containing 36 wrapped slots
     */
    public static UIElement create(AEBaseMenu menu, AeMenuBridge bridge, AePlayerInventoryLayout layout) {
        return create(menu, bridge, layout, IGuiTexture.EMPTY);
    }

    /**
     * Creates a player inventory panel whose complete geometry is supplied by LSS classes.
     *
     * @param menu               menu that owns the existing player slots
     * @param bridge             bridge responsible for preserving every slot identity
     * @param panelClass         LSS class for the panel
     * @param inventoryGridClass LSS class for the three-row inventory grid
     * @param hotbarGridClass    LSS class for the one-row hotbar grid
     * @param slotClass          LSS class applied to every wrapped slot
     * @return fresh panel containing 36 wrapped slots
     */
    public static UIElement createFlow(AEBaseMenu menu,
                                       AeMenuBridge bridge,
                                       String panelClass,
                                       String inventoryGridClass,
                                       String hotbarGridClass,
                                       String slotClass) {
        if (menu == null || bridge == null || isBlank(panelClass) || isBlank(inventoryGridClass) ||
                isBlank(hotbarGridClass) || isBlank(slotClass)) {
            throw invalid("menu, bridge, and all LSS layout classes must be present");
        }
        List<Slot> inventory = menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
        List<Slot> hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
        requireSlotCount("player inventory", inventory, INVENTORY_SLOT_COUNT);
        requireSlotCount("hotbar", hotbar, HOTBAR_SLOT_COUNT);
        requireDistinctSlots(inventory, hotbar);

        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.addClass(panelClass);
        UIElement inventoryGrid = flowGrid(inventoryGridClass);
        UIElement hotbarGrid = flowGrid(hotbarGridClass);
        addFlowGrid(inventoryGrid, bridge, inventory, INVENTORY_SLOT_ID_PREFIX, slotClass);
        addFlowGrid(hotbarGrid, bridge, hotbar, HOTBAR_SLOT_ID_PREFIX, slotClass);
        panel.addChildren(inventoryGrid, hotbarGrid);
        return panel;
    }

    /**
     * Creates one player inventory panel with a caller-owned modular slot surface.
     *
     * @param menu           menu that owns the existing player slots
     * @param bridge         bridge responsible for preserving every slot identity
     * @param layout         slot-content coordinates relative to the mounted root
     * @param slotBackground texture drawn behind each 16x16 slot content area
     * @return fresh panel containing 36 wrapped slots
     */
    public static UIElement create(AEBaseMenu menu,
                                   AeMenuBridge bridge,
                                   AePlayerInventoryLayout layout,
                                   IGuiTexture slotBackground) {
        if (menu == null || bridge == null || layout == null || slotBackground == null) {
            throw invalid("menu, bridge, layout, and slot background must all be present");
        }
        List<Slot> inventory = menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
        List<Slot> hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
        requireSlotCount("player inventory", inventory, INVENTORY_SLOT_COUNT);
        requireSlotCount("hotbar", hotbar, HOTBAR_SLOT_COUNT);
        requireDistinctSlots(inventory, hotbar);

        int hotbarOffset = layout.hotbarTop() - layout.inventoryTop();
        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(panelLayout -> panelLayout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(layout.slotLeft() - SLOT_BORDER)
                .top(layout.inventoryTop() - SLOT_BORDER)
                .width(COLUMN_COUNT * SLOT_PITCH)
                .height(hotbarOffset + SLOT_PITCH));

        addGrid(panel, bridge, inventory, INVENTORY_SLOT_ID_PREFIX, 0, true, slotBackground);
        addGrid(panel, bridge, hotbar, HOTBAR_SLOT_ID_PREFIX, hotbarOffset, false, slotBackground);
        return panel;
    }

    private static void addGrid(UIElement panel,
                                AeMenuBridge bridge,
                                List<Slot> slots,
                                String idPrefix,
                                int top,
                                boolean wrapRows,
                                IGuiTexture slotBackground) {
        for (int index = 0; index < slots.size(); index++) {
            int column = index % COLUMN_COUNT;
            int row = wrapRows ? index / COLUMN_COUNT : 0;
            AeItemSlot wrapper = bridge.wrap(slots.get(index));
            wrapper.setId(idPrefix + index);
            wrapper.getStyle().backgroundTexture(slotBackground);
            wrapper.layout(slotLayout -> slotLayout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(column * SLOT_PITCH)
                    .top(top + row * SLOT_PITCH));
            panel.addChild(wrapper);
        }
    }

    private static UIElement flowGrid(String layoutClass) {
        UIElement grid = new UIElement();
        grid.addClass(layoutClass);
        return grid;
    }

    private static void addFlowGrid(UIElement grid,
                                    AeMenuBridge bridge,
                                    List<Slot> slots,
                                    String idPrefix,
                                    String slotClass) {
        for (int index = 0; index < slots.size(); index++) {
            AeItemSlot wrapper = bridge.wrap(slots.get(index));
            wrapper.setId(idPrefix + index);
            wrapper.addClass(slotClass);
            wrapper.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            grid.addChild(wrapper);
        }
    }

    private static void requireSlotCount(String group, List<Slot> slots, int expected) {
        if (slots.size() != expected) {
            throw invalid(group + " must contain " + expected + " slots, found " + slots.size());
        }
    }

    private static void requireDistinctSlots(List<Slot> inventory, List<Slot> hotbar) {
        Set<Slot> slots = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : inventory) {
            if (!slots.add(slot)) {
                throw invalid("player inventory contains a duplicate slot identity");
            }
        }
        for (Slot slot : hotbar) {
            if (!slots.add(slot)) {
                throw invalid("hotbar overlaps another player slot identity");
            }
        }
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("AE player inventory LDLib2 panel invariant failed: {}", message);
        return new IllegalStateException(message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
