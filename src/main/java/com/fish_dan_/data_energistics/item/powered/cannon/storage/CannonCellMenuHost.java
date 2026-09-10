package com.fish_dan_.data_energistics.item.powered.cannon.storage;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailLauncher;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;

/** An AE item host binds the removable cell slots to the exact inventory slot that opened the menu. */
public final class CannonCellMenuHost extends ItemMenuHost<MatterConvergingCrossbowItem> implements InternalInventoryHost {

    private final AppEngInternalInventory cells = new AppEngInternalInventory(this, MountedAmmoCells.SLOT_COUNT, 1);

    public CannonCellMenuHost(MatterConvergingCrossbowItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        if (!isClientSide()) RailLauncher.cancel(getItemStack());
        if (!isClientSide()) MountedAmmoCells.migrateLegacy(getItemStack());
        cells.fromItemContainerContents(getItemStack().getOrDefault(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.EMPTY));
    }

    public AppEngInternalInventory cells() {
        return this.cells;
    }

    @Override
    public void tick() {
        if (!isClientSide() && isValid()) {
            MountedAmmoCells.migrateLegacy(getItemStack());
            cells.fromItemContainerContents(getItemStack().getOrDefault(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.EMPTY));
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        if (!isClientSide() && isValid()) {
            MountedAmmoCells.setCells(getItemStack(), inventory.toItemContainerContents());
        }
    }

    @Override
    public long insert(Player player, AEKey key, long amount, Actionable action) {
        if (!(key instanceof AEItemKey itemKey) || !isValid()) return 0;
        long inserted = MountedAmmoCells.insert(getItemStack(), MatterConvergingCrossbowItem.mode(getItemStack()),
                itemKey, amount, IActionSource.ofPlayer(player), action);
        if (inserted > 0 && action == Actionable.MODULATE) {
            cells.fromItemContainerContents(getItemStack().getOrDefault(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.EMPTY));
        }
        return inserted;
    }
}
