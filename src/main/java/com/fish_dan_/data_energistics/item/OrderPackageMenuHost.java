package com.fish_dan_.data_energistics.item;

import net.minecraft.world.entity.player.Player;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.stacks.GenericStack;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.ConfigInventory;

/**
 * Binds the package target component to the single generic configuration slot while the item menu is open.
 */
public final class OrderPackageMenuHost extends ItemMenuHost<OrderPackageItem> {

    /** One target is stored per order package. */
    public static final int TARGET_SLOT_COUNT = 1;

    /** Generic type-only inventory exposed through the AE2 fake-slot protocol. */
    private final ConfigInventory targetInventory;

    /** Prevents constructor hydration from writing the same component back to the hosting stack. */
    private boolean loadingTarget;

    /** Creates a menu host for the package located by AE2's item locator. */
    public OrderPackageMenuHost(OrderPackageItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        this.targetInventory = ConfigInventory.configTypes(TARGET_SLOT_COUNT)
                .changeListener(this::saveTarget)
                .build();
        loadTarget();
    }

    /** Returns the generic target inventory used by the menu fake slot. */
    public ConfigInventory getTargetInventory() {
        return this.targetInventory;
    }

    /** Clears the configured target without consuming or replacing the package item. */
    public void clearTarget() {
        this.targetInventory.clear();
    }

    private void loadTarget() {
        this.loadingTarget = true;
        try {
            OrderPackageTarget.get().getTarget(getItemStack())
                    .ifPresent(target -> this.targetInventory.setStack(0, new GenericStack(target, 0L)));
        } finally {
            this.loadingTarget = false;
        }
    }

    private void saveTarget() {
        if (this.loadingTarget || isClientSide()) {
            return;
        }

        var target = this.targetInventory.getKey(0);
        if (target == null) {
            OrderPackageTarget.get().clearTarget(getItemStack());
        } else {
            OrderPackageTarget.get().setTarget(getItemStack(), target);
        }
    }
}
