package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataAsynchronousProcessingFactoryBlockEntity;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;

import appeng.api.inventories.InternalInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;

public final class DataAsynchronousProcessingFactoryMenu extends DataRipperReassemblerMenu {

    public DataAsynchronousProcessingFactoryMenu(int id, Inventory playerInventory,
                                                 DataAsynchronousProcessingFactoryBlockEntity host) {
        super(DEMenus.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), id, playerInventory, host);
    }

    @Override
    protected void setupInventorySlots() {
        var host = this.getHost();
        InternalInventory storage = host.getStorageInventory();
        for (int slot = 0; slot < host.getItemInputSlotCount(); slot++) {
            this.addSlot(new ReassemblerItemSlot(storage, slot), SlotSemantics.MACHINE_INPUT);
        }

        this.addSlot(new AppEngSlot(host.getFluidMenuInventoryA(), 0), SlotSemantics.STORAGE);
        this.addSlot(new AppEngSlot(host.getFluidMenuInventoryB(), 0), FLUID_INPUT_B);
        this.addSlot(new AppEngSlot(host.getKeyMenuInventory(), 0), KEY_INPUT);
        this.addSlot(new AppEngSlot(host.getFluidOutputMenuInventoryA(), 0), FLUID_OUTPUT_A);
        this.addSlot(new AppEngSlot(host.getFluidOutputMenuInventoryB(), 0), FLUID_OUTPUT_B);
        this.addSlot(new AppEngSlot(host.getKeyOutputMenuInventory(), 0), KEY_OUTPUT);

        for (int slot = 0; slot < host.getItemOutputSlotCount(); slot++) {
            this.addSlot(new ReassemblerOutputSlot(storage, host.getItemOutputStartSlot() + slot),
                    SlotSemantics.MACHINE_OUTPUT);
        }
    }
}
