package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataAsynchronousProcessingFactoryBlockEntity;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;

public final class DataAsynchronousProcessingFactoryMenu extends DataRipperReassemblerMenu {

    public DataAsynchronousProcessingFactoryMenu(int id, Inventory playerInventory,
                                                 DataAsynchronousProcessingFactoryBlockEntity host) {
        super(DEMenus.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), id, playerInventory, host);
    }
}
