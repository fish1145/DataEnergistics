package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.physical;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;

import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;

/**
 * Assigns stable business identities to the three editor-authored roots of {@code pattern_core.ui.nbt}.
 *
 * <p>
 * The template owns the player-inventory host geometry, scrollable pattern content surface, and close button. Runtime
 * code normalizes the authored inventory placeholders and supplies the physical pattern slots.
 * </p>
 */
final class TrinityPatternCoreNbtLayout {

    static final String ROOT_ID = "trinity_pattern_core_root";
    static final String PLAYER_INVENTORY_ID = ROOT_ID + "_player_inventory";
    static final String CONTENT_ID = ROOT_ID + "_content";
    static final String SCROLLER_ID = ROOT_ID + "_scrollbar";
    static final String CLOSE_ID = ROOT_ID + "_close";

    private static final int AUTHORED_CHILD_COUNT = 3;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_WIDTH = 9 * SLOT_SIZE;
    private static final int PLAYER_INVENTORY_HEIGHT = 4 * SLOT_SIZE + 5;

    private TrinityPatternCoreNbtLayout() {}

    static Controls bind(UIElement root, boolean clientSide) {
        List<UIElement> children = root.getChildren();
        if (children.size() != AUTHORED_CHILD_COUNT) {
            throw new IllegalStateException("Physical pattern core layout expected " + AUTHORED_CHILD_COUNT +
                    " authored root children, found " + children.size());
        }

        root.setId(ROOT_ID);
        UIElement playerInventoryHost = child(children, 0, UIElement.class, "player inventory host");
        playerInventoryHost.setId(PLAYER_INVENTORY_ID);
        InventorySlots playerInventory = nativePlayerInventory(playerInventoryHost);

        UIElement content = child(children, 1, UIElement.class, "pattern content");
        content.setId(CONTENT_ID);
        Scroller.Vertical scrollbar = authoredScrollbar(content, clientSide);
        scrollbar.setId(SCROLLER_ID);

        Button close = child(children, 2, Button.class, "close button");
        close.setId(CLOSE_ID);
        return new Controls(playerInventoryHost, playerInventory, content, scrollbar, close);
    }

    /**
     * Converts the editor's static player-slot placeholders into one native {@link InventorySlots} tree on both
     * logical sides. The editor still owns the host's geometry while LDLib2 owns the real player inventory bindings.
     */
    private static InventorySlots nativePlayerInventory(UIElement host) {
        if (host.getChildren().size() == 1 && host.getChildren().getFirst() instanceof InventorySlots inventory) {
            inventory.setId(PLAYER_INVENTORY_ID + "_slots");
            return inventory;
        }
        host.clearAllChildren();
        InventorySlots inventory = new InventorySlots();
        inventory.setId(PLAYER_INVENTORY_ID + "_slots");
        inventory.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(PLAYER_INVENTORY_WIDTH)
                .height(PLAYER_INVENTORY_HEIGHT));
        host.addChild(inventory);
        return inventory;
    }

    /**
     * LDLib2 may omit nested rendered controls from the logical-server tree. Rebuilds only that structure while
     * preserving the client-side editor hierarchy and its authored pixel layout.
     */
    private static Scroller.Vertical authoredScrollbar(UIElement container, boolean clientSide) {
        if (!clientSide && container.getChildren().isEmpty()) {
            container.addChild(new Scroller.Vertical());
        }
        if (container.getChildren().size() != 1) {
            throw new IllegalStateException("Physical pattern core layout pattern scrollbar" +
                    " container must contain exactly one child");
        }
        UIElement child = container.getChildren().getFirst();
        if (!(child instanceof Scroller.Vertical scrollbar)) {
            throw new IllegalStateException("Physical pattern core layout pattern scrollbar has type " +
                    child.getClass().getName() + ", expected " + Scroller.Vertical.class.getName());
        }
        return scrollbar;
    }

    private static <T extends UIElement> T child(List<UIElement> children,
                                                 int index,
                                                 Class<T> type,
                                                 String role) {
        UIElement child = children.get(index);
        if (!type.isInstance(child)) {
            throw new IllegalStateException("Physical pattern core layout " + role + " has type " +
                    child.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(child);
    }

    record Controls(UIElement playerInventoryHost,
                    InventorySlots playerInventory,
                    UIElement content,
                    Scroller.Vertical scrollbar,
                    Button close) {}
}
