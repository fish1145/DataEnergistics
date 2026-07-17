package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.CompartmentHostUi;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;

public class MeCompositeOutputWarehouseMenu extends CompartmentMenu {

    public MeCompositeOutputWarehouseMenu(int id, Inventory playerInventory, MeCompositeOutputWarehouseBlockEntity host) {
        super(ModMenus.ME_COMPOSITE_OUTPUT_WAREHOUSE.get(), id, playerInventory, host);
        CompartmentHostUi.mountMeOutput(this);
    }
}
