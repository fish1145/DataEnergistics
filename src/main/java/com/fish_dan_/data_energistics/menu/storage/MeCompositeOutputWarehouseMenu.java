package com.fish_dan_.data_energistics.menu.storage;

import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.CompartmentHostUi;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;

public class MeCompositeOutputWarehouseMenu extends CompartmentMenu {

    public MeCompositeOutputWarehouseMenu(int id, Inventory playerInventory, MeCompositeOutputWarehouseBlockEntity host) {
        super(DEMenus.ME_COMPOSITE_OUTPUT_WAREHOUSE.get(), id, playerInventory, host);
        CompartmentHostUi.mountMeOutput(this);
    }
}
