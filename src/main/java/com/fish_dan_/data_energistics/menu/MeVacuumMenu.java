package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.item.MeVacuumMenuHost;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.RestrictedInputSlot;

public class MeVacuumMenu extends AEBaseMenu {

    private final MeVacuumMenuHost host;

    public MeVacuumMenu(int id, Inventory playerInventory, MeVacuumMenuHost host) {
        super(ModMenus.ME_VACUUM.get(), id, playerInventory, host);
        this.host = host;
        setupStorageSlots();
        setupUpgradeSlots();
        createPlayerInventorySlots(playerInventory);
    }

    public MeVacuumMenuHost getHost() {
        return this.host;
    }

    private void setupStorageSlots() {
        for (int i = 0; i < MeVacuumMenuHost.STORAGE_SLOT_COUNT; i++) {
            var slot = new StorageCellSlot(this.host.getStorage(), i);
            this.addSlot(slot, SlotSemantics.STORAGE_CELL);
        }
    }

    private void setupUpgradeSlots() {
        var upgrades = this.host.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            Slot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, i)
                    .setNotDraggable();
            this.addSlot(slot, SlotSemantics.UPGRADE);
        }
    }

    private static final class StorageCellSlot extends RestrictedInputSlot {

        private StorageCellSlot(appeng.api.inventories.InternalInventory inv, int invSlot) {
            super(PlacableItemType.STORAGE_CELLS, inv, invSlot);
            this.setStackLimit(1);
        }
    }
}
