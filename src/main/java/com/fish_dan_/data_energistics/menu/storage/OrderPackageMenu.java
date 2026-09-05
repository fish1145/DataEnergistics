package com.fish_dan_.data_energistics.menu.storage;

import com.fish_dan_.data_energistics.item.order.OrderPackageMenuHost;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;

import net.minecraft.world.entity.player.Inventory;

/**
 * Presents one type-only target slot and the player's inventory for an order package.
 */
public final class OrderPackageMenu extends AEBaseMenu {

    /** Semantic used by the screen and item-list integrations to identify the target slot. */
    public static final SlotSemantic TARGET = SlotSemantics.register("ORDER_PACKAGE_TARGET", false);

    /** Network action used to make right-click clearing unconditional. */
    private static final String ACTION_CLEAR_TARGET = "clear_target";

    /** Hosting package and its component-backed configuration inventory. */
    private final OrderPackageMenuHost host;

    /** Creates the server or client menu instance for the located package. */
    public OrderPackageMenu(int id, Inventory playerInventory, OrderPackageMenuHost host) {
        super(DEMenus.ORDER_PACKAGE.get(), id, playerInventory, host);
        this.host = host;
        addSlot(new FakeSlot(host.getTargetInventory().createMenuWrapper(), 0), TARGET);
        createPlayerInventorySlots(playerInventory);
        registerClientAction(ACTION_CLEAR_TARGET, host::clearTarget);
    }

    /** Returns the component-backed item-menu host. */
    public OrderPackageMenuHost getHost() {
        return this.host;
    }

    /** Clears the target on the server, forwarding from the client when necessary. */
    public void clearTarget() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_TARGET);
        } else {
            this.host.clearTarget();
        }
    }
}
