package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.storage.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.slot.CompartmentSlotPanel;
import com.fish_dan_.data_energistics.menu.storage.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.storage.CompartmentSlotLabel;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.IOptionalSlot;
import appeng.menu.slot.RestrictedInputSlot;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;

/**
 * Maps the seven-row plain warehouse, its fake-key columns, and protected upgrades to existing AE2 menu slots.
 */
public final class CompositeWarehousePanel {

    public static final String PANEL_ID = "composite_warehouse_panel";
    public static final String STORAGE_PANEL_ID = "composite_warehouse_storage";
    public static final String FLUID_PANEL_ID = "composite_warehouse_fluids";
    public static final String KEY_PANEL_ID = "composite_warehouse_keys";
    public static final String UPGRADE_PANEL_ID = "composite_warehouse_upgrades";
    public static final String UPGRADE_SIDEBAR_ID = "composite_warehouse_upgrade_sidebar";
    public static final String UPGRADE_SIDEBAR_TITLE_ID = "composite_warehouse_upgrade_sidebar_title";
    static final String STORAGE_SLOT_ID_PREFIX = "composite_warehouse_storage_slot_";
    static final String FLUID_SLOT_ID_PREFIX = "composite_warehouse_fluid_slot_";
    static final String KEY_SLOT_ID_PREFIX = "composite_warehouse_key_slot_";
    static final String UPGRADE_SLOT_ID_PREFIX = "composite_warehouse_upgrade_slot_";

    private static final int SLOT_PITCH = 18;
    private static final int SLOT_BORDER = 1;
    private static final int STORAGE_SLOT_LEFT = 8;
    private static final int FLUID_SLOT_LEFT = 134;
    private static final int KEY_SLOT_LEFT = 152;
    private static final int MACHINE_SLOT_TOP = 29;
    private static final int UPGRADE_SLOT_LEFT = 175;
    private static final int UPGRADE_SLOT_TOP = 1;
    private static final int UPGRADE_SIDEBAR_LEFT = 172;
    private static final int UPGRADE_SIDEBAR_WIDTH = 22;
    private static final int UPGRADE_SIDEBAR_FOOTER_HEIGHT = 13;
    private static final int UPGRADE_SIDEBAR_TITLE_GAP = 2;
    private static final int PANEL_WIDTH = 194;
    private static final int PANEL_HEIGHT = 155;
    private static final IGuiTexture OPTIONAL_FLUID_TEXTURE = SpriteTexture
            .of("ae2:textures/guis/composite_warehouse.png")
            .setSprite(133, 28, 18, 18);
    private static final IGuiTexture OPTIONAL_KEY_TEXTURE = SpriteTexture
            .of("ae2:textures/guis/composite_warehouse.png")
            .setSprite(151, 28, 18, 18);
    private static final List<SlotSemantic> STORAGE_ROW_SEMANTICS = List.of(
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_1,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_2,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_3,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_4,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_5,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_6,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_7);

    private CompositeWarehousePanel() {}

    /**
     * Creates one side-neutral panel from the exact semantic groups prepared by {@link CompartmentMenu}.
     *
     * @param menu   plain input or output warehouse menu
     * @param bridge existing-slot bridge owned by the current menu construction
     * @return fresh machine panel containing every non-player slot exactly once
     */
    public static UIElement create(CompartmentMenu menu, AeMenuBridge bridge) {
        CompositeWarehouseBlockEntity host = requireHost(menu, bridge);
        List<Slot> upgrades = menu.getSlots(SlotSemantics.UPGRADE);
        if (upgrades.size() != host.getUpgrades().size() || upgrades.isEmpty()) {
            throw invalid("upgrade semantic must match the plain warehouse upgrade inventory");
        }
        validateContiguous(upgrades, 0, "upgrade");

        int storageFirstMenuIndex = upgrades.getLast().index + 1;
        UIElement storage = CompartmentSlotPanel.createStridedRowGrid(
                menu,
                bridge,
                STORAGE_ROW_SEMANTICS,
                storageFirstMenuIndex,
                1,
                CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT,
                STORAGE_SLOT_LEFT,
                MACHINE_SLOT_TOP,
                STORAGE_PANEL_ID,
                STORAGE_SLOT_ID_PREFIX);
        restoreOptionalStorageBackgrounds(storage);

        int fluidFirstMenuIndex = storageFirstMenuIndex + CompartmentMenu.COMPOSITE_WAREHOUSE_SLOT_COUNT;
        UIElement fluid = createFakeColumn(
                menu,
                bridge,
                CompartmentMenu.COMPARTMENT_FLUID,
                fluidFirstMenuIndex,
                FLUID_SLOT_LEFT,
                FLUID_PANEL_ID,
                FLUID_SLOT_ID_PREFIX,
                0);
        UIElement key = createFakeColumn(
                menu,
                bridge,
                CompartmentMenu.COMPARTMENT_KEY,
                fluidFirstMenuIndex + 1,
                KEY_SLOT_LEFT,
                KEY_PANEL_ID,
                KEY_SLOT_ID_PREFIX,
                1);

        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout.width(PANEL_WIDTH).height(PANEL_HEIGHT));
        panel.setOverflowVisible(true);
        panel.addChildren(
                createUpgradeSidebar(upgrades.size()),
                storage,
                fluid,
                key,
                createUpgradePanel(bridge, upgrades));
        return panel;
    }

    private static UIElement createUpgradeSidebar(int slotCount) {
        UpgradeSidebarGeometry geometry = upgradeSidebarGeometry(slotCount);
        UIElement sidebar = new UIElement();
        sidebar.setId(UPGRADE_SIDEBAR_ID);
        sidebar.setAllowHitTest(false);
        sidebar.getStyle().backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(0xFFE3E3EA),
                new ColorBorderTexture(-1, 0xFF777784)));
        sidebar.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(geometry.left())
                .top(geometry.top())
                .width(geometry.width())
                .height(geometry.height()));
        sidebar.addChild(CompartmentHostUi.title(
                UPGRADE_SIDEBAR_TITLE_ID,
                Component.translatable("screen.data_energistics.compartment.upgrades"),
                1,
                geometry.titleTop() - geometry.top(),
                geometry.width() - 2));
        return sidebar;
    }

    static UpgradeSidebarGeometry upgradeSidebarGeometry(int slotCount) {
        if (slotCount <= 0) {
            throw invalid("upgrade sidebar requires at least one slot");
        }
        int slotPanelTop = UPGRADE_SLOT_TOP - SLOT_BORDER;
        int slotPanelHeight = slotCount * SLOT_PITCH;
        return new UpgradeSidebarGeometry(
                UPGRADE_SIDEBAR_LEFT,
                slotPanelTop,
                UPGRADE_SIDEBAR_WIDTH,
                slotPanelHeight + UPGRADE_SIDEBAR_FOOTER_HEIGHT,
                slotPanelTop + slotPanelHeight + UPGRADE_SIDEBAR_TITLE_GAP);
    }

    private static UIElement createFakeColumn(CompartmentMenu menu,
                                              AeMenuBridge bridge,
                                              SlotSemantic semantic,
                                              int firstMenuIndex,
                                              int slotLeft,
                                              String panelId,
                                              String slotIdPrefix,
                                              int textureColumn) {
        List<Slot> slots = menu.getSlots(semantic);
        if (slots.size() != CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_COUNT) {
            throw invalid(semantic.id() + " must contain one fake slot per warehouse row");
        }

        UIElement panel = new UIElement();
        panel.setId(panelId);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(slotLeft - SLOT_BORDER)
                .top(MACHINE_SLOT_TOP - SLOT_BORDER)
                .width(SLOT_PITCH)
                .height(slots.size() * SLOT_PITCH));
        for (int row = 0; row < slots.size(); row++) {
            Slot slot = slots.get(row);
            int expectedMenuIndex = firstMenuIndex + row * 2;
            if (slot.index != expectedMenuIndex || !(slot instanceof FakeSlot)) {
                throw invalid(semantic.id() + " row " + row + " must be fake slot index " + expectedMenuIndex);
            }
            validateOptionalColumnSlot(slot, row, textureColumn, semantic);
            AeItemSlot wrapper = bridge.wrap(slot);
            wrapper.setId(slotIdPrefix + row);
            wrapper.getStyle().backgroundTexture(
                    row < CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS ?
                            IGuiTexture.EMPTY : optionalColumnTexture(textureColumn));
            int wrapperTop = row * SLOT_PITCH;
            wrapper.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(0)
                    .top(wrapperTop));
            panel.addChild(wrapper);
        }
        return panel;
    }

    private static UIElement createUpgradePanel(AeMenuBridge bridge, List<Slot> slots) {
        UIElement panel = new UIElement();
        panel.setId(UPGRADE_PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(UPGRADE_SLOT_LEFT - SLOT_BORDER)
                .top(UPGRADE_SLOT_TOP - SLOT_BORDER)
                .width(SLOT_PITCH)
                .height(slots.size() * SLOT_PITCH));
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            if (!(slot instanceof RestrictedInputSlot)) {
                throw invalid("upgrade semantic slot " + index + " must retain RestrictedInputSlot rules");
            }
            AeItemSlot wrapper = bridge.wrap(slot);
            wrapper.setId(UPGRADE_SLOT_ID_PREFIX + index);
            int wrapperTop = index * SLOT_PITCH;
            wrapper.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(0)
                    .top(wrapperTop));
            panel.addChild(wrapper);
        }
        return panel;
    }

    private static void restoreOptionalStorageBackgrounds(UIElement storage) {
        List<UIElement> wrappers = storage.getChildren();
        if (wrappers.size() != CompartmentMenu.COMPOSITE_WAREHOUSE_SLOT_COUNT) {
            throw invalid("storage panel must expose every plain warehouse item slot");
        }
        int firstOptionalSlot = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS *
                CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT;
        for (int index = firstOptionalSlot; index < wrappers.size(); index++) {
            if (!(wrappers.get(index) instanceof AeItemSlot wrapper)) {
                throw invalid("storage panel child " + index + " is not an AE slot wrapper");
            }
            wrapper.getStyle().backgroundTexture(ItemSlot.ITEM_SLOT_TEXTURE);
        }
    }

    private static void validateOptionalColumnSlot(Slot slot,
                                                   int row,
                                                   int textureColumn,
                                                   SlotSemantic semantic) {
        boolean optionalRow = row >= CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS;
        if (!optionalRow && slot instanceof IOptionalSlot) {
            throw invalid(semantic.id() + " base row " + row + " must not be optional");
        }
        if (!optionalRow) {
            return;
        }
        if (!(slot instanceof IOptionalSlot) || !(slot instanceof CompartmentSlotLabel label) ||
                label.slotTextureColumn() != textureColumn) {
            throw invalid(semantic.id() + " expansion row " + row + " lost its optional F/K presentation");
        }
    }

    private static IGuiTexture optionalColumnTexture(int textureColumn) {
        return switch (textureColumn) {
            case 0 -> OPTIONAL_FLUID_TEXTURE;
            case 1 -> OPTIONAL_KEY_TEXTURE;
            default -> throw invalid("unsupported optional column texture " + textureColumn);
        };
    }

    private static CompositeWarehouseBlockEntity requireHost(CompartmentMenu menu, AeMenuBridge bridge) {
        if (menu == null || bridge == null) {
            throw invalid("menu and bridge must both be present");
        }
        if (!(menu.getHost() instanceof CompositeWarehouseBlockEntity host) || !menu.supportsUpgrades()) {
            throw invalid("plain warehouse panel requires its composite warehouse host");
        }
        CompartmentType type = host.compartmentType();
        if (type != CompartmentType.INPUT && type != CompartmentType.OUTPUT) {
            throw invalid("plain warehouse panel cannot represent " + type);
        }
        return host;
    }

    private static void validateContiguous(List<Slot> slots, int firstMenuIndex, String group) {
        for (int index = 0; index < slots.size(); index++) {
            int expectedMenuIndex = firstMenuIndex + index;
            if (slots.get(index).index != expectedMenuIndex) {
                throw invalid(group + " slot " + index + " has menu index " + slots.get(index).index +
                        ", expected " + expectedMenuIndex);
            }
        }
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Composite warehouse LDLib2 panel invariant failed: {}", message);
        return new IllegalStateException(message);
    }

    record UpgradeSidebarGeometry(int left, int top, int width, int height, int titleTop) {

        int right() {
            return left + width;
        }

        int bottom() {
            return top + height;
        }

        boolean contains(int childLeft, int childTop, int childWidth, int childHeight) {
            return childLeft >= left && childTop >= top &&
                    childLeft + childWidth <= right() && childTop + childHeight <= bottom();
        }
    }
}
