package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;

public class CompositeWarehouseMenu extends CompartmentMenu {

    public CompositeWarehouseMenu(int id, Inventory playerInventory, CompositeWarehouseBlockEntity host) {
        super(ModMenus.COMPOSITE_WAREHOUSE.get(), id, playerInventory, host);
    }
}
