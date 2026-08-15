package com.fish_dan_.data_energistics.menu.storage;

import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.CompartmentHostUi;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;

public class CompositeWarehouseMenu extends CompartmentMenu {

    public CompositeWarehouseMenu(int id, Inventory playerInventory, CompositeWarehouseBlockEntity host) {
        super(DEMenus.COMPOSITE_WAREHOUSE.get(), id, playerInventory, host);
        CompartmentHostUi.mountCompositeWarehouse(this);
    }
}
