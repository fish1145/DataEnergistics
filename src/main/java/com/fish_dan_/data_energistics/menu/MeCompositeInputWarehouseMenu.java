package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.CompartmentHostUi;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;

public class MeCompositeInputWarehouseMenu extends CompartmentMenu {

    public MeCompositeInputWarehouseMenu(int id, Inventory playerInventory, MeCompositeInputWarehouseBlockEntity host) {
        super(DEMenus.ME_COMPOSITE_INPUT_WAREHOUSE.get(), id, playerInventory, host);
        CompartmentHostUi.mountMeInput(this);
    }
}
