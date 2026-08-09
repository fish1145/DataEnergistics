package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.item.vacuum.MeVacuumMenuHost;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.RestrictedInputSlot;

public class MeVacuumMenu extends AEBaseMenu {

    public static final SlotSemantic STORAGE_CELL_RIGHT = SlotSemantics.register("ME_VACUUM_STORAGE_CELL_RIGHT", false);

    private final MeVacuumMenuHost host;

    public MeVacuumMenu(int id, Inventory playerInventory, MeVacuumMenuHost host) {
        super(DEMenus.ME_VACUUM.get(), id, playerInventory, host);
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
            this.addSlot(slot, storageSlotSemantic(i));
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

        private StorageCellSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.STORAGE_CELLS, inv, invSlot);
            this.setStackLimit(1);
        }
    }

    private static SlotSemantic storageSlotSemantic(int slot) {
        return slot < 2 ? SlotSemantics.STORAGE_CELL : STORAGE_CELL_RIGHT;
    }
}
